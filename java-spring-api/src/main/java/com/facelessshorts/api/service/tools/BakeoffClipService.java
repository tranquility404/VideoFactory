package com.facelessshorts.api.service.tools;
import com.facelessshorts.api.service.ToolService;
import com.facelessshorts.api.service.infrastructure.*;
import com.facelessshorts.api.service.integration.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.springframework.stereotype.Service;

/** Java FAL queue bakeoff runner. Models are deliberately audio-off. */
@Service
public final class BakeoffClipService {
    private final ProjectPathService paths; private final ToolSupport support;
    public BakeoffClipService(ProjectPathService paths, ToolSupport support){this.paths=paths;this.support=support;}
    public Map<String,Object> run(Path project,List<String> args,String function){
        String prompt=value(args,"--prompt"),out=value(args,"--out"),ref=value(args,"--ref");
        if(prompt==null||out==null||ref==null) throw new IllegalArgumentException("bakeoff_clip requires --prompt, --ref and --out");
        Path output=resolve(out), reference=resolve(ref); List<Map<String,Object>> models=new ArrayList<>();
        for(String raw:valueOr(args,"--models","seedance,kling,veo-lite").split(",")){String n=raw.trim();models.add(Map.of("model",n,"endpoint",endpoint(n),"audio",false));}
        Map<String,Object> result=new LinkedHashMap<>(); result.put("implementation","java-http");result.put("prompt",prompt);result.put("ref",reference.toString());result.put("out",output.toString());result.put("audio",false);result.put("models",models);
        if(args.contains("--dry-run")) return ToolService.envelope("bakeoff_clip",function,result);
        if(support==null||support.properties().falApiKey()==null||support.properties().falApiKey().isBlank()) throw new IllegalStateException("FAL_KEY is not configured");
        try { Files.createDirectories(output); String imageUrl=upload(reference); List<Map<String,Object>> runs=new ArrayList<>();
            for(Map<String,Object> model:models) runs.add(runModel((String)model.get("model"),(String)model.get("endpoint"),prompt,imageUrl,output));
            writeJson(output.resolve("manifest.json"),Map.of("prompt",prompt,"reference",reference.toString(),"audio",false,"models",runs)); makeGrid(output,runs);
            result.put("runs",runs);result.put("manifest",output.resolve("manifest.json").toString());result.put("grid",output.resolve("grid.mp4").toString()); return ToolService.envelope("bakeoff_clip",function,result);
        } catch(Exception e){throw new IllegalStateException("bakeoff failed: "+e.getMessage(),e);}
    }
    private String upload(Path file){Map<String,String> h=auth();h.put("Content-Type","application/octet-stream");Object u=find(support.postBytes(support.properties().falStorageUrl(),h,read(file),Duration.ofMinutes(2)),"url");if(u==null)throw new IllegalStateException("FAL upload returned no URL");return u.toString();}
    private Map<String,Object> runModel(String name,String endpoint,String prompt,String image,Path out){
        Map<String,Object> payload=new LinkedHashMap<>();payload.put("prompt",prompt);payload.put("image_url",image);payload.put("generate_audio",false);Map<String,Object> sub=null;Exception last=null;
        for(int attempt=1;attempt<=3;attempt++)try{sub=support.postJson(support.properties().falQueueUrl()+"/"+endpoint,auth(),payload,Duration.ofMinutes(2));break;}catch(Exception e){last=e;sleep(attempt*1000L);}if(sub==null)throw new IllegalStateException("submission failed",last);
        String id=String.valueOf(sub.get("request_id")),base=support.properties().falQueueUrl()+"/"+endpoint;String status=String.valueOf(sub.getOrDefault("status_url",base+"/requests/"+id+"/status")),response=String.valueOf(sub.getOrDefault("response_url",base+"/requests/"+id));long start=System.currentTimeMillis();
        while(true){Map<String,Object>s=support.getJson(status,auth(),Duration.ofMinutes(2));String state=String.valueOf(s.getOrDefault("status","?"));if("COMPLETED".equals(state)){String url=String.valueOf(findVideo(support.getJson(response,auth(),Duration.ofMinutes(2))));if("null".equals(url))throw new IllegalStateException("no video URL");Path f=out.resolve(name+".mp4");write(f,support.getBytes(url,Map.of(),Duration.ofMinutes(10)));Map<String,Object>r=new LinkedHashMap<>();r.put("model",name);r.put("request_id",id);r.put("file",f.toString());r.put("source_url",url);r.put("estimated_cost",estimate(name));writeJson(out.resolve(name+".json"),r);return r;}if(Set.of("FAILED","ERROR","CANCELLED").contains(state))throw new IllegalStateException("generation "+state);if((System.currentTimeMillis()-start)/1000>support.properties().defaultTimeoutSeconds())throw new IllegalStateException("generation timeout");sleep(5000);}
    }
    private void makeGrid(Path out,List<Map<String,Object>> runs){List<String>c=new ArrayList<>(List.of(support.properties().ffmpegCommand(),"-y"));for(Map<String,Object>r:runs){c.add("-i");c.add((String)r.get("file"));}if(runs.size()>1){c.add("-filter_complex");c.add("hstack=inputs="+runs.size());}c.add(out.resolve("grid.mp4").toString());support.processes().execute(c,out,support.properties().defaultTimeoutSeconds());}
    private double estimate(String n){return switch(n){case"seedance"->0.26;case"kling"->0.50;case"veo-lite"->0.25;default->0.50;};} private Object findVideo(Object v){if(v instanceof Map<?,?>m){for(String k:List.of("video_url","url"))if(m.get(k)!=null)return m.get(k);for(Object x:m.values()){Object f=findVideo(x);if(f!=null)return f;}}if(v instanceof List<?>l)for(Object x:l){Object f=findVideo(x);if(f!=null)return f;}return null;} private Object find(Object v,String k){if(v instanceof Map<?,?>m){if(m.get(k)!=null)return m.get(k);for(Object x:m.values()){Object f=find(x,k);if(f!=null)return f;}}return null;}
    private Map<String,String>auth(){return new HashMap<>(Map.of("Authorization","Key "+support.properties().falApiKey()));}private byte[]read(Path p){try{return Files.readAllBytes(p);}catch(Exception e){throw new IllegalStateException("cannot read "+p,e);}}private void write(Path p,byte[]b){try{Files.write(p,b);}catch(Exception e){throw new IllegalStateException("cannot write "+p,e);}}private void writeJson(Path p,Object o){try{new ObjectMapper().writeValue(p.toFile(),o);}catch(Exception e){throw new IllegalStateException(e);}}private void sleep(long n){try{Thread.sleep(n);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}
    private String endpoint(String n){return switch(n){case"seedance"->"fal-ai/bytedance/seedance/v1.5/pro/image-to-video";case"kling"->"fal-ai/kling-video/v2.5-turbo/pro/image-to-video";case"veo-lite"->"fal-ai/veo3.1/lite/image-to-video";default->n;};}private String value(List<String>a,String k){int i=a.indexOf(k);return i>=0&&i+1<a.size()?a.get(i+1):null;}private String valueOr(List<String>a,String k,String d){String v=value(a,k);return v==null?d:v;}private Path resolve(String p){return paths.root().resolve(p).normalize();}
}