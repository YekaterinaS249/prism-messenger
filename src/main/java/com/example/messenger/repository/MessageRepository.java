package com.example.messenger.repository;

import com.example.messenger.model.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("SELECT m FROM Message m WHERE m.groupId = :groupId AND m.deleted = false ORDER BY m.createdAt DESC")
    Page<Message> findGroupHistory(@Param("groupId") Long groupId, Pageable pageable);

    @Query("SELECT m FROM Message m WHERE m.groupId IS NULL AND m.deleted = false AND " +
            "((m.senderUsername = :a AND m.recipientUsername = :b) OR " +
            " (m.senderUsername = :b AND m.recipientUsername = :a)) " +
            "ORDER BY m.createdAt DESC")
    Page<Message> findDirectHistory(@Param("a") String userA, @Param("b") String userB, Pageable pageable);

    Optional<Message> findFirstByClientIdAndSenderUsername(String clientId, String senderUsername);

    // Lightweight fetch for computing unread counts — see MessageService.getUnreadSummary().
    @Query("SELECT m FROM Message m WHERE m.recipientUsername = :me AND m.groupId IS NULL AND m.deleted = false")
    List<Message> findAllReceivedDirect(@Param("me") String me);

    @Query("SELECT m FROM Message m WHERE m.groupId IN :groupIds AND m.senderUsername <> :me AND m.deleted = false")
    List<Message> findAllReceivedInGroups(@Param("groupIds") List<Long> groupIds, @Param("me") String me);
}
