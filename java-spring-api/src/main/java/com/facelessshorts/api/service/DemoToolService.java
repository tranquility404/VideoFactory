package com.facelessshorts.api.service;

import java.time.Duration;
import java.util.Map;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

/** Deliberately slow, deterministic tools for exercising the chat UI. */
@Service
public class DemoToolService {

    @Tool(description = "Demo tool that simulates researching a topic. Use it when the user asks to research, investigate, or gather facts. It is safe and has no external side effects.")
    public Map<String, Object> researchTopic(String topic) {
        pause(Duration.ofSeconds(3));
        return Map.of("topic", topic, "sources", 3,
                "finding", "A demo research result for '" + topic + "' with three illustrative sources.");
    }

    @Tool(description = "Demo tool that simulates generating a short-video production estimate. Use it when the user asks for an estimate, shot list, or production plan. It is safe and has no external side effects.")
    public Map<String, Object> estimateShort(String subject, int seconds) {
        pause(Duration.ofSeconds(2));
        return Map.of("subject", subject, "seconds", seconds, "shots", Math.max(3, seconds / 4),
                "estimate", "Demo estimate: storyboard, voiceover, captions, and 4 visual beats.");
    }

    @Tool(description = "Demo tool that simulates checking a render job. Use it when the user asks for render status or a progress check. It is safe and has no external side effects.")
    public Map<String, Object> checkRenderStatus(String jobId) {
        pause(Duration.ofSeconds(4));
        return Map.of("jobId", jobId, "status", "ready", "progress", 100,
                "message", "Demo render finished successfully.");
    }

    private void pause(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Demo tool interrupted", exception);
        }
    }
}