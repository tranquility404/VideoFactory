package com.facelessshorts.api.service.tools;
import com.facelessshorts.api.service.ToolService;
import com.facelessshorts.api.service.infrastructure.*;
import com.facelessshorts.api.service.integration.*;
import java.nio.file.Path; import java.time.Duration; import java.util.*;
import org.springframework.stereotype.Service;
/** fal.ai queue adapter; payload extension fields remain model-agnostic. */
@Service
public final class GenClipService {
 private final ProjectPathService paths; private final ToolSupport support;
 public GenClipService(ProjectPathService p,ToolSupport s){paths=p;support=s;}
 public Map<String,Object> run(Path project,List<String> a,String fn){String prompt=GenImageService.value(a,"--prompt",null),out=GenImageService.value(a,"--out",null);String model=GenImageService.value(a,"--model",support.properties().defaultClipModel()); if(prompt==null||out==null)throw new IllegalArgumentException("gen_clip requires prompt and out"); Map<String,Object> payload=new LinkedHashMap<>();payload.put("prompt",prompt);payload.put("duration",Double.parseDouble(GenImageService.value(a,"--duration","5")));payload.put("resolution",GenImageService.value(a,"--resolution","1080p"));String image=GenImageService.value(a,"--image-url",null);if(image!=null)payload.put("image_url",image); if(a.contains("--dry-run"))return ToolService.envelope("gen_clip",fn,Map.of("model",model,"payload",payload)); String key=support.properties().falApiKey();if(key.isBlank())throw new IllegalStateException("FAL_KEY is not configured");Map<String,String> h=Map.of("Authorization","Key "+key,"Content-Type","application/json");Map<String,Object> queued=support.postJson(support.properties().falQueueUrl()+"/"+model,h,payload,Duration.ofMinutes(2));return ToolService.envelope("gen_clip",fn,Map.of("model",model,"request",queued,"out",paths.projectFile(project,out).toString()));}
}