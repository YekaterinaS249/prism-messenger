package com.example.messenger.model;

import javax.persistence.*;
import java.time.LocalDate;

/** Same idea as DailyMessageStat, but broken down per group, for the "top active groups" widget. */
@Entity
@Table(name = "daily_group_message_stats")
public class DailyGroupMessageStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "message_count", nullable = false)
    private long messageCount;

    public DailyGroupMessageStat() {}

    public DailyGroupMessageStat(LocalDate statDate, Long groupId, long messageCount) {
        this.statDate = statDate;
        this.groupId = groupId;
        this.messageCount = messageCount;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDate getStatDate() { return statDate; }
    public void setStatDate(LocalDate statDate) { this.statDate = statDate; }

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }

    public long getMessageCount() { return messageCount; }
    public void setMessageCount(long messageCount) { this.messageCount = messageCount; }
}
