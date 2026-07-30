package com.example.messenger.repository;

import com.example.messenger.model.UserSticker;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserStickerRepository extends JpaRepository<UserSticker, Long> {
    List<UserSticker> findByOwnerUsernameOrderByCreatedAtDesc(String ownerUsername);
}
