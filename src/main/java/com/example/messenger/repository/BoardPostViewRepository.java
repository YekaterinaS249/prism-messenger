package com.example.messenger.repository;

import com.example.messenger.model.BoardPostView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BoardPostViewRepository extends JpaRepository<BoardPostView, BoardPostView.Key> {
    List<BoardPostView> findAllByIdPostId(Long postId);
}
