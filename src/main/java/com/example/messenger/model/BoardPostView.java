package com.example.messenger.model;

import javax.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** Tracks which users have opened a given board post ("seen by"), visible to admins only. */
@Entity
@Table(name = "board_post_view")
public class BoardPostView {

    @EmbeddedId
    private Key id;

    @Column(nullable = false)
    private Instant viewedAt = Instant.now();

    public BoardPostView() {}

    public BoardPostView(Long postId, String username) {
        this.id = new Key(postId, username);
    }

    public Key getId() { return id; }
    public void setId(Key id) { this.id = id; }

    public Instant getViewedAt() { return viewedAt; }
    public void setViewedAt(Instant viewedAt) { this.viewedAt = viewedAt; }

    @Embeddable
    public static class Key implements Serializable {
        @Column(name = "post_id")
        private Long postId;

        @Column(name = "username", length = 50)
        private String username;

        public Key() {}

        public Key(Long postId, String username) {
            this.postId = postId;
            this.username = username;
        }

        public Long getPostId() { return postId; }
        public void setPostId(Long postId) { this.postId = postId; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key)) return false;
            Key key = (Key) o;
            return Objects.equals(postId, key.postId) && Objects.equals(username, key.username);
        }

        @Override
        public int hashCode() { return Objects.hash(postId, username); }
    }
}
