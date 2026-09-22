package com.facelessshorts.api.service;

import com.facelessshorts.api.config.AgentProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

@Service
public class AgentSkillService {
    private final Map<String, String> skills;

    public AgentSkillService(AgentProperties properties) {
        Map<String, String> loaded = new TreeMap<>();
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources(properties.skillsLocation() + "*/SKILL.md");
            for (Resource resource : resources) {
                String name = resource.getURL().toString().replaceAll(".*/skills/([^/]+)/SKILL.md$", "$1");
                loaded.put(name, resource.getContentAsString(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load agent skills", e);
        }
        skills = Map.copyOf(loaded);
    }

    public List<String> names() { return skills.keySet().stream().toList(); }
    public String promptFor(String name) {
        if (name == null || name.isBlank()) return "No specialized skill selected.";
        String skill = skills.get(name);
        if (skill == null) throw new IllegalArgumentException("Unknown skill: " + name);
        return skill;
    }
}