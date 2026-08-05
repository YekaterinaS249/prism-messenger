package com.example.messenger.controller;

import com.example.messenger.dto.ChatMessagePayload;
import com.example.messenger.dto.PageResponse;
import com.example.messenger.service.GroupService;
import com.example.messenger.service.MessageService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;
    private final GroupService groupService;

    public MessageController(MessageService messageService, GroupService groupService) {
        this.messageService = messageService;
        this.groupService = groupService;
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
}
