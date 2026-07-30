package com.example.messenger.controller;

import com.example.messenger.dto.CreateNewsPostRequest;
import com.example.messenger.dto.NewsPostDto;
import com.example.messenger.service.NewsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/news")
public class NewsController {

    private final NewsService newsService;

    public NewsController(NewsService newsService) {
        this.newsService = newsService;
    }

    /** News feed, visible to every user. */
    @GetMapping
    public List<NewsPostDto> list() {
        return newsService.listAll();
    }

    @PostMapping
    public ResponseEntity<?> create(Authentication authentication, @Valid @RequestBody CreateNewsPostRequest request) {
        try {
            return ResponseEntity.ok(newsService.create(authentication.getName(), request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(Authentication authentication, @PathVariable Long id) {
        try {
            newsService.delete(id, authentication.getName());
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
