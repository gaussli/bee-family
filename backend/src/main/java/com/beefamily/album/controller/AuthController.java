package com.beefamily.album.controller;

import com.beefamily.album.common.ApiResponse;
import com.beefamily.album.dto.auth.ChangePasswordReq;
import com.beefamily.album.dto.auth.LoginReq;
import com.beefamily.album.dto.auth.MeResp;
import com.beefamily.album.dto.auth.RegisterReq;
import com.beefamily.album.dto.auth.TokenResp;
import com.beefamily.album.dto.auth.UpdateProfileReq;
import com.beefamily.album.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证与账号接口。
 *
 * <p>controller 只做参数绑定与 DTO 包装：所有业务规则（首用户自建家庭、邀请码校验、密码比对、
 * token 失效水位线）都在 {@link AuthService}。</p>
 */
@RestController
@RequestMapping(value = "/api/auth", produces = MediaType.APPLICATION_JSON_VALUE)
public class AuthController {

    private final AuthService authService;

    /**
     * 构造控制器。
     *
     * @param authService 认证服务
     */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 注册（公开）。首个用户自动成为 owner 并创建家庭。
     *
     * @param req 请求
     * @return 登录态
     */
    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<TokenResp> register(@Valid @RequestBody RegisterReq req) {
        return ApiResponse.ok(authService.register(req));
    }

    /**
     * 登录（公开）。
     *
     * @param req 请求
     * @return 登录态
     */
    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<TokenResp> login(@Valid @RequestBody LoginReq req) {
        return ApiResponse.ok(authService.login(req));
    }

    /**
     * 登出：无服务端会话，只把当前 token 置为失效。
     *
     * @return 空响应
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        authService.logout();
        return ApiResponse.ok();
    }

    /**
     * 当前用户信息。
     *
     * @return 当前用户 + 家庭 + 宝宝
     */
    @GetMapping("/me")
    public ApiResponse<MeResp> me() {
        return ApiResponse.ok(authService.me());
    }

    /**
     * 更新个人资料。
     *
     * @param req 请求
     * @return 更新后的当前用户信息
     */
    @PatchMapping(value = "/me", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<MeResp> updateMe(@Valid @RequestBody UpdateProfileReq req) {
        return ApiResponse.ok(authService.updateProfile(req));
    }

    /**
     * 修改密码（成功后既有 token 失效，需要重新登录）。
     *
     * @param req 请求
     * @return 空响应
     */
    @PutMapping(value = "/password", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordReq req) {
        authService.changePassword(req);
        return ApiResponse.ok();
    }
}
