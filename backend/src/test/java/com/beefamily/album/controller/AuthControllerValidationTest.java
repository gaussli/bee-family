package com.beefamily.album.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.beefamily.album.dto.auth.TokenResp;
import com.beefamily.album.security.JwtAuthenticationFilter;
import com.beefamily.album.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link AuthController} 参数校验测试（@WebMvcTest，不启动数据库）。
 *
 * <p>核心命题：注册接口的多字段校验错误必须**一次全部聚合**在一条 message 里
 * （{@code email / password / nickname} 三项同时可见），否则用户要改一项、提交一次、再报下一项。
 * 同时钉住 {@code 40001} 这个业务码，前端依赖它做字段级高亮。</p>
 *
 * <p>说明：{@code addFilters = false} 让本测试只验证「绑定 + 校验 + 异常出口」，
 * 鉴权链路（JWT/401）由端到端测试覆盖，避免在 web 层测试里重建整套安全上下文。</p>
 */
@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    @DisplayName("email / password / nickname 三项同时非法 → 400 + 40001，且 message 同时包含三个字段")
    void aggregatesAllFieldErrors() throws Exception {
        String body = """
                {"email":"not-an-email","password":"123","nickname":""}
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("email"),
                        org.hamcrest.Matchers.containsString("password"),
                        org.hamcrest.Matchers.containsString("nickname"))));

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("邮箱格式错误文案可读（不是默认英文约束消息）")
    void emailMessageIsLocalised() throws Exception {
        String body = """
                {"email":"not-an-email","password":"secret123","nickname":"yanyan"}
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("邮箱格式不正确")));
    }

    @Test
    @DisplayName("空请求体 {} → 400 + 40001（三个 NotBlank 全部报出）")
    void emptyBodyRejected() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("不能为空")));

        verifyNoInteractions(authService);
    }

    @Test
    @DisplayName("密码超长（33 位）→ 40001")
    void overlongPasswordRejected() throws Exception {
        String body = """
                {"email":"a@b.com","password":"%s","nickname":"yanyan"}
                """.formatted("x".repeat(33));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    @DisplayName("合法请求 → 200 + code 0，并转交 service")
    void validRequestPassesThrough() throws Exception {
        when(authService.register(any())).thenReturn(new TokenResp("jwt-token",
                java.time.LocalDateTime.of(2026, 12, 1, 0, 0),
                new com.beefamily.album.dto.auth.UserResp(1L, "a@b.com", "yanyan", null, null),
                10L, "许家小院", com.beefamily.album.domain.enums.Role.owner, java.util.List.of()));
        String body = """
                {"email":"a@b.com","password":"secret123","nickname":"yanyan","inviteCode":null}
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").value("jwt-token"));
    }

    @Test
    @DisplayName("请求体不是合法 JSON → 400 + 40002（参数取值非法，而非 500）")
    void malformedJsonIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40002));
    }
}
