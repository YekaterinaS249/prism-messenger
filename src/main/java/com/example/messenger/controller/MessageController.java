package com.example.messenger.controller;

import com.example.messenger.dto.ChatMessagePayload;
import com.example.messenger.dto.PageResponse;
import com.example.messenger.repository.GroupMemberRepository;
import com.example.messenger.service.GroupService;
import com.example.messenger.service.MessageService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;
    private final GroupService groupService;
    private final GroupMemberRepository groupMemberRepository;

    public MessageController(MessageService messageService, GroupService groupService,
                              GroupMemberRepository groupMemberRepository) {
        this.messageService = messageService;
        this.groupService = groupService;
        this.groupMemberRepository = groupMemberRepository;
    }

    /** History for a direct chat with {username} — always scoped to the caller's own side of it. */
    @GetMapping("/direct/{username}")
    public PageResponse<ChatMessagePayload> directHistory(Authentication authentication,
                                                            @PathVariable String username,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "50") int size) {
        return messageService.getDirectHistory(authentication.getName(), username, page, size);
    }

    @GetMapping("/group/{groupId}")
    public ResponseEntity<?> groupHistory(Authentication authentication,
                                           @PathVariable Long groupId,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "50") int size) {
        if (!groupService.isMember(groupId, authentication.getName())) {
            return ResponseEntity.status(403).body(Map.of("error", "Not a member of this group"));
        }
        return ResponseEntity.ok(messageService.getGroupHistory(groupId, page, size));
    }

    /** Marks a direct chat as read "now" — call when the user opens/is looking at this chat. */
    @PostMapping("/direct/{username}/read")
    public ResponseEntity<?> markDirectRead(Authentication authentication, @PathVariable String username) {
        messageService.markDirectRead(authentication.getName(), username);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/group/{groupId}/read")
    public ResponseEntity<?> markGroupRead(Authentication authentication, @PathVariable Long groupId) {
        if (!groupService.isMember(groupId, authentication.getName())) {
            return ResponseEntity.status(403).body(Map.of("error", "Not a member of this group"));
        }
        messageService.markGroupRead(authentication.getName(), groupId);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /**
     * Unread counts per direct peer and per group — including anything that arrived while the
     * caller was offline. Call once on app start and merge into the client's local unread state.
     */
    @GetMapping("/unread-summary")
    public Map<String, Object> unreadSummary(Authentication authentication) {
        List<Long> myGroupIds = groupMemberRepository.findByUsername(authentication.getName()).stream()
                .map(gm -> gm.getGroup().getId())
                .collect(Collectors.toList());
        return messageService.getUnreadSummary(authentication.getName(), myGroupIds);
    }
}
