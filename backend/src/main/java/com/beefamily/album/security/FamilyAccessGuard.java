package com.beefamily.album.security;

import com.beefamily.album.common.BusinessException;
import com.beefamily.album.common.ErrorCode;
import com.beefamily.album.domain.enums.Role;
import org.springframework.stereotype.Component;

/**
 * 家庭隔离闸门：所有角色类校验的唯一入口。
 *
 * <p>双层防线（§7.5）：</p>
 * <ol>
 *   <li>查询层：repository 方法一律带 {@code familyId}，跨家庭查不到 → service 抛 40301；</li>
 *   <li>本类：对「已查出数据」再做显式角色断言，覆盖重命名家庭 / 移除成员 / 删相册等动作。</li>
 * </ol>
 *
 * <p>为什么跨家庭返回 403 而不是 404：PRD §10 明确要求 ALL 数据查询强制携带 family_id 且越权返回 403，
 * 便于自查；对内自用场景下「清晰报错」优于「隐蔽」。</p>
 */
@Component
public class FamilyAccessGuard {

    /**
     * 当前主体（未认证抛 40100）。
     *
     * @return 主体
     */
    public UserPrincipal principal() {
        return SecurityUtils.requirePrincipal();
    }

    /**
     * 当前家庭 id（无家庭抛 40300）。
     *
     * @return 家庭 id
     */
    public Long familyId() {
        UserPrincipal principal = principal();
        if (principal.familyId() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前账号不属于任何家庭");
        }
        return principal.familyId();
    }

    /**
     * 断言资源属于当前家庭。
     *
     * @param resourceFamilyId 资源所属家庭 id
     */
    public void assertSameFamily(Long resourceFamilyId) {
        if (resourceFamilyId == null || !resourceFamilyId.equals(familyId())) {
            throw new BusinessException(ErrorCode.CROSS_FAMILY_ACCESS);
        }
    }

    /**
     * 断言当前用户是家庭创建者。
     */
    public void assertOwner() {
        if (principal().role() != Role.owner) {
            throw new BusinessException(ErrorCode.OWNER_ONLY);
        }
    }

    /**
     * 断言当前用户是资源创建者或家庭创建者。
     *
     * @param createdBy 资源创建者 user id
     */
    public void assertCreatorOrOwner(Long createdBy) {
        UserPrincipal principal = principal();
        if (principal.role() == Role.owner) {
            return;
        }
        if (createdBy != null && createdBy.equals(principal.userId())) {
            return;
        }
        throw new BusinessException(ErrorCode.NOT_RESOURCE_CREATOR);
    }

    /**
     * 断言当前用户是上传者 / 资源创建者 / 家庭创建者。
     *
     * <p>照片场景没有独立「创建者」，「上传者」即创建者；保留双参数是为了让调用方显式表达语义，
     * 避免以后出现「相册创建者与上传者不同」时误用。</p>
     *
     * @param uploadedBy 上传者 user id
     * @param createdBy  资源创建者 user id
     */
    public void assertUploaderOrCreatorOrOwner(Long uploadedBy, Long createdBy) {
        UserPrincipal principal = principal();
        if (principal.role() == Role.owner) {
            return;
        }
        Long userId = principal.userId();
        if (uploadedBy != null && uploadedBy.equals(userId)) {
            return;
        }
        if (createdBy != null && createdBy.equals(userId)) {
            return;
        }
        throw new BusinessException(ErrorCode.NOT_RESOURCE_CREATOR);
    }

    /**
     * 当前用户是否为 owner。
     *
     * @return true 表示 owner
     */
    public boolean isOwner() {
        return principal().role() == Role.owner;
    }
}
