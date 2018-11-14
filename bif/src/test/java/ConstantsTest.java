import java.nio.file.Path;
import java.nio.file.Paths;

class ConstantsTest {
    static Path TEMP_DIR = Paths.get("/tmp/twitch_roku_bif_test/");
    static Path DOWNLOAD_DIR = ConstantsTest.TEMP_DIR.resolve("download");
    static Path FRAME_DIR = ConstantsTest.TEMP_DIR.resolve("frame");
}
