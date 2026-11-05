package com.beefamily.album.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 链路标识过滤器：为每个请求生成 traceId，写入 MDC 与响应头。
 *
 * <p>位置：注册在 Spring Security 过滤器链的**最前面**（先于 JwtAuthenticationFilter），
 * 以便 401/403 这类在过滤器阶段就返回的响应同样带上 traceId。</p>
 *
 * <p>脱敏：访问日志中的 query string 会把 {@code token=<jwt>} 替换为 {@code token=***}
 * （ADR-04 的后果之一：文件接口允许 {@code ?token=}，token 可能出现在 URL 中）。</p>
 */
public class TraceIdFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TraceIdFilter.class);

    /** 匹配 query string 中的 token 参数值，避免 JWT 落日志。 */
    private static final Pattern TOKEN_QUERY_PATTERN = Pattern.compile("token=[^&]*");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // 已被外层（例如安全链外层注册）写入时复用，避免同一请求出现两个不同 traceId
        String traceId = MDC.get(TraceIds.MDC_KEY);
        if (traceId == null || traceId.isBlank()) {
            traceId = TraceIds.generate();
            MDC.put(TraceIds.MDC_KEY, traceId);
        }
        response.setHeader(TraceIds.HEADER, traceId);

        long startedAt = System.nanoTime();
        try {
            if (log.isDebugEnabled()) {
                log.debug("{} {} query={}", request.getMethod(), request.getRequestURI(),
                        sanitize(request.getQueryString()));
            }
            chain.doFilter(request, response);
        } finally {
            if (log.isDebugEnabled()) {
                long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
                log.debug("{} {} -> {} ({} ms)", request.getMethod(), request.getRequestURI(),
                        response.getStatus(), elapsedMs);
            }
            MDC.remove(TraceIds.MDC_KEY);
        }
    }

    /**
     * 对 query string 中的 token 做脱敏。
     *
     * @param queryString 原始 query string，可为 null
     * @return 脱敏后的字符串
     */
    static String sanitize(String queryString) {
        if (queryString == null || queryString.isEmpty()) {
            return "";
        }
        return TOKEN_QUERY_PATTERN.matcher(queryString).replaceAll("token=***");
    }
}
