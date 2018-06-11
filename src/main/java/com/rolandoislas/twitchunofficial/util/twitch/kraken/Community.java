package com.rolandoislas.twitchunofficial.util.twitch.kraken;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

@JsonAdapter(Community.CommunityAdapter.class)
public class Community {
    private String id = "";
    private String avatarImageUrl = "";
    private String coverImageUrl = "";
    private String description = "";
    private String descriptionHtml = "";
    private String language = "";
    private String ownerId = "";
    private String rules = "";
    private String rulesHtml = "";
    private String summary = "";
    private long channels;
    private String name = "";
    private String displayName = "";
    private long viewers;

    // Non-spec
    private long modified;

    // Get set

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public long getModified() {
        return modified;
    }

    public String getAvatarImageUrl() {
        return avatarImageUrl;
    }

    public void setAvatarImageUrl(String avatarImageUrl) {
        this.avatarImageUrl = avatarImageUrl;
    }

    private String getDescriptionHtml() {
        return descriptionHtml;
    }

    private String getLanguage() {
        return language;
    }

    private String getOwnerId() {
        return ownerId;
    }

    private String getRules() {
        return rules;
    }

    private String getRulesHtml() {
        return rulesHtml;
    }

    private String getSummary() {
        return summary;
    }

    private long getChannels() {
        return channels;
    }

    private long getViewers() {
        return viewers;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setDescriptionHtml(String descriptionHtml) {
        this.descriptionHtml = descriptionHtml;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public void setRules(String rules) {
        this.rules = rules;
    }

    public void setRulesHtml(String rulesHtml) {
        this.rulesHtml = rulesHtml;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public void setChannels(long channels) {
        this.channels = channels;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setViewers(long viewers) {
        this.viewers = viewers;
    }

    public void setModified(long modified) {
        this.modified = modified;
    }

    private void setCoverImageUrl(String coverImageUrl) {
        this.coverImageUrl = coverImageUrl;
    }

    private void setId(String id) {
        this.id = id;
    }

    class CommunityAdapter extends TypeAdapter<Community> {

        @Override
        public void write(JsonWriter out, Community community) throws IOException {
            out.beginObject();
            out.name("id");
            out.value(community.getId());
            out.name("avatarImageUrl");
            out.value(community.getAvatarImageUrl());
            out.name("avatar_image_url");
            out.value(community.getAvatarImageUrl());
            out.name("coverImageUrl");
            out.value(community.getCoverImageUrl());
            out.name("cover_image_url");
            out.value(community.getCoverImageUrl());
            out.name("description");
            out.value(community.getDescription());
            out.name("descriptionHtml");
            out.value(community.getDescriptionHtml());
            out.name("description_html");
            out.value(community.getDescriptionHtml());
            out.name("language");
            out.value(community.getLanguage());
            out.name("ownerId");
            out.value(community.getOwnerId());
            out.name("owner_id");
            out.value(community.getOwnerId());
            out.name("rules");
            out.value(community.getRules());
            out.name("rulesHtml");
            out.value(community.getRulesHtml());
            out.name("rules_html");
            out.value(community.getRulesHtml());
            out.name("summary");
            out.value(community.getSummary());
            out.name("channels");
            out.value(community.getChannels());
            out.name("name");
            out.value(community.getName());
            out.name("viewers");
            out.value(community.getViewers());
            out.name("display_name");
            out.value(community.getDisplayName());
            out.endObject();
        }

        @Override
        public Community read(JsonReader in) throws IOException {
            Community community = new Community();
            in.beginObject();
            while (in.hasNext()) {
                String name = in.nextName();
                switch (name) {
                    case "_id":
                        community.setId(in.nextString());
                        break;
                    case "avatar_image_url":
                        community.setAvatarImageUrl(in.nextString());
                        break;
                    case "cover_image_url":
                        community.setCoverImageUrl(in.nextString());
                        break;
                    case "description":
                        community.setDescription(in.nextString());
                        break;
                    case "description_html":
                        community.setDescriptionHtml(in.nextString());
                        break;
                    case "language":
                        community.setLanguage(in.nextString());
                        break;
                    case "owner_id":
                        community.setOwnerId(in.nextString());
                        break;
                    case "rules":
                        community.setRules(in.nextString());
                        break;
                    case "rules_html":
                        community.setRulesHtml(in.nextString());
                        break;
                    case "summary":
                        community.setSummary(in.nextString());
                        break;
                    case "channels":
                        community.setChannels(in.nextLong());
                        break;
                    case "name":
                        community.setName(in.nextString());
                        break;
                    case "viewers":
                        community.setViewers(in.nextLong());
                        break;
                    case "display_name":
                        community.setDisplayName(in.nextString());
                        break;
                    default:
                        in.skipValue();
                        break;
                }
            }
            in.endObject();
            return community;
        }
    }
}
