package com.facelessshorts.api.web;

import com.facelessshorts.api.service.AgentChatService;
import com.facelessshorts.api.service.AgentSkillService;
import com.facelessshorts.model.ChatRequest;

import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
public class ChatPageController {
    private final AgentSkillService skills;
    private final AgentChatService chat;
    private final TemplateEngine templates;

    public ChatPageController(AgentSkillService skills, AgentChatService chat, TemplateEngine templates) {
        this.skills = skills;
        this.chat = chat;
        this.templates = templates;
    }

    @GetMapping(value = "/chat", produces = MediaType.TEXT_HTML_VALUE)
    public String page() {
        StringOutput output = new StringOutput();
        templates.render("chat.jte", Map.of("skills", skills.names()), output);
        return output.toString();
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@RequestBody ChatRequest request) {
        return chat.stream(request)
                .onErrorResume(IllegalStateException.class, e -> Flux.just(
                        ServerSentEvent.<String>builder().event("error").data(e.getMessage()).build()));
    }

    @GetMapping("/chat/config")
    public Map<String, Object> config() {
        return Map.of("skills", skills.names());
    }
}