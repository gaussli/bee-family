package com.beefamily.album.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：生产期 SPA 静态资源映射。
 *
 * <p>开发期前端由 Vite Dev Server 提供（5180/5173 + proxy），生产期 {@code frontend/dist} 会被复制到
 * {@code backend/src/main/resources/static}，因此这里只需映射 classpath 下的静态目录。
 * 业务接口 {@code /api/**} 由 {@code @RestController} 优先匹配，不受此映射影响。</p>
 *
 * <p>上传体积上限走 {@code spring.servlet.multipart.*}（见 application.yml），不在这里重复配置。</p>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private static final String STATIC_LOCATION = "classpath:/static/";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/", "/index.html", "/favicon.ico", "/assets/**")
                .addResourceLocations(STATIC_LOCATION);
    }
}
