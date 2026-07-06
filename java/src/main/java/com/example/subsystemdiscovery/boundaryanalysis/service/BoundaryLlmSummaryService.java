package com.example.subsystemdiscovery.boundaryanalysis.service;

import com.example.subsystemdiscovery.boundaryanalysis.dto.BoundarySummaryInput;
import com.example.subsystemdiscovery.config.LlmProperties;
import com.example.subsystemdiscovery.discovery.dto.SummaryType;
import com.example.subsystemdiscovery.llm.LlmClientService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class BoundaryLlmSummaryService {

    private final LlmClientService llmClientService;
    private final BoundarySummaryPromptBuilder promptBuilder;
    private final LlmProperties properties;

    public BoundaryLlmSummaryService(LlmClientService llmClientService,
                                     BoundarySummaryPromptBuilder promptBuilder,
                                     LlmProperties properties) {
        this.llmClientService = llmClientService;
        this.promptBuilder = promptBuilder;
        this.properties = properties;
    }

    public BoundarySummaryResult summarise(BoundarySummaryInput input,
                                           SummaryType summaryType,
                                           String llmModel) {
        String resolvedModel = resolveModel(llmModel);
        if (!properties.isEnabled() || !StringUtils.hasText(properties.getApiKey()) || input == null) {
            return fallback(resolvedModel);
        }

        try {
            String prompt = promptBuilder.build(input, summaryType);
            JsonNode response = llmClientService.callLlm(prompt, resolvedModel);
            String content = llmClientService.extractContent(response);
            if (StringUtils.hasText(content)) {
                return new BoundarySummaryResult(content.strip(), resolvedModel, false);
            }
            return fallback(resolvedModel);
        } catch (Exception ex) {
            return fallback(resolvedModel);
        }
    }

    public String resolveModel(String llmModel) {
        if (StringUtils.hasText(llmModel)) {
            return llmModel;
        }
        if (StringUtils.hasText(properties.getModelId())) {
            return properties.getModelId();
        }
        return "default";
    }

    private BoundarySummaryResult fallback(String resolvedModel) {
        return new BoundarySummaryResult(
                "LLM boundary-node summary not available. Boundary nodes, subsystem-pair hotspots, and risk signals are available in the response.",
                resolvedModel,
                true
        );
    }
}
