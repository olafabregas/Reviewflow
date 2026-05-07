package com.reviewflow.infrastructure.config;

import com.reviewflow.infrastructure.security.JwtKeyConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(JwtKeyConfigurationProperties.class)
public class JwtKeyPropertiesConfiguration {}
