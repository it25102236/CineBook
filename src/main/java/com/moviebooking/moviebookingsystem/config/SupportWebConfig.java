package com.moviebooking.moviebookingsystem.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SupportWebConfig implements WebMvcConfigurer {

    private final SupportAuthorizationInterceptor supportAuthorizationInterceptor;

    public SupportWebConfig(
            SupportAuthorizationInterceptor supportAuthorizationInterceptor) {

        this.supportAuthorizationInterceptor = supportAuthorizationInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        registry.addInterceptor(supportAuthorizationInterceptor)
                .addPathPatterns("/support/**")
                .addPathPatterns("/my-support-tickets", "/my-support-tickets/**");
    }
}
