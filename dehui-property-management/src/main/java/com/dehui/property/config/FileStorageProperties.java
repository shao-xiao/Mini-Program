package com.dehui.property.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dehui.file")
public record FileStorageProperties(String uploadDir) {
}
