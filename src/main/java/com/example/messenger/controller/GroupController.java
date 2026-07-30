package com.example.messenger.controller;

import com.example.messenger.dto.CreateGroupRequest;
import com.example.messenger.dto.GroupDto;
import com.example.messenger.dto.GroupMemberDto;
import com.example.messenger.service.GroupService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    /** Groups the current user already belongs to. */
    @GetMapping("/mine")
    public List<GroupDto> mine(Authentication authentication) {
        return groupService.listMine(authentication.getName());
    }

    @PostMapping
    public GroupDto create(Authentication authentication, @Valid @RequestBody CreateGroupRequest request) {
        return groupService.createGroup(authentication.getName(), request);
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<?> members(Authentication authentication, @PathVariable Long id) {
        try {
            return ResponseEntity.ok(groupService.listMembers(id, authentication.getName()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/leave")
    public ResponseEntity<?> leave(Authentication authentication, @PathVariable Long id) {
        try {
            groupService.leaveGroup(id, authentication.getName());
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(Authentication authentication, @PathVariable Long id) {
        try {
            groupService.deleteGroup(id, authentication.getName());
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}/members/{username}")
    public ResponseEntity<?> kick(Authentication authentication, @PathVariable Long id, @PathVariable String username) {
        try {
            groupService.kickMember(id, authentication.getName(), username);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/members/{username}/role")
    public ResponseEntity<?> setRole(Authentication authentication, @PathVariable Long id, @PathVariable String username,
                                      @RequestBody Map<String, String> body) {
        try {
            groupService.setMemberRole(id, authentication.getName(), username, body.get("role"));
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
