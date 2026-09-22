package com.facelessshorts.api.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.resolve.ResourceCodeResolver;
import org.springframework.context.annotation.Bean;

@Configuration
@EnableConfigurationProperties({ApiProperties.class, AgentProperties.class})
public class ApplicationConfig {
    @Bean
    TemplateEngine templateEngine() {
        return TemplateEngine.create(new ResourceCodeResolver("templates"), ContentType.Html);
    }
}