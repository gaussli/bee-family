package com.beefamily.album.security;

import com.beefamily.album.common.ErrorCode;
import com.beefamily.album.domain.FamilyMember;
import com.beefamily.album.domain.User;
import com.beefamily.album.repository.FamilyMemberRepository;
import com.beefamily.album.repository.UserRepository;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT 认证过滤器：Bearer 头（全部 /api/**）或 {@code ?token=}（仅 /api/files/**）。
 *
 * <p>{@code ?token=} 的唯一理由是 {@code <img src>} 无法设置请求头（ADR-04）；限制路径前缀可以
 * 把「token 出现在 URL」的暴露面收敛到图片读取，其他接口仍然只认 Header。</p>
 *
 * <p>失败处理：解析失败**不抛异常**，只把错误码放进 request attribute 继续走链，
 * 由 {@code AuthenticationEntryPoint} 统一输出 401 —— 这样 401 响应同样经过统一响应体与 traceId。</p>
 *
 * <p>R-3 落地：{@code iat < user.token_valid_after} 视为已失效（登出 / 改密码 / 被移除成员），
 * 返回 40103，弥补无状态 JWT 无法主动失效的缺口。</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 认证失败时写入 request 的错误码，供 EntryPoint 读取。 */
    public static final String AUTH_ERROR_ATTRIBUTE = "bee.authErrorCode";

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String FILES_PATH_PREFIX = "/api/files/";
    private static final String TOKEN_PARAM = "token";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final FamilyMemberRepository familyMemberRepository;

    /**
     * 构造过滤器。
     *
     * @param jwtService             JWT 服务
     * @param userRepository         用户仓储
     * @param familyMemberRepository 成员仓储（解析 familyId / role）
     */
    public JwtAuthenticationFilter(JwtService jwtService,
                                   UserRepository userRepository,
                                   FamilyMemberRepository familyMemberRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.familyMemberRepository = familyMemberRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        if (token == null || token.isBlank()) {
            chain.doFilter(request, response);
            return;
        }
        try {
            JwtService.ParsedToken parsed = jwtService.parse(token);
            User user = userRepository.findById(parsed.userId()).orElse(null);
            if (user == null) {
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, ErrorCode.UNAUTHORIZED);
                chain.doFilter(request, response);
                return;
            }
            if (isTokenRevoked(parsed.issuedAt(), user.getTokenValidAfter())) {
                log.debug("token revoked by token_valid_after userId={}", user.getId());
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, ErrorCode.TOKEN_EXPIRED);
                chain.doFilter(request, response);
                return;
            }
            FamilyMember member = familyMemberRepository.findByUserId(user.getId()).orElse(null);
            UserPrincipal principal = new UserPrincipal(user.getId(), user.getEmail(), user.getNickname(),
                    member == null ? null : member.getFamilyId(),
                    member == null ? null : member.getRole());
            String authority = "ROLE_" + (member == null ? "USER" : member.getRole().name().toUpperCase());
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, List.of(new SimpleGrantedAuthority(authority)));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (ExpiredJwtException ex) {
            request.setAttribute(AUTH_ERROR_ATTRIBUTE, ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException ex) {
            // 签名不符 / 结构畸形 / sub 非数字：统一 40100，不给出可探测的差异信息
            log.debug("invalid token: {}", ex.getClass().getSimpleName());
            request.setAttribute(AUTH_ERROR_ATTRIBUTE, ErrorCode.UNAUTHORIZED);
        }
        chain.doFilter(request, response);
    }

    /**
     * 判断 token 是否被主动失效。
     *
     * <p>使用严格小于：登出与改密码发生在 token 签发之后的秒级时刻才判失效，
     * 同一秒内签发的 token 不会被误伤（服务端时间精度为秒）。</p>
     *
     * @param issuedAt  token 签发时间
     * @param validAfter 失效水位线
     * @return true 表示已失效
     */
    private boolean isTokenRevoked(LocalDateTime issuedAt, LocalDateTime validAfter) {
        return issuedAt != null && validAfter != null && issuedAt.isBefore(validAfter);
    }

    /**
     * 提取凭证：优先 Header，其次（仅文件读取路径）query 参数。
     *
     * @param request 请求
     * @return 原始 token，未提供时为 null
     */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER_AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            if (!token.isEmpty()) {
                return token;
            }
        }
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith(FILES_PATH_PREFIX)) {
            String queryToken = request.getParameter(TOKEN_PARAM);
            if (queryToken != null && !queryToken.isBlank()) {
                return queryToken.trim();
            }
        }
        return null;
    }
}
