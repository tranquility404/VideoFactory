package com.facelessshorts.api.service.tools;
import com.facelessshorts.api.service.ToolService;
import com.facelessshorts.api.service.infrastructure.*;
import com.facelessshorts.api.service.integration.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.facelessshorts.api.config.ApiProperties;
import com.facelessshorts.api.error.ApiException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.springframework.stereotype.Service;

/** Deterministic replacement for tools/gen_chords.py. */
@Service
public final class GenChordsService {
    private static final int SAMPLE_RATE = 44_100;
    private static final double DURATION = 2.6;
    private static final double[] HARMONIC_NUMBERS = {1, 2, 3, 4, 6};
    private static final double[] HARMONIC_AMPLITUDES = {1.00, .32, .14, .07, .03};
    private static final String SOURCE = "gen_chords.py (deterministic additive synth, equal temperament A4=440)";
    private static final List<Chord> CHORDS = List.of(
            new Chord("piano-c-maj", "C major triad (I) â€” warm electric-piano voicing", new Note[]{n("C", 3), n("C", 4), n("E", 4), n("G", 4)}),
            new Chord("piano-g-maj", "G major triad (V)", new Note[]{n("G", 2), n("G", 3), n("B", 3), n("D", 4)}),
            new Chord("piano-a-min", "A minor triad (vi)", new Note[]{n("A", 2), n("A", 3), n("C", 4), n("E", 4)}),
            new Chord("piano-f-maj", "F major triad (IV)", new Note[]{n("F", 2), n("F", 3), n("A", 3), n("C", 4)}));
    private static final Map<String, Integer> NOTE_BASE = Map.ofEntries(
            Map.entry("C", 0), Map.entry("C#", 1), Map.entry("D", 2), Map.entry("D#", 3),
            Map.entry("E", 4), Map.entry("F", 5), Map.entry("F#", 6), Map.entry("G", 7),
            Map.entry("G#", 8), Map.entry("A", 9), Map.entry("A#", 10), Map.entry("B", 11));

    private final ProjectPathService paths;
    private final ProcessGateway gateway;
    private final ApiProperties properties;
    private final ObjectMapper mapper;

    public GenChordsService(ProjectPathService paths, ProcessGateway gateway, ApiProperties properties, ObjectMapper mapper) {
        this.paths = paths; this.gateway = gateway; this.properties = properties; this.mapper = mapper;
    }

    public Map<String, Object> run(Path project, List<String> args, String function) {
        boolean force = args.contains("--force"), dryRun = args.contains("--dry-run");
        String only = valueAfter(args, "--only");
        List<Chord> todo = CHORDS.stream().filter(c -> only == null || c.id().equals(only)).toList();
        if (todo.isEmpty()) throw new ApiException(422, "no chord matches --only " + only);
        Path sfx = project.resolve("sfx").normalize();
        Path clips = sfx.resolve("clips").normalize();
        if (!clips.startsWith(sfx)) throw new ApiException(422, "invalid project path");
        try { Files.createDirectories(clips); } catch (IOException e) { throw new ApiException(500, "Cannot create SFX clips directory", e); }
        Path catalogPath = sfx.resolve("catalog.json");
        ObjectNode catalog = readCatalog(catalogPath);
        ArrayNode entries = catalog.withArray("clips");
        int made = 0;
        List<String> output = new ArrayList<>();
        for (Chord chord : todo) {
            Path mp3 = clips.resolve(chord.id() + ".mp3");
            if (Files.exists(mp3) && !force) { output.add("  = " + chord.id() + " exists, skipping"); continue; }
            if (dryRun) { output.add("  + " + chord.id() + " WOULD synth " + chord.noteNames()); continue; }
            try {
                output.add("  + " + chord.id() + " " + chord.pitches());
                Path wav = Files.createTempFile("gen-chords-", ".wav");
                try { writeWav(wav, synth(chord.notes())); encode(wav, mp3); }
                finally { Files.deleteIfExists(wav); }
                double duration = probeDuration(mp3);
                ObjectNode entry = findEntry(entries, chord.id());
                boolean existing = entry != null;
                if (!existing) entry = mapper.createObjectNode();
                entry.put("id", chord.id()).put("file", "clips/" + chord.id() + ".mp3").put("desc", chord.description())
                        .put("function", "content").put("source", SOURCE).put("duration_s", Math.round(duration * 1000.0) / 1000.0);
                if (!entry.has("used_in")) entry.putArray("used_in");
                if (!existing) entries.add(entry);
                made++;
            } catch (IOException e) { throw new ApiException(500, "Failed to generate " + chord.id(), e); }
        }
        if (!dryRun && made > 0) try { Files.createDirectories(sfx); mapper.writerWithDefaultPrettyPrinter().writeValue(catalogPath.toFile(), catalog); }
        catch (IOException e) { throw new ApiException(500, "Cannot update SFX catalog", e); }
        return ToolService.envelope("gen_chords", function, Map.of("stdout", String.join(System.lineSeparator(), output), "generated", made, "dry_run", dryRun));
    }

    private void encode(Path wav, Path mp3) { gateway.execute(List.of(properties.ffmpegCommand(), "-y", "-hide_banner", "-loglevel", "error", "-i", wav.toString(), "-af", "loudnorm=I=-20.0:TP=-1.5:LRA=11", "-ar", "44100", "-ac", "1", "-b:a", "192k", mp3.toString()), mp3.getParent(), properties.defaultTimeoutSeconds()); }
    private double probeDuration(Path mp3) { Map<String,Object> r = gateway.execute(List.of(properties.ffprobeCommand(), "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", mp3.toString()), mp3.getParent(), properties.defaultTimeoutSeconds()); return Double.parseDouble(String.valueOf(r.get("stdout")).trim()); }
    private ObjectNode readCatalog(Path path) { try { if (Files.exists(path)) return (ObjectNode) mapper.readTree(path.toFile()); ObjectNode catalog = mapper.createObjectNode(); catalog.putArray("clips"); return catalog; } catch (IOException e) { throw new ApiException(500, "Cannot read SFX catalog", e); } }
    private ObjectNode findEntry(ArrayNode entries, String id) { for (JsonNode n : entries) if (id.equals(n.path("id").asText())) return (ObjectNode)n; return null; }
    private static String valueAfter(List<String> args, String flag) { int i=args.indexOf(flag); return i >= 0 && i+1 < args.size() ? args.get(i+1) : null; }
    private static double midiHz(int midi) { return 440.0 * Math.pow(2.0, (midi - 69) / 12.0); }
    private static int midi(Note n) { return 12 * (n.octave() + 1) + NOTE_BASE.get(n.name()); }
    private static Note n(String name, int octave) { return new Note(name, octave); }
    private static double[] synth(Note[] notes) { int count=(int)(SAMPLE_RATE*DURATION); double[] b=new double[count]; for(int i=0;i<notes.length;i++){ double f0=midiHz(midi(notes[i])), amp=i==0?.62:1.0; int delay=(int)(SAMPLE_RATE*.006*i); double detune=1+.0006*(i-1.5); for(int j=0;j<HARMONIC_NUMBERS.length;j++){ double h=HARMONIC_NUMBERS[j], f=f0*h*detune; if(f>SAMPLE_RATE/2.2)continue; double w=2*Math.PI*f/SAMPLE_RATE, decay=2.6+.85*h, a=amp*HARMONIC_AMPLITUDES[j]; for(int s=delay;s<count;s++){ double t=(s-delay)/(double)SAMPLE_RATE, env=Math.exp(-decay*t); if(env<1e-4)break; b[s]+=a*Math.min(1,t/.009)*env*Math.sin(w*(s-delay)); }}} double peak=Arrays.stream(b).map(Math::abs).max().orElse(1); for(int i=0;i<b.length;i++)b[i]=b[i]/peak*.89; return b; }
    private static void writeWav(Path path, double[] samples) throws IOException { try(var out=Files.newOutputStream(path)){ int data=samples.length*2; out.write(new byte[]{'R','I','F','F'}); le(out,36+data,4); out.write(new byte[]{'W','A','V','E','f','m','t',' '}); le(out,16,4); le(out,1,2); le(out,1,2); le(out,SAMPLE_RATE,4); le(out,SAMPLE_RATE*2,4); le(out,2,2); le(out,16,2); out.write(new byte[]{'d','a','t','a'}); le(out,data,4); for(double v:samples) le(out,(short)(Math.max(-1,Math.min(1,v))*32767),2); } }
    private static void le(java.io.OutputStream o,int v,int bytes)throws IOException{for(int i=0;i<bytes;i++)o.write((v>>(8*i))&255);}
    private record Note(String name,int octave) { }
    private record Chord(String id,String description,Note[] notes) { String noteNames(){return Arrays.stream(notes).map(n->n.name()+n.octave()).reduce((a,b)->a+" "+b).orElse("");} String pitches(){return Arrays.stream(notes).map(n->n.name()+n.octave()+"("+String.format(Locale.ROOT,"%.1f",midiHz(midi(n)))+"Hz)").reduce((a,b)->a+" "+b).orElse("");} }
}