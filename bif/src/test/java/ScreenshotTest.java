import com.frozenironsoftware.twitched.bif.data.Frames;
import com.frozenironsoftware.twitched.bif.util.BifTool;
import com.frozenironsoftware.twitched.bif.util.FileUtil;
import org.junit.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ScreenshotTest {

    /**
     * Test generating all resolutions (FHD, HD, SD) of BIF screenshots with only FFMPEG
     */
    @Test
    public void testGenerateFfmpeg() {
        createFrameDirs();
        List<Path> downloads = downloadStream();
        BifTool bifTool = new BifTool(null);
        Frames generated = bifTool.generateFramesFfmpeg(downloads, ConstantsTest.FRAME_DIR);
        assertTrue(generated.getFhdFrames().size() > 0);
        assertTrue(generated.getHdFrames().size() > 0);
        assertTrue(generated.getSdFrames().size() > 0);
        assertFalse(FileUtil.isDirEmpty(ConstantsTest.FRAME_DIR.resolve(BifTool.FHD_SIZE.getName())));
        assertFalse(FileUtil.isDirEmpty(ConstantsTest.FRAME_DIR.resolve(BifTool.HD_SIZE.getName())));
        assertFalse(FileUtil.isDirEmpty(ConstantsTest.FRAME_DIR.resolve(BifTool.SD_SIZE.getName())));
    }

    /**
     * Create all the frame dirs, removing them first if necessary
     */
    private void createFrameDirs() {
        FileUtil.cleanDirectory(ConstantsTest.FRAME_DIR.toFile());
        FileUtil.createDirectory(ConstantsTest.FRAME_DIR.resolve(BifTool.FHD_SIZE.getName()));
        FileUtil.createDirectory(ConstantsTest.FRAME_DIR.resolve(BifTool.HD_SIZE.getName()));
        FileUtil.createDirectory(ConstantsTest.FRAME_DIR.resolve(BifTool.SD_SIZE.getName()));
    }

    /**
     * Run the download test to ensure there is a VOD to work with
     */
    private List<Path> downloadStream() {
        DownloadTest downloadTest = new DownloadTest();
        return downloadTest.downloadStream();
    }

    /**
     * Test generating FHD BIF screenshots with FFMPEG and generating (resizing) the others (HD, SD)
     */
    @Test
    public void testGenerateResize() {
        createFrameDirs();
        List<Path> downloads = downloadStream();
        BifTool bifTool = new BifTool(null);
        Frames generated = bifTool.generateFramesResize(downloads, ConstantsTest.FRAME_DIR);
        assertTrue(generated.getFhdFrames().size() > 0);
        assertTrue(generated.getHdFrames().size() > 0);
        assertTrue(generated.getSdFrames().size() > 0);
        assertFalse(FileUtil.isDirEmpty(ConstantsTest.FRAME_DIR.resolve(BifTool.FHD_SIZE.getName())));
        assertFalse(FileUtil.isDirEmpty(ConstantsTest.FRAME_DIR.resolve(BifTool.HD_SIZE.getName())));
        assertFalse(FileUtil.isDirEmpty(ConstantsTest.FRAME_DIR.resolve(BifTool.SD_SIZE.getName())));
    }
}
