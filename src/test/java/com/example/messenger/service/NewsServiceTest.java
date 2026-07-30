package com.example.messenger.service;

import com.example.messenger.dto.CreateNewsPostRequest;
import com.example.messenger.dto.NewsPostDto;
import com.example.messenger.model.NewsPost;
import com.example.messenger.repository.NewsPostRepository;
import com.example.messenger.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NewsServiceTest {

    @Mock
    private NewsPostRepository newsPostRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private NewsService newsService;

    @BeforeEach
    void setUp() {
        newsService = new NewsService(newsPostRepository, userRepository, messagingTemplate);
    }

    @Test
    void create_trimsFieldsAndSetsAuthor() {
        when(newsPostRepository.save(any(NewsPost.class))).thenAnswer(inv -> {
            NewsPost p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());

        CreateNewsPostRequest req = new CreateNewsPostRequest();
        req.setTitle("  Заголовок  ");
        req.setContent("  Текст новости  ");

        NewsPostDto dto = newsService.create("alice", req);

        assertThat(dto.getTitle()).isEqualTo("Заголовок");
        assertThat(dto.getContent()).isEqualTo("Текст новости");
        assertThat(dto.getAuthorUsername()).isEqualTo("alice");
    }

    @Test
    void delete_byNonAuthor_throwsSecurityException() {
        NewsPost post = new NewsPost();
        post.setId(3L);
        post.setAuthorUsername("alice");
        when(newsPostRepository.findById(3L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> newsService.delete(3L, "mallory"))
                .isInstanceOf(SecurityException.class);

        verify(newsPostRepository, never()).delete(any());
    }

    @Test
    void delete_byAuthor_succeeds() {
        NewsPost post = new NewsPost();
        post.setId(3L);
        post.setAuthorUsername("alice");
        when(newsPostRepository.findById(3L)).thenReturn(Optional.of(post));

        newsService.delete(3L, "alice");

        verify(newsPostRepository).delete(post);
    }

    @Test
    void delete_missingPost_throwsIllegalArgument() {
        when(newsPostRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newsService.delete(99L, "alice"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
