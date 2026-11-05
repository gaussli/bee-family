package com.beefamily.album.common;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 链路标识过滤器的注册点。
 *
 * <p><b>为什么注册为容器级过滤器并置于最高优先级</b>：Spring Security 的过滤器链以 order
 * {@code -100} 注册；只有让 traceId 过滤器排在它之前，401/403 这类「在安全链内就返回」的响应
 * 才能带上同一个 traceId。若把本过滤器塞进安全链内部，则必须依赖安全链的过滤器顺序注册表，
 * 反而更脆弱。</p>
 */
@Configuration
public class TraceIdFilterConfig {

    /** 过滤器名称（用于 once-per-request 去重标记与日志定位）。 */
    private static final String FILTER_NAME = "traceIdFilter";

    /**
     * 注册 traceId 过滤器。
     *
     * @return 注册描述
     */
    @Bean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistration() {
        FilterRegistrationBean<TraceIdFilter> registration = new FilterRegistrationBean<>(new TraceIdFilter());
        registration.setName(FILTER_NAME);
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
