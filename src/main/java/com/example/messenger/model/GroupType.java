package com.example.messenger.model;

public enum GroupType {
    /** Private group chat: members are added by the creator, not publicly listed. */
    GROUP,
    /**
     * @deprecated Public channels were replaced by the News feed. No longer creatable or joinable
     * via the API — kept only so any pre-existing CHANNEL rows still deserialize correctly.
     */
    @Deprecated
    CHANNEL
}
