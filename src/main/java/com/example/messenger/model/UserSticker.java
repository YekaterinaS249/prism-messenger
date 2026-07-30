package com.example.messenger.model;

import javax.persistence.*;
import java.time.Instant;

/** A custom sticker image uploaded by a user, shown in their own "Мои" picker tab. */
@Entity
@Table(name = "user_sticker")
public class UserSticker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_username", nullable = false, length = 50)
    private String ownerUsername;

    @Column(nullable = false, length = 255)
    private String url;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public UserSticker() {}

    public UserSticker(String ownerUsername, String url) {
        this.ownerUsername = ownerUsername;
        this.url = url;
    }

    public Long getId() { return id; }
    public String getOwnerUsername() { return ownerUsername; }
    public void setOwnerUsername(String ownerUsername) { this.ownerUsername = ownerUsername; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
