package com.frozenironsoftware.twitched.bif.util;

import com.frozenironsoftware.twitched.bif.BifGenerator;
import com.frozenironsoftware.twitched.bif.data.Constants;
import com.frozenironsoftware.twitched.bif.data.Frames;
import com.frozenironsoftware.twitched.bif.data.Size;
import com.goebl.david.Response;
import com.goebl.david.Webb;
import com.goebl.david.WebbException;
import com.google.common.io.LittleEndianDataOutputStream;
import com.rolandoislas.twitchunofficial.TwitchUnofficialApi;
import com.rolandoislas.twitchunofficial.data.model.Playlist;
import com.rolandoislas.twitchunofficial.util.GoogleStorage;
import com.rolandoislas.twitchunofficial.util.Logger;
import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.name.Rename;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BifTool {
    private static final Path TEMP_PATH = Paths.get("/tmp/twitch_roku_bif/");
    public static final Size FHD_SIZE = new Size(640, 360, "fhd");
    public static final Size HD_SIZE = new Size(430, 242, "hd");
    public static final Size SD_SIZE = new Size(240, 135, "sd");
    private static final int FRAME_TIME = 60;
    private static final int BIF_FRAME_INTERVAL = 10;
    private static final int MAX_DOWNLOAD_THREADS = 10;
    @Nullable private final GoogleStorage storage;
    private final ThreadedDownloader downloader;

    public BifTool(@Nullable GoogleStorage storage) {
        this.storage = storage;
        this.downloader = new ThreadedDownloader(MAX_DOWNLOAD_THREADS);
    }

    /**
     * Generate and store bif to cloud storage
     * @param id twitch video id
     */
    void generateAndStoreBif(String id) {
        cleanTempDirectory();
        createTempDirectory();
        Logger.debug("Downloading stream with ID: %s", id);
        List<Path> streamParts = downloadStream(id, TEMP_PATH.resolve("download"));
        if (streamParts.size() == 0) {
            Logger.debug("Stream not downloaded: %s", id);
            cleanTempDirectory();
            return;
        }
        Logger.debug("Generating frames for stream with ID: %s", id);
        Frames frames = generateFrames(streamParts, TEMP_PATH.resolve("frame"));
        if (frames == null) {
            Logger.debug("Frames not generated for stream with ID: %s", id);
            cleanTempDirectory();
            return;
        }
        Logger.debug("Generating BIFs for stream with ID: %s", id);
        Path fhdBif = generateBif(frames.getFhdFrames(), FHD_SIZE.getName());
        Path hdBif = generateBif(frames.getHdFrames(), HD_SIZE.getName());
        Path sdBif = generateBif(frames.getSdFrames(), SD_SIZE.getName());
        Logger.debug("Uploading BIFs for stream with ID: %s", id);
        if (storage != null)
            storage.storeBif(sdBif, hdBif, fhdBif, id);
        cleanTempDirectory();
        Logger.debug("BIF processed ID: %s", id);
    }

    /**
     * Generate a bif for the frames
     * @param frames bif frames
     * @param name bif file name
     * @return bif path or null on error
     */
    @Nullable
    private Path generateBif(List<Path> frames, String name) {
        Path outputPath = TEMP_PATH.resolve("bif").resolve(name + ".bif");
        FileOutputStream outputStream;
        try {
            outputStream = new FileOutputStream(outputPath.toFile());
        }
        catch (FileNotFoundException e) {
            Logger.exception(e);
            return null;
        }
        LittleEndianDataOutputStream bifStream = new LittleEndianDataOutputStream(outputStream);
        try {
            // Magic Number (0)
            bifStream.write(new byte[]{(byte)0x89, 0x42, 0x49, 0x46, 0x0d, 0x0a, 0x1a, 0x0a});
            // Version (8)
            bifStream.writeInt(0);
            // Number of bif images (12)
            int bifFrameMultiplier = FRAME_TIME / BIF_FRAME_INTERVAL;
            int totalFrameCount = frames.size() * bifFrameMultiplier;
            bifStream.writeInt(totalFrameCount);
            // Timestamp multiplier (milliseconds) (16)
            bifStream.writeInt(1000);
            // Reserved (20)
            bifStream.write(new byte[44]);
            // Index (64)
            int offsetBytes = 64 + ((totalFrameCount + 1) * 8);
            for (int frameIndex = 0; frameIndex < frames.size(); frameIndex++) {
                Path framePath = frames.get(frameIndex);
                File frame = framePath.toFile();
                long frameSize = frame.length();
                for (int frameCountIndex = 0; frameCountIndex < bifFrameMultiplier; frameCountIndex++) {
                    // Timestamp
                    bifStream.writeInt(frameIndex * FRAME_TIME + frameCountIndex * BIF_FRAME_INTERVAL);
                    // Offset
                    bifStream.writeInt((int) (offsetBytes + frameCountIndex * frameSize));
                }
                offsetBytes += frameSize * bifFrameMultiplier;
            }
            // Trailing index (64 + frame_count * 8)
            bifStream.writeInt(0xffffffff);
            bifStream.writeInt(offsetBytes);
            // Data (64 + (frame_count + 1) * 8)
            for (Path framePath : frames) {
                for (int frameCountIndex = 0; frameCountIndex < bifFrameMultiplier; frameCountIndex++)
                    Files.copy(framePath, outputStream);
            }
        }
        catch (IOException e) {
            Logger.exception(e);
            return null;
        }
        return outputPath;
    }

    /**
     * Generate FHD, HD, and SD frames
     * @param streamParts paths to stream parts
     * @return frames object containing all paths to the different types of frames or null on error
     */
    @Nullable
    private Frames generateFrames(List<Path> streamParts, Path frameDir) {
        return generateFramesResize(streamParts, frameDir);
    }

    /**
     * Generate frames for the specifed stream parts and of the specified size
     * @param streamParts paths to the stream ts files
     * @param size size of screenshot
     * @return paths of output frames. The list will be empty if no frames were generated or an error occurred
     */
    private List<Path> generateFrames(@NotNull List<Path> streamParts, Size size, Path frameDir) {
        if (streamParts.size() == 0)
            return new ArrayList<>();
        List<Path> frames = new ArrayList<>();
        try {
            FFmpeg ffmpeg = new FFmpeg("ffmpeg");
            for (Path streamPart : streamParts) {
                Path outputNamePath = streamPart.getFileName();
                if (outputNamePath == null)
                    return new ArrayList<>();
                String outputName = outputNamePath.toString().replace(".ts", ".jpg");
                Path outputPath = frameDir.resolve(size.getName()).resolve(outputName)
                        .toAbsolutePath();
                FFmpegBuilder builder = ffmpeg.builder()
                        .addInput(streamPart.toAbsolutePath().toString())
                        .addOutput(outputPath.toString())
                        .setFrames(1)
                        .setVideoResolution(size.getWidth(), size.getHeight())
                        .done();
                ffmpeg.run(builder);
                frames.add(outputPath);
            }
        }
        catch (IOException e) {
            Logger.exception(e);
            return new ArrayList<>();
        }
        return frames;
    }

    /**
     * Enure the temp directory is created
     */
    private void createTempDirectory() {
        File tempDir = TEMP_PATH.toFile();
        if (!tempDir.exists())
            tempDir.mkdir();
        File downloadDir = TEMP_PATH.resolve("download").toFile();
        if (!downloadDir.exists())
            downloadDir.mkdir();
        File frameDir = TEMP_PATH.resolve("frame").toFile();
        if (!frameDir.exists())
            frameDir.mkdir();
        File sizeFhd = TEMP_PATH.resolve("frame").resolve(FHD_SIZE.getName()).toFile();
        if (!sizeFhd.exists())
            sizeFhd.mkdir();
        File sizeHd = TEMP_PATH.resolve("frame").resolve(HD_SIZE.getName()).toFile();
        if (!sizeHd.exists())
            sizeHd.mkdir();
        File sizeSd = TEMP_PATH.resolve("frame").resolve(SD_SIZE.getName()).toFile();
        if (!sizeSd.exists())
            sizeSd.mkdir();
        File bifDir = TEMP_PATH.resolve("bif").toFile();
        if (!bifDir.exists())
            bifDir.mkdir();
    }

    /**
     * Download a stream to a temporary directory
     * @param id twitch stream id
     * @param downloadDir Directory where all stream parts should be stored. This does not ensure the directory has been
     *                    created.
     * @return paths to download stream parts
     */
    public List<Path> downloadStream(String id, Path downloadDir) {
        Webb webb = Webb.create();
        String playlistString = null;
        try {
            Response<String> response = webb.get(
                    String.format("https://www.twitched.org/api/twitch/vod/60/1080/%s.m3u8", id))
                    .header("Client-ID", BifGenerator.twitchedClientId)
                    .header("User-Agent", String.format("TwitchedBif/%s (Java)", Constants.VERSION))
                    .ensureSuccess()
                    .retry(3, true)
                    .asString();
            playlistString = response.getBody();
        }
        catch (WebbException e) {
            e.printStackTrace();
            Logger.exception(e);
        }
        if (playlistString == null || playlistString.isEmpty()) {
            Logger.debug("Downloaded empty primary playlist string");
            return new ArrayList<>();
        }
        Map<String, Object> parsedPlaylist = TwitchUnofficialApi.playlistStringToList(playlistString);
        Object playlistsObject = parsedPlaylist.get("playlists");
        if (!(playlistsObject instanceof List<?>))
            return new ArrayList<>();
        if (((List<?>)playlistsObject).size() < 1)
            return new ArrayList<>();
        if (!(((List<?>)playlistsObject).get(0) instanceof Playlist))
            return new ArrayList<>();
        List<Playlist> playlists = (List<Playlist>) playlistsObject;
        Playlist playlist = null;
        for (Playlist loopPlaylist : playlists) {
            if (!loopPlaylist.isVideo())
                continue;
            playlist = loopPlaylist;
            if (loopPlaylist.isQualityOrLower(FHD_SIZE.getHeight()))
                break;
        }
        if (playlist == null || playlist.getLines().size() < 3)
            return new ArrayList<>();
        String playlistUrl = playlist.getLines().get(2);
        return downloadAllStreamParts(playlistUrl, downloadDir);
    }

    /**
     * Download all the parts of the stream
     * @param playlistUrl url of playlist containing the .ts parts
     * @param downloadDir Download directory where all stream parts will be downloaded. This will fail if the directory
     *                    does not exist.
     * @return path of all downloaded parts
     */
    private List<Path> downloadAllStreamParts(@NotNull String playlistUrl, Path downloadDir) {
        StringBuilder streamPartDir = new StringBuilder();
        String[] streamUrlSplit = playlistUrl.split("/");
        for (int splitIndex = 0; splitIndex < streamUrlSplit.length; splitIndex++) {
            if (splitIndex < streamUrlSplit.length - 1)
                streamPartDir.append(streamUrlSplit[splitIndex]).append("/");
        }
        Webb webb = Webb.create();
        String playlistString = null;
        try {
            playlistString = webb.get(playlistUrl)
                    .header("User-Agent", String.format("TwitchedBif/%s (Java)", Constants.VERSION))
                    .ensureSuccess()
                    .asString()
                    .getBody();
        }
        catch (WebbException e) {
            Logger.exception(e);
        }
        if (playlistString == null || playlistString.isEmpty()) {
            Logger.debug("Downloaded empty secondary playlist string");
            return new ArrayList<>();
        }
        int streamPartIndex = 0;
        List<Path> paths = new ArrayList<>();
        int skipInterval = 0;
        downloader.reset();
        for (String line : playlistString.split("\r?\n")) {
            if (!line.trim().startsWith("#") && !line.trim().isEmpty()) {
                if (skipInterval == 0)
                    skipInterval = 1;
                if (streamPartIndex % skipInterval != 0) {
                    streamPartIndex++;
                    continue;
                }
                Path outPath = downloadDir.resolve(streamPartIndex + ".ts").toAbsolutePath();
                downloader.addDownload(streamPartDir.toString() + line, outPath);
                paths.add(outPath);
                streamPartIndex++;
            }
            else if (skipInterval == 1 && line.trim().startsWith("#EXTINF")){
                Pattern timePattern = Pattern.compile("#EXTINF:(\\d+(?:\\.\\d+)),?.*");
                Matcher matcher = timePattern.matcher(line.trim());
                if (matcher.matches()) {
                    double partSeconds = Double.parseDouble(matcher.group(1));
                    if (partSeconds >= FRAME_TIME)
                        skipInterval = 1;
                    else
                        skipInterval = (int) Math.ceil(FRAME_TIME / partSeconds);
                }
            }
        }
        downloader.waitForCompletion();
        if (downloader.didComplete())
            return paths;
        return new ArrayList<>();
    }



    /**
     * Delete all files in the temp directory
     */
    private void cleanTempDirectory() {
        FileUtil.cleanDirectory(TEMP_PATH.toFile());
    }

    /**
     * Generate frames with FFMPEG only
     * @param streamParts stream parts paths
     * @param frameDir frame output directory
     * @return frames generated
     */
    @Deprecated
    public Frames generateFramesFfmpeg(List<Path> streamParts, Path frameDir) {
        List<Path> fhdFrames = generateFrames(streamParts, FHD_SIZE, frameDir);
        List<Path> hdFrames = generateFrames(streamParts, HD_SIZE, frameDir);
        List<Path> sdFrames = generateFrames(streamParts, SD_SIZE, frameDir);
        if (fhdFrames.size() == 0 || hdFrames.size() == 0 || sdFrames.size() == 0)
            return null;
        return new Frames(fhdFrames, hdFrames, sdFrames);
    }

    /**
     * Generate frames with FFMPEG for FHD then use ImageMagick to resize for HD and SD
     * @param streamParts stream parts paths
     * @param frameDir frame output directory
     * @return frames generated
     */
    public Frames generateFramesResize(List<Path> streamParts, Path frameDir) {
        List<Path> fhdFrames = generateFrames(streamParts, FHD_SIZE, frameDir);
        List<Path> hdFrames = resizeFrames(fhdFrames, HD_SIZE, frameDir);
        List<Path> sdFrames = resizeFrames(fhdFrames, SD_SIZE, frameDir);
        if (fhdFrames.size() == 0 || hdFrames.size() == 0 || sdFrames.size() == 0)
            return null;
        return new Frames(fhdFrames, hdFrames, sdFrames);
    }

    /**
     * Resize frames with image magick
     * @param frames frame image paths
     * @param size size to resize to
     * @param frameDir output directory
     * @return array of frame paths. It will be empty if an error occurred
     */
    private List<Path> resizeFrames(@NotNull List<Path> frames, @NotNull Size size, @NotNull Path frameDir) {
        if (frames.size() == 0)
            return new ArrayList<>();
        List<Path> generatedFrames = new ArrayList<>();
        File[] input = new File[frames.size()];
        Path outputDir = frameDir.resolve(size.getName());
        for (int frameIndex = 0; frameIndex < frames.size(); frameIndex++) {
            Path framePath = frames.get(frameIndex);
            input[frameIndex] = framePath.toFile();
            generatedFrames.add(outputDir.resolve(framePath.getFileName()));
        }
        try {
            Thumbnails.of(input)
                    .size(size.getWidth(), size.getHeight())
                    .outputFormat("jpg")
                    .outputQuality(0.7)
                    .toFiles(outputDir.toFile(), Rename.NO_CHANGE);
        }
        catch (IOException e) {
            Logger.exception(e);
            return new ArrayList<>();
        }
        return generatedFrames;
    }
}
