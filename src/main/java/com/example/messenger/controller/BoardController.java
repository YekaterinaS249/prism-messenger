package com.example.messenger.controller;

import com.example.messenger.dto.BoardPostDto;
import com.example.messenger.dto.CreateBoardPostRequest;
import com.example.messenger.dto.UpdateAssigneeRequest;
import com.example.messenger.dto.UpdatePriorityRequest;
import com.example.messenger.dto.UpdateTaskStatusRequest;
import com.example.messenger.service.BoardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/board")
public class BoardController {

    private final BoardService boardService;

    public BoardController(BoardService boardService) {
        this.boardService = boardService;
    }

    /** Shared board, visible to every user. Admins additionally see "seen by" on tasks. */
    @GetMapping
    public List<BoardPostDto> list(Authentication authentication) {
        return boardService.listAll(authentication.getName());
    }

    @PostMapping
    public ResponseEntity<?> create(Authentication authentication, @Valid @RequestBody CreateBoardPostRequest request) {
        try {
            return ResponseEntity.ok(boardService.create(authentication.getName(), request));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(Authentication authentication, @PathVariable Long id,
                                           @Valid @RequestBody UpdateTaskStatusRequest request) {
        try {
            return ResponseEntity.ok(boardService.updateStatus(id, request.getStatus(), authentication.getName()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/assignee")
    public ResponseEntity<?> updateAssignee(Authentication authentication, @PathVariable Long id,
                                             @Valid @RequestBody UpdateAssigneeRequest request) {
        try {
            return ResponseEntity.ok(boardService.updateAssignee(id, request.getAssigneeUsername(), authentication.getName()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/priority")
    public ResponseEntity<?> updatePriority(Authentication authentication, @PathVariable Long id,
                                             @Valid @RequestBody UpdatePriorityRequest request) {
        try {
            return ResponseEntity.ok(boardService.updatePriority(id, request.getPriority(), authentication.getName()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Marks that the current user has opened this post — feeds the admin-only "seen by" list. */
    @PostMapping("/{id}/view")
    public ResponseEntity<?> markViewed(Authentication authentication, @PathVariable Long id) {
        try {
            boardService.markViewed(id, authentication.getName());
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(Authentication authentication, @PathVariable Long id) {
        try {
            boardService.delete(id, authentication.getName());
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
