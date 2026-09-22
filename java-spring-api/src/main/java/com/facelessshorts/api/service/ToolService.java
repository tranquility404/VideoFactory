package com.facelessshorts.api.service;
import com.facelessshorts.api.service.tools.*;
import com.facelessshorts.api.service.infrastructure.*;

import com.facelessshorts.api.config.ApiProperties;
import com.facelessshorts.api.error.ApiException;
import java.nio.file.Path;
import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ToolService {
    private final ProjectPathService paths; private final ProcessGateway gateway;
    private final ToolSupport support; private final ApiProperties properties; private final GenChordsService chords;
    private final GenMusicService music; private final GenSfxService sfx; private final GenVoiceService voice;
    private final MixMusicService mixMusic; private final MixSfxService mixSfx;
    private final CaptureWebService captureWeb; private final BakeoffClipService bakeoffClip;
    private final CutoutService cutout; private final GenImageService genImage; private final GenClipService genClip;
    
    @Autowired
    public ToolService(ProjectPathService paths, ProcessGateway gateway, ToolSupport support, ApiProperties properties,
            GenChordsService chords, GenMusicService music, GenSfxService sfx, GenVoiceService voice,
            MixMusicService mixMusic, MixSfxService mixSfx, CaptureWebService captureWeb,
            BakeoffClipService bakeoffClip, CutoutService cutout, GenImageService genImage, GenClipService genClip) {
        this.paths = paths; this.gateway = gateway; this.support = support; this.properties = properties;
        this.chords = chords; this.music = music; this.sfx = sfx; this.voice = voice;
        this.mixMusic = mixMusic; this.mixSfx = mixSfx; this.captureWeb = captureWeb; this.bakeoffClip = bakeoffClip;
        this.cutout = cutout; this.genImage = genImage; this.genClip = genClip;
    }
    /** Compatibility constructor for focused unit tests; Spring uses the complete constructor above. */
    public ToolService(ProjectPathService paths, ProcessGateway gateway, ToolSupport support, ApiProperties properties,
            GenChordsService chords, GenMusicService music, GenSfxService sfx, GenVoiceService voice,
            MixMusicService mixMusic, MixSfxService mixSfx) {
        this(paths, gateway, support, properties, chords, music, sfx, voice, mixMusic, mixSfx,
                new CaptureWebService(paths, support), new BakeoffClipService(paths, support),
                new CutoutService(paths, support), new GenImageService(paths, support), new GenClipService(paths, support));
    }
    public Map<String,Object> call(String script, Path project, List<String> args, String function) {
        return switch (script) {
           case "capture_web" -> captureWeb.run(project, args, function);
           case "bakeoff_clip" -> bakeoffClip.run(project, args, function);
            case "cutout" -> cutout.run(project, args, function);
            case "gen_image" -> genImage.run(project, args, function);
            case "gen_clip" -> genClip.run(project, args, function);
            case "gen_voice" -> voice.run(project, args, function);
            case "mix_music" -> mixMusic.run(project, args, function);
            case "mix_sfx" -> mixSfx.run(project, args, function);
            case "gen_chords" -> chords.run(project, args, function);
            case "gen_music" -> music.run(project, args, function);
            case "gen_sfx" -> sfx.run(project, args, function);
            default -> throw new ApiException(404, "Unknown tool: " + script);
        };
    }
    public static Map<String,Object> envelope(String tool, String function, Object data) {
        Map<String,Object> out = new LinkedHashMap<>(); out.put("tool", tool); out.put("function", function); out.put("result", data); return out;
    }
    public Map<String,Object> callNode(String mode, String id, String output, List<Integer> frames, double scale, long timeout, String function) {
        List<String> command = new ArrayList<>(); command.add(properties.nodeCommand()); command.add(paths.root().resolve(properties.remotionScript()).toString());
        command.add("--mode=" + mode); command.add("--id=" + id); command.add("--output=" + output); command.add("--scale=" + scale);
        if (frames != null) command.add("--frames=" + frames.stream().map(String::valueOf).reduce((a,b)->a+","+b).orElse(""));
        Map<String,Object> result = new LinkedHashMap<>(); result.put("tool", "remotion"); result.put("function", function); result.put("result", gateway.execute(command, paths.root().resolve("remotion"), timeout)); return result;
    }
    public String path(Path p) { return p.toString(); }
    public Path project(String id) { return paths.project(id); }
    public Path file(Path p, String name) { return paths.file(p, name); }
}