package com.facelessshorts.api.web;

import com.facelessshorts.api.service.*;
import com.facelessshorts.api.service.infrastructure.ProcessGateway;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.nio.file.Path;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class ToolController {
    private final ToolService tools; private final ProcessGateway processes;
    public ToolController(ToolService tools, ProcessGateway processes) { this.tools = tools; this.processes = processes; }
    @GetMapping("/health") public Map<String,String> health() { return Map.of("status", "ok"); }

    public record GenVoice(@NotBlank String project_id, @NotBlank String beats, String voice, String model, String mux, boolean force, boolean dry_run) {}
    public record GenImage(@NotBlank String project_id, @NotBlank String prompt, @NotBlank String out, String model, String aspect, String size, boolean force, boolean dry_run) {}
    public record GenClip(@NotBlank String project_id, @NotBlank String prompt, @NotBlank String out, String model, @Positive Double duration, String resolution, String ref, String image_url, boolean dry_run) {}
    public record Cutout(@NotBlank String project_id, @NotBlank String inp, @NotBlank String out, String method, Double tol, Double soft, Double feather, Integer border, Integer pad, Boolean trim) {}
    public record CaptureWeb(@NotBlank String project_id, @NotBlank String spec, boolean headed, boolean keep_server) {}
    public record Music(@NotBlank String project_id, boolean force, boolean dry_run, String only) {}
    public record Sfx(@NotBlank String project_id, boolean force, boolean dry_run, String only, boolean renorm) {}
    public record Mix(@NotBlank String project_id, @NotBlank String base, String plan, @NotBlank String out, String bed, Double gain_db) {}
    public record Chords(@NotBlank String project_id, boolean force, String only, boolean dry_run) {}
    public record Bakeoff(@NotBlank String project_id, @NotBlank String prompt, @NotBlank String out, String ref, String image_url, @Positive Double duration, String resolution, List<String> models, boolean dry_run) {}
    public record Remotion(@NotBlank String project_id, @NotBlank String composition_id, @NotBlank String out, @Positive Double scale, @Positive Integer timeout) {}
    public record Frames(@NotBlank String project_id, @NotBlank String composition_id, @NotEmpty List<Integer> frames, String out_dir, @Positive Double scale, @Positive Integer timeout) {}

    private List<String> a(String... values) { return Arrays.stream(values).filter(Objects::nonNull).toList(); }
    private String v(Object value, String defaultValue) { return value == null ? defaultValue : String.valueOf(value); }
    private Path project(String id) { return tools.project(id); }
    private String f(Path p, String name) { return tools.file(p, name).toString(); }

    @PostMapping("/gen_voice") public Object voice(@Valid @RequestBody GenVoice r) { Path p=project(r.project_id()); return tools.call("gen_voice",p,a("--beats",f(p,r.beats()),"--voice",v(r.voice(),"TX3LPaxmHKxFdv7VOQHJ"),"--model",v(r.model(),"eleven_multilingual_v2"),"--mux",r.mux(),r.force()?"--force":null,r.dry_run()?"--dry-run":null),"generate_voice"); }
    @PostMapping("/gen_image") public Object image(@Valid @RequestBody GenImage r) { Path p=project(r.project_id()); return tools.call("gen_image",p,a("--prompt",r.prompt(),"--out",f(p,r.out()),"--model",v(r.model(),"klein"),"--aspect",v(r.aspect(),"9:16"),"--size",v(r.size(),"2K"),r.force()?"--force":null,r.dry_run()?"--dry-run":null),"generate_image_file"); }
    @PostMapping("/gen_clip") public Object clip(@Valid @RequestBody GenClip r) { Path p=project(r.project_id()); return tools.call("gen_clip",p,a("--prompt",r.prompt(),"--out",f(p,r.out()),"--model",v(r.model(),"fal-ai/veo3.1/fast"),"--duration",v(r.duration(),"5"),"--resolution",v(r.resolution(),"1080p"),"--ref",r.ref()==null?null:f(p,r.ref()),"--image-url",r.image_url(),r.dry_run()?"--dry-run":null),"generate_clip"); }
    @PostMapping("/cutout") public Object cutout(@Valid @RequestBody Cutout r) { Path p=project(r.project_id()); return tools.call("cutout",p,a(f(p,r.inp()),f(p,r.out()),"--method",v(r.method(),"auto"),"--tol",v(r.tol(),"34"),"--soft",v(r.soft(),"26"),"--feather",v(r.feather(),"1"),"--border",v(r.border(),"0"),"--pad",v(r.pad(),"16"),Boolean.FALSE.equals(r.trim())?"--no-trim":null),"make_cutout"); }
    @PostMapping("/capture_web") public Object web(@Valid @RequestBody CaptureWeb r) { Path p=project(r.project_id()); return tools.call("capture_web",p,a("--spec",f(p,r.spec()),r.headed()?"--headed":null,r.keep_server()?"--keep-server":null),"capture"); }
    @PostMapping("/gen_music") public Object music(@Valid @RequestBody Music r) { Path p=project(r.project_id()); return tools.call("gen_music",p,a(r.force()?"--force":null,r.dry_run()?"--dry-run":null,r.only()==null?null:"--only",r.only()),"generate_music"); }
    @PostMapping("/gen_sfx") public Object sfx(@Valid @RequestBody Sfx r) { Path p=project(r.project_id()); return tools.call("gen_sfx",p,a(r.force()?"--force":null,r.dry_run()?"--dry-run":null,r.only()==null?null:"--only",r.only(),r.renorm()?"--renorm":null),"generate_sfx"); }
    @PostMapping("/mix_music") public Object mixMusic(@Valid @RequestBody Mix r) { Path p=project(r.project_id()); return tools.call("mix_music",p,a("--base",f(p,r.base()),r.plan()==null?null:"--plan",r.plan()==null?null:f(p,r.plan()),"--bed",v(r.bed(),"default"),"--out",f(p,r.out()),"--bed-gain",v(r.gain_db(),"-7")),"mix_music"); }
    @PostMapping("/mix_sfx") public Object mixSfx(@Valid @RequestBody Mix r) { Path p=project(r.project_id()); if(r.plan()==null) throw new com.facelessshorts.api.error.ApiException(422,"mix_sfx requires plan"); return tools.call("mix_sfx",p,a("--base",f(p,r.base()),"--plan",f(p,r.plan()),"--out",f(p,r.out()),"--gain-db",v(r.gain_db(),"0")),"mix_sfx"); }
    @PostMapping("/gen_chords") public Object chords(@Valid @RequestBody Chords r) { Path p=project(r.project_id()); List<String> args=new ArrayList<>(a(r.force()?"--force":null,r.dry_run()?"--dry-run":null)); if(r.only()!=null&&!r.only().isBlank()) { args.add("--only"); args.add(r.only()); } return tools.call("gen_chords",p,args,"generate_chords"); }
    @PostMapping("/bakeoff_clip") public Object bakeoff(@Valid @RequestBody Bakeoff r) { Path p=project(r.project_id()); List<String> args = new ArrayList<>(a("--prompt",r.prompt(),"--out",f(p,r.out()),"--ref",r.ref()==null?null:f(p,r.ref()),"--image-url",r.image_url(),"--duration",v(r.duration(),"5"),"--resolution",v(r.resolution(),"1080p"),r.models()==null||r.models().isEmpty()?null:"--models",r.models()==null||r.models().isEmpty()?null:String.join(",",r.models()),r.dry_run()?"--dry-run":null)); return tools.call("bakeoff_clip",p,args,"run_bakeoff"); }

    @PostMapping("/render/remotion") public Object remotion(@Valid @RequestBody Remotion r) {
        Path p=project(r.project_id()); String output=f(p,r.out());
        return tools.callNode("video",r.composition_id(),output,null,r.scale()==null?1:r.scale(),r.timeout()==null?86400:r.timeout(),"render_remotion");
    }
    @PostMapping("/render/remotion/frames") public Object remotionFrames(@Valid @RequestBody Frames r) {
        Path p=project(r.project_id()); String output=f(p,r.out_dir()==null?"frames":r.out_dir());
        if (new HashSet<>(r.frames()).size()!=r.frames().size()) throw new com.facelessshorts.api.error.ApiException(422,"frames must not contain duplicates");
        return tools.callNode("frames",r.composition_id(),output,r.frames(),r.scale()==null?1:r.scale(),r.timeout()==null?86400:r.timeout(),"render_remotion_frames");
    }
}