package com.beefamily.album.config;

import com.beefamily.album.common.ApiResponse;
import com.beefamily.album.common.ErrorCode;
import com.beefamily.album.security.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Spring Security 配置：无状态 JWT、统一 401/403 出口、CORS 白名单、安全响应头。
 *
 * <p>放行清单与 docs §7.3 逐行一致：只有注册 / 登录 / 错误转发 / 健康检查 / 预检 / 生产期静态资源是公开的，
 * 其余 {@code /api/**}（含 {@code /api/files/**}）一律要求认证。</p>
 *
 * <p>CSRF 关闭：无 Cookie 会话、token 走 Authorization 头，CSRF 攻击面不成立；同时
 * {@code allowCredentials=false}，避免与通配来源冲突（§7.4）。</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;
    private final List<String> allowedOrigins;

    /**
     * 构造配置。
     *
     * @param jwtAuthenticationFilter JWT 过滤器
     * @param objectMapper            JSON 序列化器（错误体输出）
     * @param allowedOrigins          允许的跨域来源（逗号分隔）
     */
    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          ObjectMapper objectMapper,
                          @Value("${app.cors.allowed-origins:http://localhost:5173}") String allowedOrigins) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }

    /**
     * 过滤器链。
     *
     * @param http HttpSecurity
     * @return 安全过滤器链
     * @throws Exception 构建异常
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; img-src 'self' data: blob:; style-src 'self' 'unsafe-inline'"))
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers("/error", "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/", "/index.html", "/favicon.ico", "/assets/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, ex) -> {
                            ErrorCode errorCode = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE)
                                    instanceof ErrorCode code ? code : ErrorCode.UNAUTHORIZED;
                            writeError(response, errorCode);
                        })
                        .accessDeniedHandler((request, response, ex) -> writeError(response, ErrorCode.FORBIDDEN)))
                // traceId 过滤器由容器级注册（Ordered.HIGHEST_PRECEDENCE）保证先于本链执行
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 密码哈希：bcrypt cost=10（PRD §10 明确要求 ≥10）。
     *
     * @return 编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    /**
     * CORS 白名单（仅开发期生效；生产期同源单端口部署时无跨域请求）。
     *
     * @return CORS 配置源
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Trace-Id"));
        configuration.setExposedHeaders(List.of("X-Trace-Id"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * 输出统一错误体。
     *
     * @param response  HTTP 响应
     * @param errorCode 错误码
     * @throws IOException 写响应失败
     */
    private void writeError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getHttpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.fail(errorCode));
    }
}
