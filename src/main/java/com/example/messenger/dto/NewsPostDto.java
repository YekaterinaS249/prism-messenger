package com.example.messenger.dto;

public class NewsPostDto {
    private Long id;
    private String title;
    private String content;
    private String imageUrl;
    private String authorUsername;
    private String authorDisplayName;
    private String authorAvatarUrl;
    private String createdAt;

    public NewsPostDto(Long id, String title, String content, String imageUrl, String authorUsername,
                        String authorDisplayName, String authorAvatarUrl, String createdAt) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.imageUrl = imageUrl;
        this.authorUsername = authorUsername;
        this.authorDisplayName = authorDisplayName;
        this.authorAvatarUrl = authorAvatarUrl;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getImageUrl() { return imageUrl; }
    public String getAuthorUsername() { return authorUsername; }
    public String getAuthorDisplayName() { return authorDisplayName; }
    public String getAuthorAvatarUrl() { return authorAvatarUrl; }
    public String getCreatedAt() { return createdAt; }
}
