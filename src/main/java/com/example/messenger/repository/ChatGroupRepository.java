package com.example.messenger.repository;

import com.example.messenger.model.ChatGroup;
import com.example.messenger.model.GroupType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatGroupRepository extends JpaRepository<ChatGroup, Long> {
    List<ChatGroup> findByType(GroupType type);
}
