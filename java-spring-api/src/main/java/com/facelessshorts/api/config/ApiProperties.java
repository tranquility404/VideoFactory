package com.facelessshorts.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tools")
public record ApiProperties(
        String pythonCommand,
        long defaultTimeoutSeconds,
        String nodeCommand,
        String ffmpegCommand,
        String ffprobeCommand,
        String chromiumExecutable,
        String remotionScript,
        String elevenLabsApiKey,
        String elevenLabsBaseUrl,
        String falApiKey,
        String falQueueUrl,
        String falStorageUrl,
        String cloudflareApiUrl,
        String cloudflareApiKey,
        String defaultVoice,
        String defaultVoiceModel,
        String defaultImageModel,
        String defaultClipModel) {}