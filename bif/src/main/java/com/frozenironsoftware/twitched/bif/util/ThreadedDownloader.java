package com.frozenironsoftware.twitched.bif.util;

import com.frozenironsoftware.twitched.bif.data.Constants;
import com.frozenironsoftware.twitched.bif.data.Download;
import com.goebl.david.Response;
import com.goebl.david.Webb;
import com.rolandoislas.twitchunofficial.util.Logger;

import java.io.InputStream;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ThreadedDownloader {
    private List<Download> downloads;
    private Downloader[] downloadThreads;
    private boolean success;

    public ThreadedDownloader(int threadCount) {
        downloads = new ArrayList<>();
        downloadThreads = new Downloader[threadCount];
        for (int threadIndex = 0; threadIndex < threadCount; threadIndex++)
            downloadThreads[threadIndex] = null;
    }

    /**
     * Clear any active downloads
     */
    public void reset() {
        success = false;
        downloads.clear();
        for (Downloader downloader : downloadThreads) {
            if (downloader == null)
                continue;
            downloader.interrupt();
            try {
                downloader.join();
            }
            catch (InterruptedException e) {
                Logger.exception(e);
            }
        }
    }

    /**
     * Add a URL to download
     * @param url url to download
     * @param outPath path to write the download to
     */
    public void addDownload(String url, Path outPath) {
        downloads.add(new Download(url, outPath));
    }

    public void waitForCompletion() {
        while (downloads.size() > 0) {
            for (int threadIndex = 0; threadIndex < downloadThreads.length; threadIndex++) {
                Downloader downloader = downloadThreads[threadIndex];
                if (downloader == null || !downloader.isAlive()) {
                    if (downloader != null && !downloader.isSuccess()) {
                        reset();
                        break;
                    }
                    if (downloads.size() == 0)
                        break;
                    Download download = downloads.get(0);
                    downloads.remove(0);
                    downloadThreads[threadIndex] = new Downloader(download);
                    downloadThreads[threadIndex].start();
                }
            }
        }
        boolean wait;
        do {
            wait = false;
            for (Downloader downloader : downloadThreads)
                if (downloader != null && downloader.isAlive())
                    wait = true;
        }
        while (wait);
        success = true;
    }

    public boolean didComplete() {
        return success;
    }

    private class Downloader extends Thread {
        private final Download download;
        private boolean success = false;

        Downloader(Download download) {
            this.download = download;
            setName("DownloadThread-" + getId());
        }

        @Override
        public void run() {
            Webb webb = Webb.create();
            Response<InputStream> response = null;
            int maxRetries = 10;
            int retries = maxRetries;
            do {
                try {
                    response = webb.get(download.getUrl())
                            .header("User-Agent", String.format("TwitchedBif/%s (Java)", Constants.VERSION))
                            .ensureSuccess()
                            .retry(10, true)
                            .asStream();
                    retries = 0;
                    if (response == null || response.getBody() == null)
                        return;
                    Files.copy(response.getBody(), download.getOutPath());
                    success = true;
                }
                catch (Exception e) {
                    Logger.exception(e);
                    if (e.getCause() instanceof SocketException || e instanceof SocketException) {
                        try {
                            Thread.sleep((maxRetries - retries + 1) * 2500);
                        }
                        catch (InterruptedException ie) {
                            Logger.exception(ie);
                        }
                        retries--;
                    }
                    else
                        retries = 0;
                }
            }
            while (retries > 0);
        }

        boolean isSuccess() {
            return success;
        }
    }
}
