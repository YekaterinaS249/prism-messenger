package com.example.messenger.repository;

import com.example.messenger.model.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("SELECT m FROM Message m WHERE m.groupId = :groupId ORDER BY m.createdAt DESC")
    Page<Message> findGroupHistory(@Param("groupId") Long groupId, Pageable pageable);

    @Query("SELECT m FROM Message m WHERE m.groupId IS NULL AND " +
            "((m.senderUsername = :a AND m.recipientUsername = :b) OR " +
            " (m.senderUsername = :b AND m.recipientUsername = :a)) " +
            "ORDER BY m.createdAt DESC")
    Page<Message> findDirectHistory(@Param("a") String userA, @Param("b") String userB, Pageable pageable);
}
