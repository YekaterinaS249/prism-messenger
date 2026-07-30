package com.example.messenger.service;

import com.example.messenger.dto.CreateNewsPostRequest;
import com.example.messenger.dto.NewsPostDto;
import com.example.messenger.model.NewsPost;
import com.example.messenger.repository.NewsPostRepository;
import com.example.messenger.repository.UserRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class NewsService {

    private final NewsPostRepository newsPostRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public NewsService(NewsPostRepository newsPostRepository, UserRepository userRepository,
                        SimpMessagingTemplate messagingTemplate) {
        this.newsPostRepository = newsPostRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }

    public NewsPostDto create(String author, CreateNewsPostRequest request) {
        NewsPost post = new NewsPost();
        post.setTitle(request.getTitle().trim());
        post.setContent(request.getContent().trim());
        post.setImageUrl(request.getImageUrl());
        post.setAuthorUsername(author);
        post = newsPostRepository.save(post);
        NewsPostDto dto = toDto(post);
        // Live "new post" signal so every connected client can badge the Новости tab
        // without polling; the client re-fetches the full list if that tab is open.
        messagingTemplate.convertAndSend("/topic/news", dto);
        return dto;
    }

    public List<NewsPostDto> listAll() {
        return newsPostRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public void delete(Long id, String requester) {
        NewsPost post = newsPostRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Post not found"));
        if (!post.getAuthorUsername().equals(requester)) {
            throw new SecurityException("Only the author can delete this post");
        }
        newsPostRepository.delete(post);
    }

    private NewsPostDto toDto(NewsPost p) {
        var author = userRepository.findByUsername(p.getAuthorUsername());
        String authorDisplayName = author.map(u -> u.getDisplayName()).orElse(p.getAuthorUsername());
        String authorAvatarUrl = author.map(u -> u.getAvatarUrl()).orElse(null);
        String createdAtStr = DateTimeFormatter.ISO_INSTANT.format(p.getCreatedAt().atZone(ZoneOffset.UTC));
        return new NewsPostDto(p.getId(), p.getTitle(), p.getContent(), p.getImageUrl(),
                p.getAuthorUsername(), authorDisplayName, authorAvatarUrl, createdAtStr);
    }
}
