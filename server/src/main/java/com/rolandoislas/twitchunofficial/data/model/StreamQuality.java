package com.rolandoislas.twitchunofficial.data.model;

import com.google.gson.annotations.SerializedName;
import com.rolandoislas.twitchunofficial.util.Logger;

public class StreamQuality {
    private long id;
    private String model = "";
    private int bitrate;
    private String comment = "";
    @SerializedName("240p30")
    private boolean _240p30;
    @SerializedName("240p60")
    private boolean _240p60;
    @SerializedName("480p30")
    private boolean _480p30;
    @SerializedName("480p60")
    private boolean _480p60;
    @SerializedName("720p30")
    private boolean _720p30;
    @SerializedName("720p60")
    private boolean _720p60;
    @SerializedName("1080p30")
    private boolean _1080p30;
    @SerializedName("1080p60")
    private boolean _1080p60;
    @SerializedName("only_source_60")
    private boolean onlySource60;

    public StreamQuality(String model, int bitrate, String comment, boolean _240p30, boolean _240p60, boolean _480p30,
                         boolean _480p60, boolean _720p30, boolean _720p60, boolean _1080p30, boolean _1080p60,
                         boolean onlySource60) {
        this.model = model;
        this.bitrate = bitrate;
        this.comment = comment;
        this._240p30 = _240p30;
        this._240p60 = _240p60;
        this._480p30 = _480p30;
        this._480p60 = _480p60;
        this._720p30 = _720p30;
        this._720p60 = _720p60;
        this._1080p30 = _1080p30;
        this._1080p60 = _1080p60;
        this.onlySource60 = onlySource60;
    }

    public boolean validate() {
        return model != null && !model.isEmpty() && bitrate > 0 && bitrate <= 20000000 && comment != null;
    }

    public String getModel() {
        return model;
    }

    public int getBitrate() {
        return bitrate;
    }

    public boolean get240p30() {
        return _240p30;
    }

    public boolean get240p60() {
        return _240p60;
    }

    public boolean get480p30() {
        return _480p30;
    }

    public boolean get480p60() {
        return _480p60;
    }

    public boolean get720p30() {
        return _720p30;
    }

    public boolean get720p60() {
        return _720p60;
    }

    public boolean get1080p30() {
        return _1080p30;
    }

    public boolean get1080p60() {
        return _1080p60;
    }

    public boolean getOnlySource60() {
        return onlySource60;
    }

    public String getComment() {
        return comment;
    }

    /**
     * Disable all 60 FPS qualities
     */
    public void disable60() {
        this._240p60 = false;
        this._480p60 = false;
        this._720p60 = false;
        this._1080p60 = false;
    }

    /**
     * Disable all qualities higher than the specified quality
     * @param limit max quality
     */
    public void limitQuality(int limit) {
        if (limit < 1080) {
            this._1080p30 = false;
            this._1080p60 = false;
        }
        if (limit < 720) {
            this._720p30 = false;
            this._720p60 = false;
        }
        if (limit < 480) {
            this._480p30 = false;
            this._480p60 = false;
        }
        if (limit < 240) {
            this._240p30 = false;
            this._240p60 = false;
            Logger.warn("Quality limited to less than 240p");
        }
    }

    /**
     * Check if a playlist stream meets the defined quality
     * @param stream Stream playlist to check
     * @return true if the stream meets the restrictions placed by this StreamQuality instance
     */
    public boolean meetsQuality(Playlist stream) {
        // Check if this stream is 60 FPS. If it is and the StreamQuality denies non-source 60 FPS, check if it is
        // a source stream.
        if (stream.getFps() == 60 && getOnlySource60() && !stream.isSource())
            return false;
        // Check if the bitrate is higher than the StreamQuality defined max bitrate and decline.
        if (stream.getBitrate() > getBitrate())
            return false;
        // Check 30 FPS streams
        if (stream.getFps() == 30) {
            if (stream.getQuality() <= 240 && !get240p30())
                return false;
            if (stream.getQuality() > 240 && stream.getQuality() <= 480 && !get480p30())
                return false;
            if (stream.getQuality() == 720 && !get720p30())
                return false;
            if (stream.getQuality() > 720 && !get1080p30())
                return false;
        }
        // Check 60 FPS streams
        if (stream.getFps() == 60) {
            if (stream.getQuality() <= 240 && !get240p60())
                return false;
            if (stream.getQuality() > 240 && stream.getQuality() <= 480 && !get480p60())
                return false;
            if (stream.getQuality() == 720 && !get720p60())
                return false;
            if (stream.getQuality() > 720 && !get1080p60())
                return false;
        }
        return true;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }
}
