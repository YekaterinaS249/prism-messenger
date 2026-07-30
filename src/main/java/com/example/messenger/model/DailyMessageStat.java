package com.example.messenger.model;

import javax.persistence.*;
import java.time.LocalDate;

/**
 * One row per calendar day holding a running total of messages sent (direct + group) on that
 * day. No message content, sender, or recipient is ever recorded here — just an aggregate count
 * for the admin analytics chart. This is the only message-related data persisted anywhere in the
 * app; everything else about a message stays purely in-memory/live-relayed (see ChatService).
 */
@Entity
@Table(name = "daily_message_stats")
public class DailyMessageStat {

    @Id
    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

    @Column(name = "message_count", nullable = false)
    private long messageCount;

    public DailyMessageStat() {}

    public DailyMessageStat(LocalDate statDate, long messageCount) {
        this.statDate = statDate;
        this.messageCount = messageCount;
    }

    public LocalDate getStatDate() { return statDate; }
    public void setStatDate(LocalDate statDate) { this.statDate = statDate; }

    public long getMessageCount() { return messageCount; }
    public void setMessageCount(long messageCount) { this.messageCount = messageCount; }
}
