package com.facelessshorts.chat;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.util.StringUtils;

import reactor.core.publisher.Flux;

public class ToolCallObservingAdvisor implements CallAdvisor, StreamAdvisor {

    /** Run inside the tool loop after Spring AI's tool-calling advisor. */
    public static final int ORDER = ToolCallingAdvisor.DEFAULT_ORDER + 100;

    private final Consumer<ToolCallEvent> listener;
    private final Set<String> started = ConcurrentHashMap.newKeySet();
    private final Set<String> completed = ConcurrentHashMap.newKeySet();

    public ToolCallObservingAdvisor(Consumer<ToolCallEvent> listener) {
        this.listener = listener;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        emitCompletions(request);
        return chain.nextStream(request).doOnNext(this::emitStarts);
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        emitCompletions(request);
        ChatClientResponse response = chain.nextCall(request);
        emitStarts(response);
        return response;
    }

    private void emitStarts(ChatClientResponse response) {
        ChatResponse chatResponse = response.chatResponse();
        if (chatResponse == null) {
            return;
        }
        chatResponse.getResults().stream()
                .flatMap(generation -> generation.getOutput().getToolCalls().stream())
                .filter(toolCall -> StringUtils.hasText(toolCall.id()) && StringUtils.hasText(toolCall.name()))
                .filter(toolCall -> this.started.add(toolCall.id()))
                .forEach(toolCall -> this.listener
                        .accept(new ToolCallEvent(toolCall.id(), toolCall.name(), ToolCallEvent.Phase.STARTED)));
    }

    private void emitCompletions(ChatClientRequest request) {
        request.prompt().getInstructions().stream()
                .filter(ToolResponseMessage.class::isInstance)
                .map(ToolResponseMessage.class::cast)
                .flatMap(message -> message.getResponses().stream())
                .filter(response -> this.started.contains(response.id()) && this.completed.add(response.id()))
                .forEach(response -> this.listener
                        .accept(new ToolCallEvent(response.id(), response.name(), ToolCallEvent.Phase.COMPLETED)));
    }

    @Override
    public String getName() {
        return "toolCallObservingAdvisor";
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}