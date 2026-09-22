package com.facelessshorts.api.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.session.advisor.SessionMemoryAdvisor;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.facelessshorts.chat.ChatEventStream;
import com.facelessshorts.model.ChatRequest;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor 
public class AgentChatService {
    private final ChatClient client;
    private final AgentSkillService skills;
    private final JsonMapper jsonMapper;

    public Flux<ServerSentEvent<String>> stream(ChatRequest request) {
        String sessionId = StringUtils.hasText(request.sessionId()) ? request.sessionId() : "default";
        
        ChatEventStream events = new ChatEventStream(jsonMapper);

        Flux<ServerSentEvent<String>> answerTokens = client.prompt()
                // .system(systemPrompt(skill))
                .user(request.message())
                .advisors(a -> a.advisors(events.observer())
                        .param(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, sessionId))
                .stream()
                .content()
                .map(events::token);

        return events.mergeWith(answerTokens);
    }

    private String systemPrompt(String skill) {
        return "You are the repository's Spring video-production agent. Follow the selected skill exactly. "
                + "Use only the registered Spring tools; never invent Python scripts or shell commands. "
                + "Preserve artifact contracts and approval gates.\n\nSELECTED SKILL:\n" + skills.promptFor(skill);
    }
}