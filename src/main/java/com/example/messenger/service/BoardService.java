package com.example.messenger.service;

import com.example.messenger.dto.BoardPostDto;
import com.example.messenger.dto.CreateBoardPostRequest;
import com.example.messenger.dto.UserDto;
import com.example.messenger.model.BoardPost;
import com.example.messenger.model.BoardPostType;
import com.example.messenger.model.BoardPostView;
import com.example.messenger.model.TaskPriority;
import com.example.messenger.model.TaskStatus;
import com.example.messenger.model.User;
import com.example.messenger.repository.BoardPostRepository;
import com.example.messenger.repository.BoardPostViewRepository;
import com.example.messenger.repository.UserRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class BoardService {

    private final BoardPostRepository boardPostRepository;
    private final BoardPostViewRepository boardPostViewRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public BoardService(BoardPostRepository boardPostRepository, BoardPostViewRepository boardPostViewRepository,
                         UserRepository userRepository, SimpMessagingTemplate messagingTemplate) {
        this.boardPostRepository = boardPostRepository;
        this.boardPostViewRepository = boardPostViewRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }

    private boolean isAdmin(String username) {
        return userRepository.findByUsername(username).map(User::isAdmin).orElse(false);
    }

    /** Only admins create board posts (schedule entries, announcements or tasks). */
    public BoardPostDto create(String author, CreateBoardPostRequest request) {
        if (!isAdmin(author)) {
            throw new SecurityException("Only an admin can create board posts");
        }
        BoardPostType type;
        try {
            type = BoardPostType.valueOf(request.getType().trim().toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("type must be SCHEDULE or ANNOUNCEMENT");
        }

        BoardPost post = new BoardPost();
        post.setType(type);
        post.setTitle(request.getTitle().trim());
        // NOTE: description is intentionally NOT trimmed here — SCHEDULE posts append an
        // embedded-tables payload behind a "\n<<<TABLES>>>" marker (see app.js), and trimming
        // would strip that leading newline and break parsing on the client.
        post.setDescription(request.getDescription());
        post.setAuthorUsername(author);

        if (type == BoardPostType.TASK) {
            post.setStatus(TaskStatus.TODO);

            TaskPriority priority = TaskPriority.MEDIUM;
            if (request.getPriority() != null && !request.getPriority().isBlank()) {
                try {
                    priority = TaskPriority.valueOf(request.getPriority().trim().toUpperCase());
                } catch (Exception ignored) {
                    // fall back to MEDIUM on garbage input
                }
            }
            post.setPriority(priority);

            if (request.getAssigneeUsername() != null && !request.getAssigneeUsername().isBlank()) {
                String assignee = request.getAssigneeUsername().trim();
                if (!userRepository.existsByUsername(assignee)) {
                    throw new IllegalArgumentException("Assignee not found");
                }
                post.setAssigneeUsername(assignee);
            }

            if (request.getStartAt() != null && !request.getStartAt().isBlank()) {
                try {
                    post.setStartAt(LocalDateTime.parse(request.getStartAt()).atZone(ZoneId.systemDefault()).toInstant());
                } catch (Exception ignored) {
                    // leave startAt empty if the client sent something unparsable
                }
            }
        }

        if ((type == BoardPostType.SCHEDULE || type == BoardPostType.TASK)
                && request.getEventAt() != null && !request.getEventAt().isBlank()) {
            try {
                post.setEventAt(LocalDateTime.parse(request.getEventAt()).atZone(ZoneId.systemDefault()).toInstant());
            } catch (Exception ignored) {
                // leave eventAt empty if the client sent something unparsable
            }
        }

        post = boardPostRepository.save(post);
        BoardPostDto dto = toDto(post, author);
        // Live "new item" signal so every connected client can badge the board icon without
        // polling; the client re-fetches the full list if the board view is currently open.
        messagingTemplate.convertAndSend("/topic/board", dto);
        return dto;
    }

    /**
     * Status changes are split by role: admins may only reset a task back to TODO ("Нужно
     * сделать"); everyone else may only move it between IN_PROGRESS and DONE. This keeps
     * "un-starting" a task an admin decision while letting whoever's doing the work self-report
     * progress.
     */
    public BoardPostDto updateStatus(Long id, String status, String requester) {
        BoardPost post = boardPostRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Post not found"));
        if (post.getType() != BoardPostType.TASK) {
            throw new IllegalArgumentException("Only tasks have a status");
        }
        TaskStatus newStatus;
        try {
            newStatus = TaskStatus.valueOf(status.trim().toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("status must be TODO, IN_PROGRESS or DONE");
        }
        boolean admin = isAdmin(requester);
        if (admin && newStatus != TaskStatus.TODO) {
            throw new SecurityException("Admin can only reset a task to TODO");
        }
        if (!admin && newStatus == TaskStatus.TODO) {
            throw new SecurityException("Only an admin can reset a task to TODO");
        }
        post.setStatus(newStatus);
        post = boardPostRepository.save(post);
        return toDto(post, requester);
    }

    /** Only admins (re)assign a task. */
    public BoardPostDto updateAssignee(Long id, String assigneeUsername, String requester) {
        if (!isAdmin(requester)) {
            throw new SecurityException("Only an admin can assign tasks");
        }
        BoardPost post = boardPostRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Post not found"));
        if (post.getType() != BoardPostType.TASK) {
            throw new IllegalArgumentException("Only tasks can be assigned");
        }
        if (assigneeUsername == null || assigneeUsername.isBlank()) {
            post.setAssigneeUsername(null);
        } else {
            String assignee = assigneeUsername.trim();
            if (!userRepository.existsByUsername(assignee)) {
                throw new IllegalArgumentException("Assignee not found");
            }
            post.setAssigneeUsername(assignee);
        }
        post = boardPostRepository.save(post);
        return toDto(post, requester);
    }

    /** Only admins reprioritize a task. */
    public BoardPostDto updatePriority(Long id, String priority, String requester) {
        if (!isAdmin(requester)) {
            throw new SecurityException("Only an admin can set task priority");
        }
        BoardPost post = boardPostRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Post not found"));
        if (post.getType() != BoardPostType.TASK) {
            throw new IllegalArgumentException("Only tasks have a priority");
        }
        TaskPriority newPriority;
        try {
            newPriority = TaskPriority.valueOf(priority.trim().toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("priority must be LOW, MEDIUM or HIGH");
        }
        post.setPriority(newPriority);
        post = boardPostRepository.save(post);
        return toDto(post, requester);
    }

    public List<BoardPostDto> listAll(String requester) {
        return boardPostRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(p -> toDto(p, requester))
                .collect(Collectors.toList());
    }

    /** Records that `viewer` has opened this post — surfaced to admins as "seen by". */
    public void markViewed(Long id, String viewer) {
        if (!boardPostRepository.existsById(id)) {
            throw new IllegalArgumentException("Post not found");
        }
        BoardPostView.Key key = new BoardPostView.Key(id, viewer);
        if (boardPostViewRepository.existsById(key)) {
            return;
        }
        BoardPostView view = new BoardPostView(id, viewer);
        view.setViewedAt(Instant.now());
        boardPostViewRepository.save(view);
    }

    public void delete(Long id, String requester) {
        if (!isAdmin(requester)) {
            throw new SecurityException("Only an admin can delete board posts");
        }
        BoardPost post = boardPostRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Post not found"));
        boardPostRepository.delete(post);
    }

    private BoardPostDto toDto(BoardPost p, String requester) {
        var author = userRepository.findByUsername(p.getAuthorUsername());
        String authorDisplayName = author.map(u -> u.getDisplayName()).orElse(p.getAuthorUsername());
        String authorAvatarUrl = author.map(u -> u.getAvatarUrl()).orElse(null);

        String assigneeDisplayName = null;
        String assigneeAvatarUrl = null;
        if (p.getAssigneeUsername() != null) {
            var assignee = userRepository.findByUsername(p.getAssigneeUsername());
            assigneeDisplayName = assignee.map(u -> u.getDisplayName()).orElse(p.getAssigneeUsername());
            assigneeAvatarUrl = assignee.map(u -> u.getAvatarUrl()).orElse(null);
        }

        String eventAtStr = p.getEventAt() == null ? null : DateTimeFormatter.ISO_INSTANT.format(p.getEventAt().atZone(ZoneOffset.UTC));
        String startAtStr = p.getStartAt() == null ? null : DateTimeFormatter.ISO_INSTANT.format(p.getStartAt().atZone(ZoneOffset.UTC));
        String createdAtStr = DateTimeFormatter.ISO_INSTANT.format(p.getCreatedAt().atZone(ZoneOffset.UTC));

        List<UserDto> seenBy = null;
        if (p.getType() == BoardPostType.TASK && isAdmin(requester)) {
            seenBy = boardPostViewRepository.findAllByIdPostId(p.getId()).stream()
                    .map(v -> userRepository.findByUsername(v.getId().getUsername()))
                    .filter(java.util.Optional::isPresent)
                    .map(o -> {
                        User u = o.get();
                        return new UserDto(u.getUsername(), u.getDisplayName(), u.getAvatarUrl(), u.getStatus(),
                                false, null, u.isShowOnlineStatus(), null, u.isAdmin());
                    })
                    .collect(Collectors.toList());
        }

        return new BoardPostDto(p.getId(), p.getType(), p.getTitle(), p.getDescription(), eventAtStr, startAtStr,
                p.getStatus(), p.getPriority(), p.getAuthorUsername(), authorDisplayName, authorAvatarUrl,
                p.getAssigneeUsername(), assigneeDisplayName, assigneeAvatarUrl, createdAtStr, seenBy);
    }
}
