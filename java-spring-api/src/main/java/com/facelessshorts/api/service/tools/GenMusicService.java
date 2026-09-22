package com.facelessshorts.api.service.tools;
import com.facelessshorts.api.service.ToolService;
import com.facelessshorts.api.service.infrastructure.*;
import com.facelessshorts.api.service.integration.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.facelessshorts.api.error.ApiException;
import java.io.*; import java.nio.file.*; import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class GenMusicService {
    private final ProjectPathService paths; private final ToolSupport support; private final ElevenLabsHttpClient eleven;
    public GenMusicService(ProjectPathService p, ToolSupport s, ElevenLabsHttpClient e){paths=p;support=s;eleven=e;}
    public Map<String,Object> run(Path project,List<String> args,String function){
        ObjectMapper j=support.json(); Path dir=project.resolve("music"), palette=dir.resolve("palette.json"), catalog=dir.resolve("catalog.json");
        try { JsonNode root=j.readTree(Files.readString(palette)); ObjectNode cat=Files.exists(catalog)?(ObjectNode)j.readTree(Files.readString(catalog)):j.createObjectNode(); ArrayNode clips=cat.withArray("clips");
            Map<String,ObjectNode> existing=new LinkedHashMap<>(); clips.forEach(n->existing.put(n.path("id").asText(),(ObjectNode)n)); Files.createDirectories(dir.resolve("clips"));
            Set<String> only=only(args); boolean force=args.contains("--force"), dry=args.contains("--dry-run"); List<String> generated=new ArrayList<>(), skipped=new ArrayList<>();
            JsonNode d=root.path("defaults"); String model=d.path("model").asText("music_v2"), format=d.path("output_format").asText("mp3_44100_128"); boolean instrumental=d.path("force_instrumental").asBoolean(true);
            for(JsonNode s:root.path("beds")){String id=s.path("id").asText(); if(only!=null&&!only.contains(id))continue; String rel="clips/"+id+".mp3"; Path out=dir.resolve(rel); if(Files.exists(out)&&!force){skipped.add(id);continue;} if(dry){generated.add(id);continue;}
                Files.write(out,eleven.music(s.path("prompt").asText(),Math.round(s.path("duration_s").asDouble(63)*1000),model,instrumental,format)); ObjectNode e=existing.getOrDefault(id,j.createObjectNode()); e.put("id",id);e.put("file",rel);e.put("category",s.path("category").asText(""));e.set("tags",s.path("tags"));e.put("prompt",s.path("prompt").asText());e.put("source","elevenlabs:music"); existing.put(id,e); generated.add(id);
            } clips.removeAll(); existing.values().forEach(clips::add); if(!dry)Files.writeString(catalog,j.writerWithDefaultPrettyPrinter().writeValueAsString(cat)+"\n"); return ToolService.envelope("gen_music",function,Map.of("generated",generated,"skipped",skipped,"dry_run",dry));
        } catch(IOException e){throw new ApiException(500,"Cannot process music palette",e);}
    }
    private Set<String> only(List<String>a){int i=a.indexOf("--only");return i<0||i+1>=a.size()?null:new HashSet<>(Arrays.asList(a.get(i+1).split(",")));}
}