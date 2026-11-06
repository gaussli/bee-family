package com.beefamily.album.controller;

import com.beefamily.album.common.ApiResponse;
import com.beefamily.album.dto.family.DeleteFamilyReq;
import com.beefamily.album.dto.family.FamilyResp;
import com.beefamily.album.dto.family.MemberResp;
import com.beefamily.album.dto.family.UpdateFamilyReq;
import com.beefamily.album.service.FamilyService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 家庭空间与成员接口。
 *
 * <p>权限（owner / member）在 service 层用 {@code FamilyAccessGuard} 判定，
 * controller 不做任何角色判断 —— UI 隐藏按钮不是安全边界（§7.5）。</p>
 */
@RestController
@RequestMapping(value = "/api/family", produces = MediaType.APPLICATION_JSON_VALUE)
public class FamilyController {

    private final FamilyService familyService;

    /**
     * 构造控制器。
     *
     * @param familyService 家庭服务
     */
    public FamilyController(FamilyService familyService) {
        this.familyService = familyService;
    }

    /**
     * 当前家庭信息。
     *
     * @return 家庭响应
     */
    @GetMapping
    public ApiResponse<FamilyResp> getFamily() {
        return ApiResponse.ok(familyService.getFamily());
    }

    /**
     * 修改家庭名（仅 owner）。
     *
     * @param req 请求
     * @return 家庭响应
     */
    @PatchMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<FamilyResp> updateFamily(@Valid @RequestBody UpdateFamilyReq req) {
        return ApiResponse.ok(familyService.updateFamily(req.name()));
    }

    /**
     * 删除家庭（仅 owner，需输入家庭名确认）。
     *
     * @param req 请求
     * @return 空响应
     */
    @DeleteMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Void> deleteFamily(@Valid @RequestBody DeleteFamilyReq req) {
        familyService.deleteFamily(req.confirmName());
        return ApiResponse.ok();
    }

    /**
     * 成员列表。
     *
     * @return 成员列表
     */
    @GetMapping("/members")
    public ApiResponse<List<MemberResp>> listMembers() {
        return ApiResponse.ok(familyService.listMembers());
    }

    /**
     * 移除成员（仅 owner）。
     *
     * @param userId 目标用户 id
     * @return 空响应
     */
    @DeleteMapping("/members/{userId}")
    public ApiResponse<Void> removeMember(@PathVariable Long userId) {
        familyService.removeMember(userId);
        return ApiResponse.ok();
    }
}
