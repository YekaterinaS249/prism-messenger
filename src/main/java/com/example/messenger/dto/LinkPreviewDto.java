package com.example.messenger.dto;

public class LinkPreviewDto {
    private String url;
    private String title;
    private String description;
    private String image;
    private String siteName;

    public LinkPreviewDto() {}

    public LinkPreviewDto(String url, String title, String description, String image, String siteName) {
        this.url = url;
        this.title = title;
        this.description = description;
        this.image = image;
        this.siteName = siteName;
    }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
    public String getSiteName() { return siteName; }
    public void setSiteName(String siteName) { this.siteName = siteName; }
}
