package com.facelessshorts.api.service;
import com.facelessshorts.api.service.tools.*;
import com.facelessshorts.api.service.infrastructure.*;
import com.facelessshorts.api.service.integration.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.facelessshorts.api.config.ApiProperties;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolServiceDispatchTest {
    @Test
    void dispatchesImageAndClipToJavaAdaptersInDryRunMode() {
        ProjectPathService paths = mock(ProjectPathService.class);
        ToolSupport support = mock(ToolSupport.class);
        ApiProperties properties = mock(ApiProperties.class);
        when(properties.defaultClipModel()).thenReturn("test-model");
        when(support.properties()).thenReturn(properties);
        ToolService service = new ToolService(paths, mock(ProcessGateway.class), support, properties,
                mock(GenChordsService.class), mock(GenMusicService.class), mock(GenSfxService.class),
                mock(GenVoiceService.class), mock(MixMusicService.class), mock(MixSfxService.class));

        Map<String, Object> image = service.call("gen_image", Path.of("C:/project"),
                List.of("--prompt", "a red kite", "--out", "image.png", "--dry-run"), "image");
        Map<String, Object> clip = service.call("gen_clip", Path.of("C:/project"),
                List.of("--prompt", "a red kite", "--out", "clip.mp4", "--dry-run"), "clip");

        assertEquals("gen_image", image.get("tool"));
        assertEquals("gen_clip", clip.get("tool"));
    }
}