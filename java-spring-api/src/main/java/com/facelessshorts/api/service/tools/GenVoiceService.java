package com.facelessshorts.api.service.tools;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Application-facing contract for generating voice tracks. */
public interface GenVoiceService {
    Map<String, Object> run(Path project, List<String> args, String function);
}