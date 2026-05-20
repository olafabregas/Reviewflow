package com.reviewflow.infrastructure.config;

import com.reviewflow.auth.interceptor.StepUpInterceptor;
import com.reviewflow.infrastructure.web.CourseContextInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

  private final StepUpInterceptor stepUpInterceptor;
  private final CourseContextInterceptor courseContextInterceptor;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(stepUpInterceptor);
    registry
        .addInterceptor(courseContextInterceptor)
        .addPathPatterns("/api/v1/courses/**", "/api/v1/assignments/**");
  }
}
