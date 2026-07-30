package com.example.messenger.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class CreateNewsPostRequest {

    @NotBlank(message = "Заголовок обязателен")
    @Size(min = 1, max = 150, message = "Заголовок должен быть не длиннее 150 символов")
    private String title;

    @NotBlank(message = "Текст поста обязателен")
    @Size(min = 1, max = 4000, message = "Текст поста должен быть не длиннее 4000 символов")
    private String content;

    // Set after uploading via /api/media/upload.
    private String imageUrl;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}
