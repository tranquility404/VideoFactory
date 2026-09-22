package com.facelessshorts.api.service.tools;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.facelessshorts.api.config.ApiProperties;
import com.facelessshorts.api.service.ToolService;
import com.facelessshorts.api.service.infrastructure.ProjectPathService;
import com.facelessshorts.api.service.infrastructure.ToolSupport;

/** Java implementation of gen_image.py's Cloudflare worker integration. */
@Service
public final class GenImageService {
    private final ProjectPathService paths;
    private final ToolSupport support;

    public GenImageService(ProjectPathService paths, ToolSupport support) {
        this.paths = paths;
        this.support = support;
    }

    public Map<String, Object> run(Path project, List<String> args, String function) {
        String prompt = value(args, "--prompt", null), out = value(args, "--out", null),
                model = value(args, "--model", "klein");

        String aspect = value(args, "--aspect", "9:16"), size = value(args, "--size", "2K");

        if (prompt == null || out == null)
            throw new IllegalArgumentException("gen_image requires prompt and out");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("prompt", prompt);
        result.put("model", model);
        result.put("dimensions", dimensions(aspect, size));

        if (args.contains("--dry-run"))
            return ToolService.envelope("gen_image", function, result);

        ApiProperties p = support.properties();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");

        if (!p.cloudflareApiKey().isBlank())
            headers.put("Authorization", "Bearer " + p.cloudflareApiKey());
        
        byte[] data = support.postBytes(p.cloudflareApiUrl(), headers, Map.of("prompt", prompt, "model", model),
                Duration.ofMinutes(10));

        Path target = paths.projectFile(project, out);
        support.write(target, data);
        result.put("out", target.toString());
        
        return ToolService.envelope("gen_image", function, result);
    }

    static String value(List<String> args, String key, String def) {
        int i = args.indexOf(key);
        return i >= 0 && i + 1 < args.size() ? args.get(i + 1) : def;
    }

    static String dimensions(String aspect, String size) {
        try {
            String[] r = aspect.split(":");
            int w = Integer.parseInt(r[0]), h = Integer.parseInt(r[1]);
            int target = switch (size.toUpperCase()) {
                case "1K" -> 1024;
                case "4K" -> 4096;
                default -> 2048;
            };
            double scale = Math.min(Math.min((double) target / w, (double) target / h), 2048.0 / Math.max(w, h));
            return (Math.max(64, (int) (w * scale) / 64 * 64)) + "x" + (Math.max(64, (int) (h * scale) / 64 * 64));
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid aspect or size");
        }
    }
}