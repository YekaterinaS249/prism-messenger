package com.example.messenger.service;

import com.example.messenger.dto.ChatMessagePayload;
import com.example.messenger.dto.PageResponse;
import com.example.messenger.model.Message;
import com.example.messenger.model.MessageType;
import com.example.messenger.model.ReadMarker;
import com.example.messenger.repository.MessageRepository;
import com.example.messenger.repository.ReadMarkerRepository;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Persists chat messages sent via /app/chat.send and serves history for a direct chat or a
 * group. Reactions, edits, poll votes and pins stay live-only (see ChatWebSocketController) —
 * history reflects messages as they were originally sent, not their later live-edited state.
 * Deletions ARE reflected (soft-deleted rows are excluded from history), matching the fact that
 * a deleted message also just disappears from currently-open live conversations.
 *
 * Also tracks per-user read markers (ReadMarker) so unread counts survive being offline —
 * before this, "unread" only ever counted messages that arrived while the recipient was
 * actively connected.
 */
@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final ReadMarkerRepository readMarkerRepository;
    private final ObjectMapper objectMapper;

    public MessageService(MessageRepository messageRepository, ReadMarkerRepository readMarkerRepository,
                           ObjectMapper objectMapper) {
        this.messageRepository = messageRepository;
        this.readMarkerRepository = readMarkerRepository;
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

    /**
     * Soft-deletes the stored copy of a message a user just deleted live (see
     * ChatWebSocketController#edit). Scoped to (clientId, senderUsername) so only the original
     * sender's own message can ever be affected — a cheap safety net matching the doc comment on
     * EditPayload ("only the original sender may issue this").
     */
    public void markDeleted(String clientId, String senderUsername) {
        if (clientId == null) return;
        messageRepository.findFirstByClientIdAndSenderUsername(clientId, senderUsername)
                .ifPresent(m -> { m.setDeleted(true); messageRepository.save(m); });
    }

    public void markDirectRead(String me, String peer) {
        ReadMarker marker = readMarkerRepository.findByUsernameAndPeerUsername(me, peer).orElseGet(() -> {
            ReadMarker m = new ReadMarker();
            m.setUsername(me);
            m.setPeerUsername(peer);
            return m;
        });
        marker.setLastReadAt(Instant.now());
        readMarkerRepository.save(marker);
    }

    public void markGroupRead(String me, Long groupId) {
        ReadMarker marker = readMarkerRepository.findByUsernameAndGroupId(me, groupId).orElseGet(() -> {
            ReadMarker m = new ReadMarker();
            m.setUsername(me);
            m.setGroupId(groupId);
            return m;
        });
        marker.setLastReadAt(Instant.now());
        readMarkerRepository.save(marker);
    }

    /**
     * Unread counts per direct peer and per group, including messages that arrived while the
     * user was offline (now possible since messages are persisted). A conversation with no read
     * marker yet is treated as fully unread — harmless in practice since message persistence
     * itself only just shipped, so there's no large historical backlog to worry about.
     */
    public Map<String, Object> getUnreadSummary(String me, List<Long> myGroupIds) {
        Map<String, Instant> directMarkers = new HashMap<>();
        Map<Long, Instant> groupMarkers = new HashMap<>();
        for (ReadMarker rm : readMarkerRepository.findByUsername(me)) {
            if (rm.getPeerUsername() != null) directMarkers.put(rm.getPeerUsername(), rm.getLastReadAt());
            else if (rm.getGroupId() != null) groupMarkers.put(rm.getGroupId(), rm.getLastReadAt());
        }

        Map<String, Long> direct = new HashMap<>();
        for (Message m : messageRepository.findAllReceivedDirect(me)) {
            Instant since = directMarkers.get(m.getSenderUsername());
            if (since == null || m.getCreatedAt().isAfter(since)) {
                direct.merge(m.getSenderUsername(), 1L, Long::sum);
            }
        }

        Map<String, Long> group = new HashMap<>();
        if (myGroupIds != null && !myGroupIds.isEmpty()) {
            for (Message m : messageRepository.findAllReceivedInGroups(myGroupIds, me)) {
                Instant since = groupMarkers.get(m.getGroupId());
                if (since == null || m.getCreatedAt().isAfter(since)) {
                    group.merge(String.valueOf(m.getGroupId()), 1L, Long::sum);
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("direct", direct);
        result.put("group", group);
        return result;
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
