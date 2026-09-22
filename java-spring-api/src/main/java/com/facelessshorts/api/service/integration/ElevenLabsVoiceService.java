package com.facelessshorts.api.service.integration;
import com.facelessshorts.api.service.infrastructure.*;
import com.facelessshorts.api.service.tools.GenVoiceService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.facelessshorts.api.error.ApiException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Service;

/** Generates independently cached voice clips and assembles the real, untimed voice track. */
@Service
public class ElevenLabsVoiceService implements GenVoiceService {
    private final ToolSupport support; private final ElevenLabsHttpClient eleven; private final ProcessGateway processes;
    public ElevenLabsVoiceService(ToolSupport s, ElevenLabsHttpClient e, ProcessGateway p) { support=s; eleven=e; processes=p; }

    public Map<String,Object> run(Path project, List<String> args, String function) {
        String beatArg=value(args,"--beats",null); if (beatArg==null) throw new ApiException(422,"gen_voice requires beats");
        Path beatsPath=Path.of(beatArg).toAbsolutePath().normalize();
        String voice=value(args,"--voice",support.properties().defaultVoice()), model=value(args,"--model",support.properties().defaultVoiceModel());
        String mux=value(args,"--mux",null), emit=value(args,"--emit-ts",null); boolean dry=args.contains("--dry-run"), force=args.contains("--force");
        try {
            ObjectMapper json=support.json(); ObjectNode beats=(ObjectNode)json.readTree(Files.readString(beatsPath)); ArrayNode vo=(ArrayNode)beats.path("vo");
            if (!vo.isArray()) throw new ApiException(422,"beats must contain a vo array");
            Path dir=beatsPath.getParent().resolve("voice"); if (!dry) Files.createDirectories(dir);
            List<Clip> clips=new ArrayList<>(); double previousEnd=0, plannedTotal=beats.path("format").path("durationSec").asDouble(0);
            for (int i=0;i<vo.size();i++) {
                ObjectNode line=(ObjectNode)vo.get(i); double plannedStart=line.path("start").asDouble(0), plannedEnd=line.path("end").asDouble(plannedStart);
                double gap=i==0?0:Math.max(0,plannedStart-vo.get(i-1).path("end").asDouble(0)); double start=i==0?plannedStart:previousEnd+gap;
                String text=line.path("text").asText(), tts=line.path("tts").asText(text), hash=sha1(voice+"|"+model+"|"+tts).substring(0,8);
                Path raw=dir.resolve(String.format("line-%02d-%s.mp3",i,hash)), wordsPath=Path.of(raw+".words.json");
                if (dry) { double d=Math.max(0,plannedEnd-plannedStart); clips.add(new Clip(raw,start,d,List.of())); previousEnd=start+d; continue; }
                if (force || !Files.exists(raw)) {
                    String prev=i==0?null:vo.get(i-1).path("tts").asText(vo.get(i-1).path("text").asText()), next=i+1==vo.size()?null:vo.get(i+1).path("tts").asText(vo.get(i+1).path("text").asText());
                    ElevenLabsHttpClient.TtsResponse response=eleven.tts(voice,model,tts,prev,next); Files.write(raw,response.audio()); Files.writeString(wordsPath,json.writeValueAsString(response.words()));
                }
                double duration=duration(raw); List<Map<String,Object>> words=Files.exists(wordsPath)?json.readValue(Files.readString(wordsPath),List.class):List.of();
                line.put("start",round(start,2)); line.put("end",round(start+duration,2)); ArrayNode actual=json.createArrayNode();
                for (Map<String,Object> word:words) { ObjectNode w=json.createObjectNode(); w.put("w",String.valueOf(word.get("w"))); w.put("start",round(start+number(word.get("start")),3)); w.put("end",round(start+number(word.get("end")),3)); actual.add(w); } line.set("words",actual);
                clips.add(new Clip(raw,start,duration,words)); previousEnd=start+duration;
            }
            double total=round(clips.isEmpty()?plannedTotal:previousEnd,2); if (dry) return result(function,beatsPath,clips,total,true);
            beats.with("format").put("durationSec",total); beats.put("voiceStatus","elevenlabs:"+voice); Path wav=dir.resolve("voice.wav"); assemble(clips,wav,total);
            Files.writeString(beatsPath,json.writerWithDefaultPrettyPrinter().writeValueAsString(beats)+System.lineSeparator()); if (emit!=null) emitTs(vo,Path.of(emit).toAbsolutePath().normalize()); if (mux!=null) mux(Path.of(mux).toAbsolutePath().normalize(),wav);
            return result(function,beatsPath,clips,total,false);
        } catch (IOException e) { throw new ApiException(500,"Cannot generate voice track",e); }
    }

    private void assemble(List<Clip> clips,Path out,double total) { if (clips.isEmpty()) throw new ApiException(422,"beats contains no voice lines"); List<String> c=new ArrayList<>(List.of(support.properties().ffmpegCommand(),"-y","-v","error")); List<String> parts=new ArrayList<>(); for(int i=0;i<clips.size();i++){Clip x=clips.get(i);c.addAll(List.of("-i",x.audio.toString()));long ms=Math.round(x.start*1000);parts.add("["+i+":a]adelay="+ms+"|"+ms+"[a"+i+"]");} StringBuilder chain=new StringBuilder();for(int i=0;i<clips.size();i++)chain.append("[a").append(i).append("]"); String f=String.join(";",parts)+";"+chain+"amix=inputs="+clips.size()+":normalize=0,apad,atrim=0:"+total+",loudnorm=I=-16:TP=-1.5:LRA=11[out]"; c.addAll(List.of("-filter_complex",f,"-map","[out]","-ar","44100","-ac","2",out.toString())); processes.execute(c,out.getParent(),300); }
    private void mux(Path in,Path voice){Path out=in.resolveSibling(strip(in.getFileName().toString())+"-voiced.mp4");processes.execute(List.of(support.properties().ffmpegCommand(),"-y","-v","error","-i",in.toString(),"-i",voice.toString(),"-map","0:v","-map","1:a","-c:v","copy","-c:a","aac","-b:a","192k",out.toString()),in.getParent(),300);}
    private double duration(Path p){try{Map<String,Object> r=processes.execute(List.of(support.properties().ffprobeCommand(),"-v","error","-show_entries","format=duration","-of","default=noprint_wrappers=1:nokey=1",p.toString()),p.getParent(),60);double d=Double.parseDouble(String.valueOf(r.getOrDefault("stdout","")).trim());if(d>0)return d;}catch(RuntimeException ignored){}try{byte[]b=Files.readAllBytes(p);if(b.length>=44&&new String(b,0,4,StandardCharsets.US_ASCII).equals("RIFF")){int rate=ByteBuffer.wrap(b,24,4).order(ByteOrder.LITTLE_ENDIAN).getInt(),ch=ByteBuffer.wrap(b,22,2).order(ByteOrder.LITTLE_ENDIAN).getShort(),bits=ByteBuffer.wrap(b,34,2).order(ByteOrder.LITTLE_ENDIAN).getShort(),data=ByteBuffer.wrap(b,40,4).order(ByteOrder.LITTLE_ENDIAN).getInt();return data/(double)(rate*ch*bits/8);}}catch(IOException ignored){}throw new ApiException(500,"Unable to determine duration for "+p);}
    private void emitTs(ArrayNode vo,Path p)throws IOException{if(p.getParent()!=null)Files.createDirectories(p.getParent());StringBuilder s=new StringBuilder("// AUTO-GENERATED by gen_voice.py â€” do not edit.\nimport type { VoLine } from '../../lib/shorts';\n\nexport const VO: VoLine[] = [\n");for(JsonNode n:vo){s.append("  { text: '").append(esc(n.path("text").asText())).append("', start: ").append(n.path("start").asDouble()).append(", end: ").append(n.path("end").asDouble()).append(", words: [");for(JsonNode w:n.path("words"))s.append("{ w: '").append(esc(w.path("w").asText())).append("', start: ").append(w.path("start").asDouble()).append(", end: ").append(w.path("end").asDouble()).append(" }, ");s.append("] },\n");}Files.writeString(p,s.append("];\n").toString());}
    private static String esc(String s){return s.replace("\\","\\\\").replace("'","\\'");} private static String strip(String s){int i=s.lastIndexOf('.');return i<0?s:s.substring(0,i);} private static double number(Object x){return x instanceof Number n?n.doubleValue():Double.parseDouble(String.valueOf(x));} private static double round(double x,int n){double p=Math.pow(10,n);return Math.round(x*p)/p;} private static String sha1(String x){try{byte[]d=MessageDigest.getInstance("SHA-1").digest(x.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte b:d)s.append(String.format("%02x",b));return s.toString();}catch(Exception e){throw new IllegalStateException(e);}} private static String value(List<String>a,String k,String d){int i=a.indexOf(k);return i>=0&&i+1<a.size()?a.get(i+1):d;} private static Map<String,Object> result(String f,Path b,List<Clip> c,double d,boolean dry){return Map.of("tool","gen_voice","function",f,"beats",b.toString(),"lines",c.size(),"durationSec",d,"dryRun",dry);} private record Clip(Path audio,double start,double duration,List<Map<String,Object>> words){}
}