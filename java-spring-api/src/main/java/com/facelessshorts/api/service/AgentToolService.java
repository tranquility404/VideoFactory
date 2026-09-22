package com.facelessshorts.api.service;

import java.nio.file.Path;
import java.util.*;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

/** Spring AI tool facade over the repository's existing deterministic tool dispatcher. */
// @Service
public class AgentToolService {
    private final ToolService tools;
    public AgentToolService(ToolService tools) { this.tools = tools; }

    @Tool(description = "Run one repository video-production tool. tool is one of capture_web, bakeoff_clip, cutout, gen_image, gen_clip, gen_voice, mix_music, mix_sfx, gen_chords, gen_music, gen_sfx. Arguments are the tool's named options without leading dashes. Do not run destructive mixing until the user approves the plan.")
    public Map<String, Object> runVideoTool(String tool, String projectId, Map<String, Object> arguments) {
        if (tool == null || projectId == null) throw new IllegalArgumentException("tool and projectId are required");
        List<String> args = new ArrayList<>();
        if (arguments != null) arguments.forEach((key, value) -> {
            if (value != null) { args.add("--" + key.replace('_', '-')); args.add(String.valueOf(value)); }
        });
        Path project = tools.project(projectId);
        return tools.call(tool, project, args, "agent");
    }

    @Tool(description = "Return the list of Spring agent video-production tools and their usage contract.")
    public List<String> listVideoTools() {
        return List.of("capture_web", "bakeoff_clip", "cutout", "gen_image", "gen_clip", "gen_voice", "mix_music", "mix_sfx", "gen_chords", "gen_music", "gen_sfx");
    }
}