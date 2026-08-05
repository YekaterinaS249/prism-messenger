package com.example.messenger.service;

import com.example.messenger.dto.ChatMessagePayload;
import com.example.messenger.dto.PageResponse;
import com.example.messenger.model.Message;
import com.example.messenger.model.MessageType;
import com.example.messenger.repository.MessageRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Persists chat messages sent via /app/chat.send and serves history for a direct chat or a
 * group. Reactions, edits, deletions, poll votes and pins stay live-only (see ChatWebSocketController)
 * — history reflects messages as they were originally sent, not their later live-edited state.
 */
@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final ObjectMapper objectMapper;

    public MessageService(MessageRepository messageRepository, ObjectMapper objectMapper) {
        this.messageRepository = messageRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Saves a copy of an already-enriched outgoing payload. Self-destructing messages
     * (expiresInSeconds set) are intentionally skipped — persisting them would defeat the point.
     */
    public void save(ChatMessagePayload payload) {
        if (payload.getExpiresInSeconds() != null) {
            return;
        }
        Message m = new Message();
        m.setSenderUsername(payload.getSenderUsername());
        m.setSenderDisplayName(payload.getSenderDisplayName());
        m.setSenderAvatarUrl(payload.getSenderAvatarUrl());
        m.setRecipientUsername(payload.getRecipientUsername());
        m.setGroupId(payload.getGroupId());
        m.setContent(payload.getContent());
        m.setType(payload.getType() != null ? payload.getType().name() : MessageType.TEXT.name());
        m.setMediaUrl(payload.getMediaUrl());
        m.setMediaName(payload.getMediaName());
        m.setCreatedAt(Instant.now());
        m.setClientId(payload.getClientId());
        m.setReplyToClientId(payload.getReplyToClientId());
        m.setReplyToSenderName(payload.getReplyToSenderName());
        m.setReplyToSnippet(payload.getReplyToSnippet());
        m.setPollQuestion(payload.getPollQuestion());
        if (payload.getPollOptions() != null) {
            try {
                m.setPollOptionsJson(objectMapper.writeValueAsString(payload.getPollOptions()));
            } catch (Exception ignored) { /* history is best-effort; never block sending on this */ }
        }
        m.setAction(payload.isAction());
        m.setEncrypted(payload.isEncrypted());
        m.setIv(payload.getIv());
        m.setForwardedFrom(payload.getForwardedFrom());
        m.setLat(payload.getLat());
        m.setLng(payload.getLng());
        messageRepository.save(m);
    }

    public PageResponse<ChatMessagePayload> getDirectHistory(String userA, String userB, int page, int size) {
        Page<Message> result = messageRepository.findDirectHistory(userA, userB, PageRequest.of(page, size));
        return toPageResponse(result);
    }

    public PageResponse<ChatMessagePayload> getGroupHistory(Long groupId, int page, int size) {
        Page<Message> result = messageRepository.findGroupHistory(groupId, PageRequest.of(page, size));
        return toPageResponse(result);
    }

    private PageResponse<ChatMessagePayload> toPageResponse(Page<Message> result) {
        // Repository returns newest-first (for efficient "most recent page" pagination); the UI
        // renders top-to-bottom chronologically, so reverse just the current page here.
        List<ChatMessagePayload> content = new ArrayList<>(result.getContent().size());
        for (Message m : result.getContent()) {
            content.add(toPayload(m));
        }
        Collections.reverse(content);
        return new PageResponse<>(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    private ChatMessagePayload toPayload(Message m) {
        ChatMessagePayload p = new ChatMessagePayload();
        p.setSenderUsername(m.getSenderUsername());
        p.setSenderDisplayName(m.getSenderDisplayName());
        p.setSenderAvatarUrl(m.getSenderAvatarUrl());
        p.setRecipientUsername(m.getRecipientUsername());
        p.setGroupId(m.getGroupId());
        p.setContent(m.getContent());
        try {
            p.setType(MessageType.valueOf(m.getType()));
        } catch (Exception e) {
            p.setType(MessageType.TEXT);
        }
        p.setMediaUrl(m.getMediaUrl());
        p.setMediaName(m.getMediaName());
        p.setCreatedAt(DateTimeFormatter.ISO_INSTANT.format(m.getCreatedAt().atZone(ZoneOffset.UTC)));
        p.setClientId(m.getClientId());
        p.setReplyToClientId(m.getReplyToClientId());
        p.setReplyToSenderName(m.getReplyToSenderName());
        p.setReplyToSnippet(m.getReplyToSnippet());
        p.setPollQuestion(m.getPollQuestion());
        if (m.getPollOptionsJson() != null) {
            try {
                p.setPollOptions(objectMapper.readValue(m.getPollOptionsJson(), new TypeReference<List<String>>() {}));
            } catch (Exception ignored) { /* leave null */ }
        }
        p.setAction(m.isAction());
        p.setEncrypted(m.isEncrypted());
        p.setIv(m.getIv());
        p.setForwardedFrom(m.getForwardedFrom());
        p.setLat(m.getLat());
        p.setLng(m.getLng());
        return p;
    }
}
