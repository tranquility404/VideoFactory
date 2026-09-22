package com.facelessshorts.api.service.tools;
import com.facelessshorts.api.service.ToolService;
import com.facelessshorts.api.service.infrastructure.*;
import com.facelessshorts.api.service.integration.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
@Service public final class MixMusicService { private final FfmpegMixService mixer; public MixMusicService(FfmpegMixService mixer){this.mixer=mixer;} public Map<String,Object> run(Path project,List<String> args,String function){return mixer.run("mix_music",project,args,function);} }