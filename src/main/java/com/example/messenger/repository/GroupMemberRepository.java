package com.example.messenger.repository;

import com.example.messenger.model.GroupMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupMemberRepository extends JpaRepository<GroupMember, Long> {
    List<GroupMember> findByGroupId(Long groupId);
    List<GroupMember> findByUsername(String username);
    Optional<GroupMember> findByGroupIdAndUsername(Long groupId, String username);
    boolean existsByGroupIdAndUsername(Long groupId, String username);
    long countByGroupId(Long groupId);
}
