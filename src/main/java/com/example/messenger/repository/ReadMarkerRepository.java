package com.example.messenger.repository;

import com.example.messenger.model.ReadMarker;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReadMarkerRepository extends JpaRepository<ReadMarker, Long> {
    Optional<ReadMarker> findByUsernameAndPeerUsername(String username, String peerUsername);
    Optional<ReadMarker> findByUsernameAndGroupId(String username, Long groupId);
    List<ReadMarker> findByUsername(String username);
}
