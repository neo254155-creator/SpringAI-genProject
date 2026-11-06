package com.first.genProject.config;

import org.springframework.ai.chat.client.*;
import org.springframework.ai.chat.client.advisor.api.*;
import com.first.genProject.service.AiQuota;

/** Covers every synchronous call made by the shared application ChatClient. */
public class QuotaAdvisor implements CallAdvisor {
    private final AiQuota quota;
    public QuotaAdvisor(AiQuota quota) { this.quota=quota; }
    public String getName() { return "AiQuota"; }
    public int getOrder() { return Integer.MAX_VALUE - 100; }
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        long size=request.prompt().getInstructions().stream().mapToLong(m -> m.getText()==null?0:m.getText().length()).sum();
        if (size>20000) throw new com.first.genProject.exception.InvalidInputException("AI prompt exceeds 20000 characters. Shorten the input.");
        return quota.call((int)size, () -> chain.nextCall(request));
    }
}
