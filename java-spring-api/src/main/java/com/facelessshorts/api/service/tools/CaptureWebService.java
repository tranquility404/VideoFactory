package com.facelessshorts.api.service.tools;
import com.facelessshorts.api.service.ToolService;
import com.facelessshorts.api.service.infrastructure.*;
import com.facelessshorts.api.service.integration.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.*;
import java.io.IOException;
import java.nio.file.*;
import java.net.*;

import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/** Playwright state capture with persistent profiles and optional VS Code serve-web. */
@Service
public class CaptureWebService {
    private final ProjectPathService paths; private final ObjectMapper mapper; private final RestClient restClient;
    public CaptureWebService(ProjectPathService paths, ToolSupport support){this(paths, support, support.restClient());}
    @Autowired
    public CaptureWebService(ProjectPathService paths, ToolSupport support, RestClient restClient){this.paths=paths;this.mapper=support.json();this.restClient=restClient;}
    public Map<String,Object> run(Path project,List<String> args,String function){
        String specArg=value(args,"--spec");if(specArg==null)throw new IllegalArgumentException("capture_web requires --spec");
        try{Map<String,Object> spec=mapper.readValue(Files.readString(resolve(specArg)),new TypeReference<>(){});String outName=(String)spec.get("out_dir");if(outName==null)throw new IllegalArgumentException("spec requires out_dir");Path out=resolve(outName);List<?> steps=(List<?>)spec.getOrDefault("steps",List.of());Map<String,Object> result=new LinkedHashMap<>();result.put("implementation","java-playwright");result.put("headed",args.contains("--headed"));result.put("out_dir",out.toString());result.put("steps",steps.size());
            if(args.contains("--dry-run"))return ToolService.envelope("capture_web",function,result);Files.createDirectories(out);Map<String,Object> server=serveWeb(spec);List<Map<String,Object>> shots=new ArrayList<>();List<?> vp=(List<?>)spec.getOrDefault("viewport",List.of(1920,1080));
            try(Playwright pw=Playwright.create()){BrowserContext context;String profile=(String)spec.get("browser_profile");Browser browser=null;if(profile!=null&&!profile.isBlank()){Path p=resolve(profile);Files.createDirectories(p);context=pw.chromium().launchPersistentContext(p,new BrowserType.LaunchPersistentContextOptions().setHeadless(!args.contains("--headed")).setViewportSize(((Number)vp.get(0)).intValue(),((Number)vp.get(1)).intValue()));}else{browser=pw.chromium().launch(new BrowserType.LaunchOptions().setHeadless(!args.contains("--headed")));context=browser.newContext(new Browser.NewContextOptions().setViewportSize(((Number)vp.get(0)).intValue(),((Number)vp.get(1)).intValue()));}Page page=context.pages().isEmpty()?context.newPage():context.pages().get(0);String base=(String)spec.getOrDefault("base_url","");if(!server.isEmpty()&&Boolean.TRUE.equals(server.get("goto_workbench"))){page.navigate((String)server.get("url"));}
                for(Object raw:steps){Map<String,Object>s=(Map<String,Object>)raw;if(s.containsKey("goto")){String u=(String)s.get("goto");page.navigate(u.startsWith("/")&&!base.isBlank()?base+u:u);}else if(s.containsKey("wait"))page.waitForTimeout(((Number)s.get("wait")).intValue());else if(s.containsKey("wait_for"))page.waitForSelector((String)s.get("wait_for"));else if(s.containsKey("click"))page.click((String)s.get("click"));else if(s.containsKey("fill")){List<?>v=(List<?>)s.get("fill");page.fill((String)v.get(0),(String)v.get(1));}else if(s.containsKey("press"))page.keyboard().press((String)s.get("press"));else if(s.containsKey("scroll"))page.evaluate("window.scrollTo(0,"+s.get("scroll")+")");else if(s.containsKey("eval"))page.evaluate((String)s.get("eval"));else if(s.containsKey("shot")){String n=(String)s.get("shot");Path f=out.resolve(n+".png");page.screenshot(new Page.ScreenshotOptions().setPath(f));shots.add(Map.of("name",n,"file",f.getFileName().toString(),"url",page.url()));}}context.close();if(browser!=null)browser.close();}
            Path manifest=out.resolve("manifest.json");Map<String,Object> manifestBody=new LinkedHashMap<>();manifestBody.put("viewport",vp);manifestBody.put("shots",shots);manifestBody.put("serve_web",server);Files.writeString(manifest,mapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifestBody));result.put("shots",shots.size());result.put("manifest",manifest.toString());return ToolService.envelope("capture_web",function,result);
        }catch(Exception e){throw new IllegalStateException("capture_web failed: "+e.getMessage(),e);}
    }
    private Map<String,Object> serveWeb(Map<String,Object> spec)throws Exception{Object raw=spec.get("serve_web");if(!(raw instanceof Map<?,?>))return Map.of();Map<String,Object> cfg=new LinkedHashMap<>();((Map<?,?>)raw).forEach((k,v)->cfg.put(String.valueOf(k),v));String folder=String.valueOf(cfg.get("folder")),port=String.valueOf(cfg.getOrDefault("port",9400));Path data=resolve(String.valueOf(cfg.getOrDefault("server_data_dir",".vscode-server")));Files.createDirectories(data.resolve("data/User"));Map<String,Object>settings=new LinkedHashMap<>();settings.put("workbench.startupEditor","none");settings.put("security.workspace.trust.enabled",false);settings.put("workbench.tips.enabled",false);if(cfg.get("settings") instanceof Map<?,?>m)m.forEach((k,v)->settings.put(String.valueOf(k),v));mapper.writeValue(data.resolve("data/User/settings.json").toFile(),settings);List<String>cmd=new ArrayList<>(List.of("code","serve-web","--port",port,"--without-connection-token","--accept-server-license-terms","--server-data-dir",data.toString()));new ProcessBuilder(cmd).redirectErrorStream(true).start();String url="http://127.0.0.1:"+port;long end=System.currentTimeMillis()+90_000;while(System.currentTimeMillis()<end){try{java.net.HttpURLConnection c=(java.net.HttpURLConnection)URI.create(url).toURL().openConnection();c.setConnectTimeout(1000);c.setReadTimeout(1000);if(c.getResponseCode()<500){String workbench=url+"/?folder="+URLEncoder.encode(resolve(folder).toString(),java.nio.charset.StandardCharsets.UTF_8);return Map.of("url",workbench,"folder",folder,"goto_workbench",cfg.getOrDefault("goto_workbench",true));}}catch(IOException e){Thread.sleep(500);}}throw new IllegalStateException("VS Code serve-web did not start");}
    private Path resolve(String p){Path q=Path.of(p);return q.isAbsolute()?q.normalize():paths.root().resolve(q).normalize();}private String value(List<String>a,String k){int i=a.indexOf(k);return i>=0&&i+1<a.size()?a.get(i+1):null;}
}