package com.example.messenger.repository;

import com.example.messenger.model.NewsPost;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NewsPostRepository extends JpaRepository<NewsPost, Long> {
    List<NewsPost> findAllByOrderByCreatedAtDesc();
}
