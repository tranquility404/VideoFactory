package com.facelessshorts.api.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.session.SessionService;
import org.springframework.ai.session.advisor.SessionMemoryAdvisor;
import org.springframework.ai.session.compaction.RecursiveSummarizationCompactionStrategy;
import org.springframework.ai.session.compaction.TokenCountTrigger;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.facelessshorts.api.service.DemoToolService;

/** Chat client, tool and bounded conversation-memory configuration. */
@Configuration
public class ChatConfig {

    @Bean
    public SessionMemoryAdvisor sessionMemoryAdvisor(SessionService sessionService, ChatClient.Builder chatClientBuilder) {
        TokenCountTrigger tokenTrigger = TokenCountTrigger.builder()
                        .threshold(4000)
                        .tokenCountEstimator(new JTokkitTokenCountEstimator())
                        .build();

        /*
         * Separate ChatClient for summarization.
         *
         * Do not attach SessionMemoryAdvisor to this client,
         * otherwise summarization could recursively trigger
         * another summarization.
         */
        ChatClient summarizationClient = chatClientBuilder.build();

        RecursiveSummarizationCompactionStrategy strategy = RecursiveSummarizationCompactionStrategy
                        .builder(summarizationClient)
                        .maxEventsToKeep(5)
                        .overlapSize(2)
                        .build();

        return SessionMemoryAdvisor
                .builder(sessionService)
                .compactionTrigger(tokenTrigger)
                .compactionStrategy(strategy)
                .build();
    }

    @Bean
    ChatClient chatClient (
        ChatClient.Builder builder, 
        SessionMemoryAdvisor sessionMemoryAdvisor,
        DemoToolService demoTools
    ) {
        return builder
                .defaultSystem(
                        "You are a helpful faceless-video studio copilot. Use the demo tools when the user asks for a demo, weather, research, or a production estimate. Explain tool results clearly.")
                .defaultAdvisors(sessionMemoryAdvisor)
                .defaultTools(demoTools)
                .build();
    }
}