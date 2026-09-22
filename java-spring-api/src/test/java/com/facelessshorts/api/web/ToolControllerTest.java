package com.facelessshorts.api.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.facelessshorts.api.service.infrastructure.ProcessGateway;
import com.facelessshorts.api.service.ToolService;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ToolControllerTest {
    private ToolService tools;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        tools = mock(ToolService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ToolController(tools, mock(ProcessGateway.class))).build();
    }

    @Test
    void mapsAllChordOptionsAndPreservesServiceResponse() throws Exception {
        Path project = Path.of("D:/projects/demo");
        when(tools.project("demo")).thenReturn(project);
        when(tools.call(eq("gen_chords"), eq(project), eq(List.of("--force", "--dry-run", "--only", "piano-a-min")), eq("generate_chords")))
                .thenReturn(Map.of("ok", true, "result", Map.of("dry_run", true)));

        mvc.perform(post("/gen_chords")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"project_id":"demo","force":true,"only":"piano-a-min","dry_run":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.result.dry_run").value(true));

        verify(tools).call(eq("gen_chords"), eq(project), eq(List.of("--force", "--dry-run", "--only", "piano-a-min")), eq("generate_chords"));
    }

    @Test
    void omitsOnlyWhenItIsAbsentOrBlank() throws Exception {
        Path project = Path.of("D:/projects/demo");
        when(tools.project("demo")).thenReturn(project);
        when(tools.call(eq("gen_chords"), eq(project), eq(List.of()), eq("generate_chords")))
                .thenReturn(Map.of("ok", true));

        mvc.perform(post("/gen_chords")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"project_id\":\"demo\",\"only\":\"  \"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"ok\":true")));

        verify(tools).call(eq("gen_chords"), eq(project), eq(List.of()), eq("generate_chords"));
    }

    @Test
    void rejectsMissingProjectId() throws Exception {
        mvc.perform(post("/gen_chords")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"force\":true}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(tools);
    }
}