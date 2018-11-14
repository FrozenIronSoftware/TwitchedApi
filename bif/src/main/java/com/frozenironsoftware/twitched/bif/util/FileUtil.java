package com.frozenironsoftware.twitched.bif.util;

import com.rolandoislas.twitchunofficial.util.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Path;

public class FileUtil {
    /**
     * Check if directory does not contain any files
     * @param dir directory to check
     * @return true if there are no files of size 1 byte or more
     */
    public static boolean isDirEmpty(@NotNull Path dir) {
        File file = dir.toFile();
        if (file.exists()) {
            File[] internalFiles = file.listFiles();
            if (internalFiles == null)
                return true;
            for (File internalFile : internalFiles) {
                if (internalFile.isDirectory() && !isDirEmpty(dir))
                    return false;
                else if (internalFile.isFile() && internalFile.length() > 0)
                    return false;
            }
        }
        return true;
    }

    /**
     * Create a directory if it does not exist
     * @param dir directory to create
     * @return true if it was created or it already exists
     */
    public static boolean createDirectory(@NotNull Path dir) {
        File file = dir.toFile();
        if (!file.exists())
            return file.mkdirs();
        return true;
    }

    /**
     * Delete all files in the directory
     * @param dir directory to delete
     */
    public static void cleanDirectory(@NotNull File dir) {
        File[] contents = dir.listFiles();
        if (contents == null)
            return;
        for (File file : contents) {
            if (file.isDirectory())
                cleanDirectory(file);
            else if (!file.delete())
                Logger.debug("Failed to delete: ", file.getAbsolutePath());
        }
        if (!dir.delete())
            Logger.debug("Failed to delete: ", dir.getAbsolutePath());
    }
}
