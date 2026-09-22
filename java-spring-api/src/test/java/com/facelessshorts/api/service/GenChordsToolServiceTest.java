package com.facelessshorts.api.service;
import com.facelessshorts.api.service.tools.*;
import com.facelessshorts.api.service.infrastructure.*;
import com.facelessshorts.api.service.integration.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.facelessshorts.api.config.ApiProperties;
import com.facelessshorts.api.error.ApiException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class GenChordsServiceTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir Path temp;

    @Test
    void createsDeterministicWavWithPythonCompatibleHeaderAndSamples() throws Exception {
        AtomicReference<byte[]> wavBytes = new AtomicReference<>();
        ProcessGateway gateway = mockGateway(wavBytes);
        GenChordsService service = service(gateway);

        service.run(temp, List.of("--only", "piano-a-min"), "generate_chords");
        byte[] first = wavBytes.get();

        Files.delete(temp.resolve("sfx/clips/piano-a-min.mp3"));
        service.run(temp, List.of("--only", "piano-a-min", "--force"), "generate_chords");
        byte[] second = wavBytes.get();

        assertArrayEquals(first, second);
        assertArrayEquals("RIFF".getBytes(), java.util.Arrays.copyOfRange(first, 0, 4));
        assertArrayEquals("WAVE".getBytes(), java.util.Arrays.copyOfRange(first, 8, 12));
        assertEquals(1, u16(first, 22));
        assertEquals(44_100, u32(first, 24));
        assertEquals(16, u16(first, 34));
        assertEquals((int) (44_100 * 2.6) * 2L, u32(first, 40));
        assertEquals((int) (44_100 * 2.6), (first.length - 44) / 2);
    }

    @Test
    void onlyDryRunDoesNotInvokeEncoderOrModifyCatalog() throws IOException {
        ProcessGateway gateway = mockGateway();
        GenChordsService service = service(gateway);

        Map<String, Object> response = service.run(temp, List.of("--only", "piano-f-maj", "--dry-run"), "generate_chords");

        assertEquals(0, result(response).path("generated").asInt());
        assertTrue(result(response).path("dry_run").asBoolean());
        assertTrue(result(response).path("stdout").asText().contains("F2 F3 A3 C4"));
        verify(gateway, never()).execute(any(), any(), anyLong());
        assertFalse(Files.exists(temp.resolve("sfx/catalog.json")));
    }

    @Test
    void cacheSkipsAndForceRegeneratesOnlySelectedClip() throws IOException {
        ProcessGateway gateway = mockGateway();
        GenChordsService service = service(gateway);
        Path mp3 = temp.resolve("sfx/clips/piano-c-maj.mp3");
        Files.createDirectories(mp3.getParent());
        Files.writeString(mp3, "cached");

        Map<String, Object> cached = service.run(temp, List.of("--only", "piano-c-maj"), "generate_chords");
        assertEquals(0, result(cached).path("generated").asInt());
        verify(gateway, never()).execute(any(), any(), anyLong());

        service.run(temp, List.of("--only", "piano-c-maj", "--force"), "generate_chords");
        verify(gateway, times(2)).execute(any(), any(), anyLong());
    }

    @Test
    void updatesSelectedCatalogEntryWithoutDroppingUnknownFields() throws IOException {
        ProcessGateway gateway = mockGateway();
        GenChordsService service = service(gateway);
        Path catalog = temp.resolve("sfx/catalog.json");
        Files.createDirectories(catalog.getParent());
        Files.writeString(catalog, """
                {"note":"keep me","clips":[{"id":"piano-c-maj","custom":true,"used_in":["short-9-chords"]}]}
                """);

        service.run(temp, List.of("--only", "piano-c-maj"), "generate_chords");

        JsonNode entry = JSON.readTree(catalog.toFile()).path("clips").get(0);
        assertTrue(entry.path("custom").asBoolean());
        assertEquals("keep me", JSON.readTree(catalog.toFile()).path("note").asText());
        assertEquals("clips/piano-c-maj.mp3", entry.path("file").asText());
        assertEquals("content", entry.path("function").asText());
        assertEquals(2.601, entry.path("duration_s").asDouble(), 0.0001);
        assertEquals("short-9-chords", entry.path("used_in").get(0).asText());
    }

    @Test
    void rejectsUnknownChord() {
        GenChordsService service = service(mockGateway());
        ApiException error = assertThrows(ApiException.class,
                () -> service.run(temp, List.of("--only", "not-a-chord"), "generate_chords"));
        assertEquals(422, error.status());
    }

    private GenChordsService service(ProcessGateway gateway) {
        ApiProperties properties = new ApiProperties("python", 60, "node", "ffmpeg", "ffprobe", "", "render.mjs", "", "", "", "", "", "", "", "", "", "", "", "");
        return new GenChordsService(new ProjectPathService(temp.toString()), gateway, properties, JSON);
    }

    private ProcessGateway mockGateway() { return mockGateway(new AtomicReference<>()); }

    private ProcessGateway mockGateway(AtomicReference<byte[]> wavBytes) {
        ProcessGateway gateway = mock(ProcessGateway.class);
        when(gateway.execute(any(), any(), anyLong())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked") List<String> command = invocation.getArgument(0);
            if (command.get(0).equals("ffprobe")) return Map.of("stdout", "2.601\n", "exit_code", 0);
            wavBytes.set(Files.readAllBytes(Path.of(command.get(command.indexOf("-i") + 1))));
            Path output = Path.of(command.get(command.size() - 1));
            Files.createDirectories(output.getParent());
            Files.writeString(output, "fake mp3");
            return Map.of("stdout", "", "exit_code", 0);
        });
        return gateway;
    }

    private static JsonNode result(Map<String, Object> response) { return JSON.valueToTree(response).path("result"); }
    private static int u16(byte[] bytes, int offset) { return ByteBuffer.wrap(bytes, offset, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xffff; }
    private static long u32(byte[] bytes, int offset) { return ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt() & 0xffffffffL; }
}