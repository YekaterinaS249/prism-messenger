package com.example.messenger.controller;

import com.example.messenger.dto.CallSignalPayload;
import com.example.messenger.dto.ChatMessagePayload;
import com.example.messenger.dto.EditPayload;
import com.example.messenger.dto.PinPayload;
import com.example.messenger.dto.ReactionPayload;
import com.example.messenger.dto.TypingPayload;
import com.example.messenger.dto.VotePayload;
import com.example.messenger.repository.UserRepository;
import com.example.messenger.service.ChatService;
import com.example.messenger.service.GroupService;
import com.example.messenger.service.MessageService;
import com.example.messenger.service.UserService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class ChatWebSocketController {

    private final ChatService chatService;
    private final GroupService groupService;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;
    private final UserService userService;
    private final MessageService messageService;

    public ChatWebSocketController(ChatService chatService, GroupService groupService,
                                    SimpMessagingTemplate messagingTemplate, UserRepository userRepository,
                                    UserService userService, MessageService messageService) {
        this.chatService = chatService;
        this.groupService = groupService;
        this.messagingTemplate = messagingTemplate;
        this.userRepository = userRepository;
        this.userService = userService;
        this.messageService = messageService;
    }

    /**
     * Client sends to /app/chat.send. If payload.groupId is set, it's a group/channel message
     * (broadcast to /topic/group.{id}); otherwise it's a 1-on-1 direct message (relayed to the
     * sender + recipient private queues). The payload is enriched with a timestamp and sender
     * name, relayed live to currently connected recipients, and — unless it's a self-destructing
     * message — saved via MessageService so it's there on reload (see /api/messages/**).
     */
    @MessageMapping("/chat.send")
    public void send(@Payload ChatMessagePayload payload, Principal principal) {
        // Trust the authenticated principal for the sender, not client input.
        payload.setSenderUsername(principal.getName());

        if (payload.getGroupId() != null) {
            if (!groupService.isMember(payload.getGroupId(), principal.getName())) {
                return; // silently ignore messages from non-members
            }
            payload.setRecipientUsername(null);
            ChatMessagePayload out = chatService.enrich(payload);
            messageService.save(out);
            messagingTemplate.convertAndSend("/topic/group." + out.getGroupId(), out);
            return;
        }

        ChatMessagePayload out = chatService.enrich(payload);
        messageService.save(out);
        // If the recipient has blocked us, the message is simply never delivered to them — we
        // still echo it back to the sender so their own composer/UI doesn't appear broken.
        if (!userService.isBlocked(out.getRecipientUsername(), out.getSenderUsername())) {
            messagingTemplate.convertAndSendToUser(out.getRecipientUsername(), "/queue/messages", out);
        }
        messagingTemplate.convertAndSendToUser(out.getSenderUsername(), "/queue/messages", out);
    }

    /** Client sends to /app/chat.typing to notify others that this user is typing (direct or group chats). */
    @MessageMapping("/chat.typing")
    public void typing(@Payload TypingPayload payload, Principal principal) {
        payload.setSenderUsername(principal.getName());
        userRepository.findByUsername(principal.getName()).ifPresent(u -> payload.setSenderDisplayName(u.getDisplayName()));

        if (payload.getGroupId() != null) {
            if (!groupService.isMember(payload.getGroupId(), principal.getName())) return;
            payload.setRecipientUsername(null);
            messagingTemplate.convertAndSend("/topic/group." + payload.getGroupId(), payload);
            return;
        }

        if (userService.isBlocked(payload.getRecipientUsername(), payload.getSenderUsername())) return;
        messagingTemplate.convertAndSendToUser(payload.getRecipientUsername(), "/queue/typing", payload);
    }

    /**
     * Client sends to /app/chat.edit to edit or delete one of their own messages (referenced by
     * its client-generated id). Broadcast over the same channels as chat.send; nothing is
     * persisted, so this only affects clients that currently have the conversation open.
     */
    @MessageMapping("/chat.edit")
    public void edit(@Payload EditPayload payload, Principal principal) {
        payload.setSenderUsername(principal.getName());

        if (payload.getGroupId() != null) {
            if (!groupService.isMember(payload.getGroupId(), principal.getName())) return;
            payload.setRecipientUsername(null);
            messagingTemplate.convertAndSend("/topic/group." + payload.getGroupId(), payload);
            return;
        }

        messagingTemplate.convertAndSendToUser(payload.getRecipientUsername(), "/queue/messages", payload);
        messagingTemplate.convertAndSendToUser(payload.getSenderUsername(), "/queue/messages", payload);
    }

    /**
     * Client sends to /app/chat.react to add/remove an emoji reaction on a message (referenced by
     * its client-generated id). Broadcast over the same channels as chat.send so every participant
     * currently viewing the conversation updates live; nothing is persisted.
     */
    @MessageMapping("/chat.react")
    public void react(@Payload ReactionPayload payload, Principal principal) {
        payload.setSenderUsername(principal.getName());

        if (payload.getGroupId() != null) {
            if (!groupService.isMember(payload.getGroupId(), principal.getName())) return;
            payload.setRecipientUsername(null);
            messagingTemplate.convertAndSend("/topic/group." + payload.getGroupId(), payload);
            return;
        }

        messagingTemplate.convertAndSendToUser(payload.getRecipientUsername(), "/queue/messages", payload);
        messagingTemplate.convertAndSendToUser(payload.getSenderUsername(), "/queue/messages", payload);
    }

    /**
     * Client sends to /app/chat.vote to pick/un-pick a poll option (referenced by the poll
     * message's client-generated id). Broadcast over the same channels as chat.send; nothing
     * is persisted, so vote tallies only exist for as long as the conversation stays open.
     */
    @MessageMapping("/chat.vote")
    public void vote(@Payload VotePayload payload, Principal principal) {
        payload.setSenderUsername(principal.getName());

        if (payload.getGroupId() != null) {
            if (!groupService.isMember(payload.getGroupId(), principal.getName())) return;
            payload.setRecipientUsername(null);
            messagingTemplate.convertAndSend("/topic/group." + payload.getGroupId(), payload);
            return;
        }

        messagingTemplate.convertAndSendToUser(payload.getRecipientUsername(), "/queue/messages", payload);
        messagingTemplate.convertAndSendToUser(payload.getSenderUsername(), "/queue/messages", payload);
    }

    /**
     * Client sends to /app/chat.pin to pin/unpin a message (referenced by its client-generated
     * id) for everyone currently viewing the conversation. Nothing is persisted — a client that
     * opens the chat later starts with no pin, same as message history itself.
     */
    @MessageMapping("/chat.pin")
    public void pin(@Payload PinPayload payload, Principal principal) {
        payload.setSenderUsername(principal.getName());

        if (payload.getGroupId() != null) {
            if (!groupService.isMember(payload.getGroupId(), principal.getName())) return;
            payload.setRecipientUsername(null);
            messagingTemplate.convertAndSend("/topic/group." + payload.getGroupId(), payload);
            return;
        }

        messagingTemplate.convertAndSendToUser(payload.getRecipientUsername(), "/queue/messages", payload);
        messagingTemplate.convertAndSendToUser(payload.getSenderUsername(), "/queue/messages", payload);
    }

    /**
     * Client sends to /app/call.signal for WebRTC call setup: offer/answer/ICE-candidate/hangup/
     * reject/busy/ringing messages. Direct 1:1 signaling and per-peer group-mesh negotiation both
     * target a single recipientUsername via their private queue. Group calls additionally use
     * broadcast "join"/"leave" events (recipientUsername null, groupId set) over the group topic
     * so every member's client knows who to open a mesh connection to. The server never inspects
     * sdp/candidate — it's a pure relay.
     */
    @MessageMapping("/call.signal")
    public void callSignal(@Payload CallSignalPayload payload, Principal principal) {
        payload.setSenderUsername(principal.getName());
        userRepository.findByUsername(principal.getName())
                .ifPresent(u -> payload.setSenderDisplayName(u.getDisplayName()));

        if (payload.getGroupId() != null && (payload.getRecipientUsername() == null || payload.getRecipientUsername().isBlank())) {
            if (!groupService.isMember(payload.getGroupId(), principal.getName())) return;
            messagingTemplate.convertAndSend("/topic/group." + payload.getGroupId(), payload);
            return;
        }

        if (payload.getRecipientUsername() == null || payload.getRecipientUsername().isBlank()) return;

        messagingTemplate.convertAndSendToUser(payload.getRecipientUsername(), "/queue/messages", payload);
        messagingTemplate.convertAndSendToUser(payload.getSenderUsername(), "/queue/messages", payload);
    }
}
