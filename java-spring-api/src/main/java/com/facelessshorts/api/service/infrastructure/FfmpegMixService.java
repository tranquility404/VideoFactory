package com.facelessshorts.api.service.infrastructure;
import com.facelessshorts.api.service.integration.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.facelessshorts.api.config.ApiProperties;
import com.facelessshorts.api.error.ApiException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.springframework.stereotype.Service;

/** Builds and runs the FFmpeg graphs used by mix_sfx.py and mix_music.py. */
@Service
public final class FfmpegMixService {
    private final ProjectPathService paths;
    private final ProcessGateway processes;
    private final ApiProperties props;
    private final ObjectMapper json;

    public FfmpegMixService(ProjectPathService paths, ProcessGateway processes, ApiProperties props, ObjectMapper json) {
        this.paths = paths; this.processes = processes; this.props = props; this.json = json;
    }

    public Map<String, Object> run(String tool, Path project, List<String> args, String function) {
        String base = option(args, "--base"), plan = option(args, "--plan"), out = option(args, "--out");
        if (base == null || out == null || (tool.equals("mix_sfx") && plan == null))
            throw new ApiException(422, tool + " requires --base, --plan and --out");
        Path basePath = resolve(project, base), outPath = resolve(project, out);
        requireFile(basePath, "base media");
        try { Files.createDirectories(outPath.getParent()); } catch (IOException e) { throw new ApiException(500, "Cannot create mixer output directory", e); }
        return tool.equals("mix_sfx") ? mixSfx(project, basePath, resolve(project, plan), outPath, args) : mixMusic(project, basePath, outPath, args);
    }

    private Map<String, Object> mixSfx(Path project, Path base, Path planPath, Path out, List<String> args) {
        JsonNode plan = read(planPath, "SFX plan");
        JsonNode catalog = read(resolveCatalog(project, plan, "sfx"), "SFX catalog");
        Map<String, JsonNode> clips = index(catalog, "clips", "id");
        List<Event> events = new ArrayList<>();
        double end = number(option(args, "--end"), plan.path("render").path("end_s").asDouble(60));
        boolean noOptional = args.contains("--no-optional"), duck = plan.path("render").path("duck").asBoolean(true);
        if (args.contains("--duck")) duck = true; if (args.contains("--no-duck")) duck = false;
        Set<String> missing = new TreeSet<>();
        for (JsonNode event : plan.path("events")) {
            if (noOptional && event.path("optional").asBoolean(false)) continue;
            if (event.path("at_s").asDouble() >= end) continue;
            JsonNode clip = clips.get(event.path("sfx_id").asText());
            if (clip == null) { missing.add(event.path("sfx_id").asText()); continue; }
            Path file = resolveLibrary(project, clip.path("file").asText(), "sfx");
            if (!Files.isRegularFile(file)) { missing.add(event.path("sfx_id").asText() + " (file)"); continue; }
            events.add(new Event(event, file));
        }
        if (!missing.isEmpty()) throw new ApiException(422, "missing SFX clips: " + String.join(", ", missing));
        if (events.isEmpty()) throw new ApiException(422, "no SFX events to mix");
        events.sort(Comparator.comparingDouble(e -> e.node.path("at_s").asDouble()));
        List<String> command = new ArrayList<>(List.of(props.ffmpegCommand(), "-y", "-hide_banner", "-i", base.toString()));
        for (Event event : events) command.addAll(List.of("-i", event.file.toString()));
        List<String> filters = new ArrayList<>(), labels = new ArrayList<>();
        for (int i = 0; i < events.size(); i++) {
            JsonNode e = events.get(i).node; long delay = Math.max(0, Math.round(e.path("at_s").asDouble() * 1000));
            String label = "e" + i; labels.add("[" + label + "]");
            filters.add("[" + (i + 1) + ":a]aformat=sample_rates=48000:channel_layouts=stereo,adelay=" + delay + ":all=1,volume=" + e.path("gain_db").asDouble(0) + "dB[" + label + "]");
        }
        filters.add(String.join("", labels) + "amix=inputs=" + labels.size() + ":normalize=0:dropout_transition=0[sfxraw]");
        if (duck) {
            filters.add("[0:a]aformat=sample_rates=48000:channel_layouts=stereo,asplit=2[v1][v2]");
            filters.add("[sfxraw][v1]sidechaincompress=threshold=0.15:ratio=2:attack=5:release=200:makeup=2[sfxduck]");
            filters.add("[v2][sfxduck]amix=inputs=2:normalize=0[mixraw]");
        } else {
            filters.add("[0:a]aformat=sample_rates=48000:channel_layouts=stereo[v2]");
            filters.add("[v2][sfxraw]amix=inputs=2:normalize=0[mixraw]");
        }
        filters.add("[mixraw]alimiter=level_in=1:level_out=1:limit=0.97[mix]");
        command.addAll(List.of("-filter_complex", String.join(";", filters), "-map", "0:v:0", "-map", "[mix]", "-c:v", "copy", "-c:a", "aac", "-b:a", "192k", "-t", String.format(Locale.ROOT, "%.3f", end), "-movflags", "+faststart", out.toString()));
        return execute(command, out, events.size(), end, "mix_sfx");
    }

    private Map<String, Object> mixMusic(Path project, Path base, Path out, List<String> args) {
        String bed = option(args, "--bed"); if (bed == null) throw new ApiException(422, "mix_music requires --bed");
        JsonNode catalog = read(resolve(project, "media/library/music/catalog.json"), "music catalog");
        JsonNode item = index(catalog, "beds", "id").get(bed); if (item == null) item = index(catalog, "clips", "id").get(bed);
        if (item == null) throw new ApiException(422, "music bed not found: " + bed);
        Path file = resolveLibrary(project, item.path("file").asText(), "music"); requireFile(file, "music bed");
        double gain = number(option(args, "--bed-gain"), -7), fade = number(option(args, "--fade"), 1.5);
        String filter = "[1:a]aloop=loop=-1:size=2e+09,volume=" + gain + "dB,highpass=f=80,afade=t=in:d=" + fade + "[bed];[0:a][bed]sidechaincompress=threshold=0.03:ratio=8:attack=20:release=500:makeup=1[ducked];[0:a][ducked]amix=inputs=2:normalize=0,alimiter=limit=0.97[mix]";
        List<String> command = new ArrayList<>(List.of(props.ffmpegCommand(), "-y", "-hide_banner", "-i", base.toString(), "-stream_loop", "-1", "-i", file.toString(), "-filter_complex", filter, "-map", "0:v:0", "-map", "[mix]", "-c:v", "copy", "-c:a", "aac", "-b:a", "192k", "-shortest", "-movflags", "+faststart", out.toString()));
        return execute(command, out, 1, null, "mix_music");
    }

    private Map<String, Object> execute(List<String> command, Path out, int cues, Double end, String tool) {
        Map<String, Object> result = processes.execute(command, out.getParent(), props.defaultTimeoutSeconds());
        if (result.containsKey("ok") && !Boolean.TRUE.equals(result.get("ok"))) throw new ApiException(502, tool + " FFmpeg failed: " + result.get("output"));
        requireFile(out, "mixer output");
        Map<String, Object> response = new LinkedHashMap<>(); response.put("ok", true); response.put("tool", tool); response.put("path", out.toString()); response.put("cues", cues); if (end != null) response.put("end_s", end); return response;
    }

    private record Event(JsonNode node, Path file) {}
    private JsonNode read(Path file, String label) { requireFile(file, label); try { return json.readTree(file.toFile()); } catch (IOException e) { throw new ApiException(422, "invalid " + label + " JSON", e); } }
    private Path resolveCatalog(Path project, JsonNode plan, String kind) {
        String value = plan.path("catalog").asText("media/library/" + kind + "/catalog.json");
        Path local = resolve(project, value);
        if (Files.isRegularFile(local)) return local;
        return resolveRoot(value);
    }
    private Path resolveLibrary(Path project, String file, String kind) {
        Path local = resolve(project, kind + "/" + file);
        if (Files.isRegularFile(local)) return local;
        return resolveRoot("media/library/" + kind + "/" + file);
    }
    private Path resolveRoot(String value) {
        Path p = paths.root().resolve(value).toAbsolutePath().normalize();
        if (!p.startsWith(paths.root().toAbsolutePath().normalize())) throw new ApiException(422, "path escapes repository root: " + value);
        return p;
    }
    private Path resolve(Path project, String value) { Path p = Path.of(value); if (!p.isAbsolute()) p = project.resolve(value); p = p.toAbsolutePath().normalize(); if (!p.startsWith(paths.root().toAbsolutePath().normalize())) throw new ApiException(422, "path escapes repository root: " + value); return p; }
    private void requireFile(Path file, String label) { if (!Files.isRegularFile(file)) throw new ApiException(422, "missing " + label + ": " + file); }
    private Map<String, JsonNode> index(JsonNode root, String array, String key) { Map<String, JsonNode> m = new HashMap<>(); for (JsonNode n : root.path(array)) m.put(n.path(key).asText(), n); return m; }
    private String option(List<String> args, String name) { int i = args.indexOf(name); return i >= 0 && i + 1 < args.size() ? args.get(i + 1) : null; }
    private double number(String value, double fallback) { try { return value == null ? fallback : Double.parseDouble(value); } catch (NumberFormatException e) { throw new ApiException(422, "invalid numeric option"); } }
}