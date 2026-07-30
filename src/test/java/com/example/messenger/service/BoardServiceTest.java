package com.example.messenger.service;

import com.example.messenger.dto.BoardPostDto;
import com.example.messenger.dto.CreateBoardPostRequest;
import com.example.messenger.model.BoardPost;
import com.example.messenger.model.BoardPostType;
import com.example.messenger.model.TaskStatus;
import com.example.messenger.repository.BoardPostRepository;
import com.example.messenger.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BoardServiceTest {

    @Mock
    private BoardPostRepository boardPostRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private BoardService boardService;

    @BeforeEach
    void setUp() {
        boardService = new BoardService(boardPostRepository, userRepository, messagingTemplate);
        // save() just returns whatever entity it was given, like a real repository would
        // (with the id already set, as if the DB had assigned it).
        when(boardPostRepository.save(any(BoardPost.class))).thenAnswer(inv -> {
            BoardPost p = inv.getArgument(0);
            if (p.getId() == null) p.setId(1L);
            return p;
        });
    }

    private CreateBoardPostRequest taskRequest(String assignee, String startAt, String eventAt) {
        CreateBoardPostRequest req = new CreateBoardPostRequest();
        req.setType("TASK");
        req.setTitle("Написать тесты");
        req.setDescription("до конца недели");
        req.setAssigneeUsername(assignee);
        req.setStartAt(startAt);
        req.setEventAt(eventAt);
        return req;
    }

    @Test
    void create_task_defaultsStatusToTodo() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());

        BoardPostDto dto = boardService.create("author1", taskRequest(null, null, null));

        assertThat(dto.getType()).isEqualTo(BoardPostType.TASK);
        assertThat(dto.getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(dto.getAuthorUsername()).isEqualTo("author1");
    }

    @Test
    void create_task_withValidAssignee_setsAssignee() {
        when(userRepository.existsByUsername("bob")).thenReturn(true);
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());

        BoardPostDto dto = boardService.create("author1", taskRequest("bob", null, null));

        assertThat(dto.getAssigneeUsername()).isEqualTo("bob");
    }

    @Test
    void create_task_withUnknownAssignee_throws() {
        when(userRepository.existsByUsername("ghost")).thenReturn(false);

        assertThatThrownBy(() -> boardService.create("author1", taskRequest("ghost", null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Assignee not found");

        verify(boardPostRepository, never()).save(any());
    }

    @Test
    void create_unknownType_throws() {
        CreateBoardPostRequest req = new CreateBoardPostRequest();
        req.setType("NOT_A_TYPE");
        req.setTitle("x");

        assertThatThrownBy(() -> boardService.create("author1", req))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateStatus_nonTaskPost_throws() {
        BoardPost announcement = new BoardPost();
        announcement.setId(5L);
        announcement.setType(BoardPostType.ANNOUNCEMENT);
        when(boardPostRepository.findById(5L)).thenReturn(Optional.of(announcement));

        assertThatThrownBy(() -> boardService.updateStatus(5L, "DONE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Only tasks");
    }

    @Test
    void updateStatus_invalidStatus_throws() {
        BoardPost task = new BoardPost();
        task.setId(5L);
        task.setType(BoardPostType.TASK);
        task.setStatus(TaskStatus.TODO);
        when(boardPostRepository.findById(5L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> boardService.updateStatus(5L, "NOT_A_STATUS"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateStatus_valid_updatesAndReturns() {
        BoardPost task = new BoardPost();
        task.setId(5L);
        task.setType(BoardPostType.TASK);
        task.setStatus(TaskStatus.TODO);
        task.setAuthorUsername("author1");
        when(boardPostRepository.findById(5L)).thenReturn(Optional.of(task));
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());

        BoardPostDto dto = boardService.updateStatus(5L, "in_progress");

        assertThat(dto.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    void updateAssignee_blankUnassigns() {
        BoardPost task = new BoardPost();
        task.setId(7L);
        task.setType(BoardPostType.TASK);
        task.setAssigneeUsername("bob");
        task.setAuthorUsername("author1");
        when(boardPostRepository.findById(7L)).thenReturn(Optional.of(task));
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());

        BoardPostDto dto = boardService.updateAssignee(7L, "  ");

        assertThat(dto.getAssigneeUsername()).isNull();
    }

    @Test
    void updateAssignee_unknownUser_throws() {
        BoardPost task = new BoardPost();
        task.setId(7L);
        task.setType(BoardPostType.TASK);
        when(boardPostRepository.findById(7L)).thenReturn(Optional.of(task));
        when(userRepository.existsByUsername("ghost")).thenReturn(false);

        assertThatThrownBy(() -> boardService.updateAssignee(7L, "ghost"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void delete_byNonAuthor_throwsSecurityException() {
        BoardPost post = new BoardPost();
        post.setId(9L);
        post.setAuthorUsername("author1");
        when(boardPostRepository.findById(9L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> boardService.delete(9L, "someone-else"))
                .isInstanceOf(SecurityException.class);

        verify(boardPostRepository, never()).delete(any());
    }

    @Test
    void delete_byAuthor_deletes() {
        BoardPost post = new BoardPost();
        post.setId(9L);
        post.setAuthorUsername("author1");
        when(boardPostRepository.findById(9L)).thenReturn(Optional.of(post));

        boardService.delete(9L, "author1");

        ArgumentCaptor<BoardPost> captor = ArgumentCaptor.forClass(BoardPost.class);
        verify(boardPostRepository).delete(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(9L);
    }
}
