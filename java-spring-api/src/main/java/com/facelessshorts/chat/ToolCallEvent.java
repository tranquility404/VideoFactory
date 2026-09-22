package com.facelessshorts.chat;

public record ToolCallEvent(String id, String name, Phase phase) {

    public enum Phase {
        STARTED, COMPLETED
    }
}