package com.facelessshorts.api.service.integration;
import com.facelessshorts.api.service.infrastructure.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.facelessshorts.api.config.ApiProperties;
import com.facelessshorts.api.error.ApiException;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Component;

/** Small ElevenLabs adapter; keeping it injectable makes media services deterministic in tests. */
@Component
public class ElevenLabsHttpClient {
    private final ToolSupport support;
    private final ObjectMapper json;
    private final ApiProperties properties;

    public ElevenLabsHttpClient(ToolSupport support, ObjectMapper json, ApiProperties properties) {
        this.support = support; this.json = json; this.properties = properties;
    }

    public byte[] music(String prompt, long lengthMs, String model, boolean instrumental, String format) {
        requireKey();
        return support.postBytes(base("/v1/music") + "?output_format=" + format,
                headers(), Map.of("prompt", prompt, "music_length_ms", lengthMs,
                        "model_id", model, "force_instrumental", instrumental), Duration.ofSeconds(300));
    }

    public byte[] sfx(String prompt, double duration, double influence, String model) {
        requireKey();
        return support.postBytes(base("/v1/sound-generation"), headers(),
                Map.of("text", prompt, "duration_seconds", duration,
                        "prompt_influence", influence, "model_id", model), Duration.ofSeconds(120));
    }

    public TtsResponse tts(String voice, String model, String text, String previous, String next) {
        requireKey();
        Map<String,Object> body = new LinkedHashMap<>(); body.put("text", text); body.put("model_id", model);
        if (!model.startsWith("eleven_v3")) {
            body.put("voice_settings", Map.of("stability", .5, "similarity_boost", .75,
                    "style", .3, "use_speaker_boost", true));
            if (previous != null && !previous.isBlank()) body.put("previous_text", previous);
            if (next != null && !next.isBlank()) body.put("next_text", next);
        }
        String url = base("/v1/text-to-speech/" + voice) + "/with-timestamps?output_format=mp3_44100_128";
        try {
            byte[] response = support.postBytes(url, headers(), body, Duration.ofSeconds(180));
            JsonNode node = json.readTree(response);
            if (!node.isObject() || !node.has("audio_base64")) return new TtsResponse(response, List.of());
            List<Map<String,Object>> words = new ArrayList<>();
            JsonNode alignment = node.path("alignment");
            JsonNode chars = alignment.path("characters"), starts = alignment.path("character_start_times_seconds");
            JsonNode ends = alignment.path("character_end_times_seconds");
            StringBuilder word = new StringBuilder(); double start = 0, end = 0;
            for (int i=0; i<chars.size(); i++) {
                String ch = chars.get(i).asText();
                if (ch.trim().isEmpty()) { if (word.length()>0) { words.add(word(start,end,word.toString())); word.setLength(0); } }
                else { if (word.length()==0) start=starts.get(i).asDouble(); word.append(ch); end=ends.get(i).asDouble(); }
            }
            if (word.length()>0) words.add(word(start,end,word.toString()));
            words.removeIf(w -> ((String) w.get("w")).startsWith("[") || ((String) w.get("w")).endsWith("]"));
            return new TtsResponse(Base64.getDecoder().decode(node.get("audio_base64").asText()), words);
        } catch (ApiException e) {
            if (e.status() != 400 && e.status() != 404 && e.status() != 422) throw e;
            byte[] audio = support.postBytes(base("/v1/text-to-speech/" + voice) + "?output_format=mp3_44100_128",
                    headers(), body, Duration.ofSeconds(180));
            return new TtsResponse(audio, List.of());
        } catch (Exception e) { throw new ApiException(502, "Invalid ElevenLabs TTS response", e); }
    }

    private Map<String,Object> word(double start, double end, String value) {
        return Map.of("w", value, "start", Math.round(start*1000)/1000.0, "end", Math.round(end*1000)/1000.0);
    }
    private Map<String,String> headers() { return Map.of("xi-api-key", requireKey(), "Accept", "audio/mpeg"); }
    private String requireKey() { if (properties.elevenLabsApiKey()==null || properties.elevenLabsApiKey().isBlank()) throw new ApiException(503, "ELEVENLABS_API_KEY is not configured"); return properties.elevenLabsApiKey(); }
    private String base(String suffix) { String base=properties.elevenLabsBaseUrl(); return (base==null?"https://api.elevenlabs.io":base).replaceAll("/$", "")+suffix; }
    public record TtsResponse(byte[] audio, List<Map<String,Object>> words) {}
}