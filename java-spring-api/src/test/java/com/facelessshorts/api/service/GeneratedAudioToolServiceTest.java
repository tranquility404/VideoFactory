package com.facelessshorts.api.service;
import com.facelessshorts.api.service.tools.*;
import com.facelessshorts.api.service.infrastructure.*;
import com.facelessshorts.api.service.integration.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.facelessshorts.api.config.ApiProperties;
import com.facelessshorts.api.error.ApiException;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeneratedAudioToolServiceTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path temp;

    @Test
    void sfxSupportsDryRunCacheAndForceWithoutCallingUpstream() throws Exception {
        Path dir = Files.createDirectories(temp.resolve("sfx/clips"));
        Files.writeString(temp.resolve("sfx/palette.json"), "{\"defaults\":{},\"sounds\":[{\"id\":\"hit\",\"prompt\":\"soft hit\",\"duration_s\":1}]}\n");
        Files.write(dir.resolve("hit.mp3"), new byte[] {1});
        ElevenLabsHttpClient api = mock(ElevenLabsHttpClient.class);
        GenSfxService service = new GenSfxService(support(api), api);

        Map<String, Object> cached = service.run(temp, List.of(), "generate_sfx");
        assertEquals(List.of("hit"), ((Map<?, ?>) cached.get("result")).get("skipped"));
        service.run(temp, List.of("--dry-run", "--force"), "generate_sfx");
        verifyNoInteractions(api);
        when(api.sfx(anyString(), anyDouble(), anyDouble(), anyString())).thenReturn(new byte[] {2, 3});
        service.run(temp, List.of("--force"), "generate_sfx");
        verify(api).sfx("soft hit", 1.0, .45, "eleven_text_to_sound_v2");
        assertArrayEquals(new byte[] {2, 3}, Files.readAllBytes(dir.resolve("hit.mp3")));
    }

    @Test
    void sfxReportsMissingCredentialsBeforeUpstreamCall() throws Exception {
        Files.createDirectories(temp.resolve("sfx"));
        Files.writeString(temp.resolve("sfx/palette.json"), "{\"sounds\":[{\"id\":\"hit\",\"prompt\":\"x\"}]}\n");
        ApiProperties p = properties("");
        ToolSupport support = new ToolSupport(p, mock(ProcessGateway.class), JSON);
        ElevenLabsHttpClient api = new ElevenLabsHttpClient(support, JSON, p);
        ApiException ex = assertThrows(ApiException.class, () -> new GenSfxService(support, api).run(temp, List.of(), "generate_sfx"));
        assertEquals(503, ex.status());
        assertTrue(ex.getMessage().contains("ELEVENLABS_API_KEY"));
    }

    private ToolSupport support(ElevenLabsHttpClient api) {
        return new ToolSupport(properties("test-key"), mock(ProcessGateway.class), JSON);
    }

    private ApiProperties properties(String key) {
        return new ApiProperties("python", 60, "node", "ffmpeg", "ffprobe", "", "render.mjs", key, "https://api.elevenlabs.io", "", "", "", "", "", "", "", "", "", "");
    }
}