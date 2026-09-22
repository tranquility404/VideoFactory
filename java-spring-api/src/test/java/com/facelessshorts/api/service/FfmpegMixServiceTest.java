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

class FfmpegMixServiceTest {
    @TempDir Path temp;

    @Test
    void validatesRequiredInputsAndTraversal() throws Exception {
        ProcessGateway gateway = mock(ProcessGateway.class);
        FfmpegMixService service = service(gateway);
        Files.write(temp.resolve("base.mp4"), new byte[] {1});
        ApiException missing = assertThrows(ApiException.class, () -> service.run("mix_sfx", temp, List.of("--base", "base.mp4", "--out", "x.mp4"), "mix_sfx"));
        assertEquals(422, missing.status());
        ApiException traversal = assertThrows(ApiException.class, () -> service.run("mix_music", temp, List.of("--base", "../base.mp4", "--bed", "x", "--out", "x.mp4"), "mix_music"));
        assertEquals(422, traversal.status());
        verifyNoInteractions(gateway);
    }

    @Test
    void buildsRepresentativeSfxGraphAndReturnsMetadata() throws Exception {
        ProcessGateway gateway = mock(ProcessGateway.class);
        when(gateway.execute(anyList(), any(), anyLong())).thenAnswer(call -> {
            @SuppressWarnings("unchecked") List<String> command = (List<String>) call.getArgument(0);
            Files.write(Path.of(command.get(command.size() - 1)), new byte[] {1});
            return Map.of("stdout", "ok", "exit_code", 0);
        });
        Files.write(temp.resolve("base.mp4"), new byte[] {1});
        Files.createDirectories(temp.resolve("media/library/sfx/clips"));
        Files.write(temp.resolve("media/library/sfx/clips/hit.mp3"), new byte[] {1});
        Files.writeString(temp.resolve("plan.json"), "{\"render\":{\"end_s\":2},\"events\":[{\"sfx_id\":\"hit\",\"at_s\":0.25,\"gain_db\":-3}]}\n");
        Files.writeString(temp.resolve("media/library/sfx/catalog.json"), "{\"clips\":[{\"id\":\"hit\",\"file\":\"clips/hit.mp3\"}]}\n");
        Map<String, Object> result = service(gateway).run("mix_sfx", temp, List.of("--base", "base.mp4", "--plan", "plan.json", "--out", "out.mp4"), "mix_sfx");
        @SuppressWarnings("unchecked") List<String> command = (List<String>) invocation(gateway).getArgument(0);
        assertTrue(command.contains("-filter_complex"));
        assertTrue(command.stream().anyMatch(s -> s.contains("adelay=250")));
        assertEquals("mix_sfx", result.get("tool"));
        assertEquals(2.0, result.get("end_s"));
    }

    private FfmpegMixService service(ProcessGateway gateway) {
        ApiProperties p = new ApiProperties("python", 60, "node", "ffmpeg", "ffprobe", "", "", "", "", "", "", "", "", "", "", "", "", "", "");
        return new FfmpegMixService(new ProjectPathService(temp.toString()), gateway, p, new ObjectMapper());
    }

    private org.mockito.invocation.InvocationOnMock invocation(ProcessGateway gateway) {
        return mockingDetails(gateway).getInvocations().stream().findFirst().orElseThrow();
    }
}