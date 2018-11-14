import com.frozenironsoftware.twitched.bif.util.BifTool;
import com.frozenironsoftware.twitched.bif.util.FileUtil;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DownloadTest {
    private static String VOD_ID = "9763379";

    /**
     * Test downloading a VOD
     * This will use cached data if BIF_CLEAR_CACHE is not set to true
     */
    @Test
    public void testDownloadStream() {
        assertNotNull(downloadStream());
    }

    /**
     * Test downloading a VOD
     * This will use cached data if BIF_CLEAR_CACHE is not set to true
     * @return the paths of the downloaded files
     */
    @Nullable
    List<Path> downloadStream() {
        if (!Boolean.parseBoolean(System.getenv("BIF_CLEAR_CACHE")) &&
                !FileUtil.isDirEmpty(ConstantsTest.DOWNLOAD_DIR)) {
            System.out.println("Using cached data");
            File downloadDir = ConstantsTest.DOWNLOAD_DIR.toFile();
            List<Path> downloadPaths = new ArrayList<>();
            File[] downloadedFiles = downloadDir.listFiles();
            assertNotNull(downloadedFiles);
            for (File download : downloadedFiles)
                downloadPaths.add(download.toPath());
            return downloadPaths;
        }
        System.out.println("Downloading data");
        FileUtil.cleanDirectory(ConstantsTest.DOWNLOAD_DIR.toFile());
        assertTrue(FileUtil.createDirectory(ConstantsTest.DOWNLOAD_DIR));
        BifTool bifTool = new BifTool(null);
        List<Path> downloads = bifTool.downloadStream(VOD_ID, ConstantsTest.DOWNLOAD_DIR);
        assertNotNull(downloads);
        assertTrue("No files downloaded", downloads.size() > 0);
        assertFalse("No downloaded files found on disk",FileUtil.isDirEmpty(ConstantsTest.DOWNLOAD_DIR));
        return downloads;
    }
}
