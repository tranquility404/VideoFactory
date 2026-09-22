package com.facelessshorts.api.service;
import com.facelessshorts.api.service.tools.*;
import com.facelessshorts.api.service.infrastructure.*;
import com.facelessshorts.api.service.integration.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.facelessshorts.api.config.ApiProperties;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class Stage5ToolServiceTest {
    @Test void captureDryRunParsesSpecWithoutPython() throws Exception {
        ProjectPathService p=mock(ProjectPathService.class); when(p.root()).thenReturn(Path.of("C:/repo"));
        Path s=Files.createTempFile("capture-", ".json"); Files.writeString(s,"{\"out_dir\":\"captures\",\"steps\":[{\"shot\":\"one\"}]}");
        ToolSupport support=mock(ToolSupport.class); when(support.json()).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper());
        Map<String,Object> r=new CaptureWebService(p,support).run(Path.of("C:/repo"),List.of("--spec",s.toString(),"--dry-run"),"capture");
        assertEquals("java-playwright",((Map<?,?>)r.get("result")).get("implementation"));
    }
    @Test void bakeoffDryRunUsesJavaRegistry() {
        ProjectPathService p=mock(ProjectPathService.class); when(p.root()).thenReturn(Path.of("C:/repo"));
        Map<String,Object> r=new BakeoffClipService(p,mock(ToolSupport.class)).run(Path.of("C:/repo"),List.of("--prompt","walk","--ref","still.png","--out","out","--models","seedance,kling","--dry-run"),"bakeoff");
        Map<?,?> b=(Map<?,?>)r.get("result"); assertEquals("java-http",b.get("implementation")); assertEquals(false,b.get("audio")); assertEquals(2,((List<?>)b.get("models")).size());
    }
}