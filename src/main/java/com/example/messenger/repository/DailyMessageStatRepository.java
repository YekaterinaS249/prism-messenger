package com.example.messenger.repository;

import com.example.messenger.model.DailyMessageStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

public interface DailyMessageStatRepository extends JpaRepository<DailyMessageStat, LocalDate> {

    List<DailyMessageStat> findByStatDateGreaterThanEqualOrderByStatDateAsc(LocalDate from);

    /** Atomic +1 on an existing row. Returns 0 (no-op) if today's row doesn't exist yet. */
    @Modifying
    @Transactional
    @Query("UPDATE DailyMessageStat d SET d.messageCount = d.messageCount + 1 WHERE d.statDate = :date")
    int incrementIfExists(@Param("date") LocalDate date);
}
