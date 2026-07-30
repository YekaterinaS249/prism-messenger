package com.example.messenger.repository;

import com.example.messenger.model.DailyGroupMessageStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

public interface DailyGroupMessageStatRepository extends JpaRepository<DailyGroupMessageStat, Long> {

    List<DailyGroupMessageStat> findByStatDateGreaterThanEqual(LocalDate from);

    /** Atomic +1 on an existing (date, group) row. Returns 0 (no-op) if it doesn't exist yet. */
    @Modifying
    @Transactional
    @Query("UPDATE DailyGroupMessageStat d SET d.messageCount = d.messageCount + 1 WHERE d.statDate = :date AND d.groupId = :groupId")
    int incrementIfExists(@Param("date") LocalDate date, @Param("groupId") Long groupId);
}
