package com.beefamily.album.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.beefamily.album.controller.FamilyController;
import com.beefamily.album.dto.auth.RegisterReq;
import com.beefamily.album.mapper.FamilyMapper;
import com.beefamily.album.repository.FamilyRepository;
import com.beefamily.album.service.FamilyService;
import com.beefamily.album.support.Limits;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Q7「旧邀请码无过渡移除」的自动守卫（docs/07 §4.6 步骤 6、§5.3.5 删除清单）。
 *
 * <p>用反射与常量检查把「残留即视为未完成」变成可执行的断言，防止后续任务把
 * 旧邀请码符号、端点、常量悄悄带回主干。T01 只清理基础设施侧，不实现 T02/T03 新业务。</p>
 */
class Q7CleanupTest {

    @Test
    @DisplayName("RegisterReq 恰好 3 个字段（email/password/nickname），无 inviteCode")
    void registerReqHasNoInviteCode() {
        String[] components = Arrays.stream(RegisterReq.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).toArray(String[]::new);
        assertThat(components).containsExactly("email", "password", "nickname");
    }

    @ParameterizedTest(name = "FamilyRepository 不得声明：{0}")
    @ValueSource(strings = {"findByInviteCode", "existsByInviteCode"})
    @DisplayName("FamilyRepository 的旧邀请码派生查询已删除")
    void familyRepositoryHasNoInviteQuery(String methodName) {
        assertThat(Arrays.stream(FamilyRepository.class.getDeclaredMethods()).map(Method::getName))
                .doesNotContain(methodName);
    }

    @Test
    @DisplayName("FamilyMapper 不再声明 toInviteCodeResp")
    void familyMapperHasNoInviteCodeMapping() {
        assertThat(Arrays.stream(FamilyMapper.class.getDeclaredMethods()).map(Method::getName))
                .doesNotContain("toInviteCodeResp");
    }

    @ParameterizedTest(name = "FamilyService 不得声明：{0}")
    @ValueSource(strings = {"getInviteCode", "resetInviteCode", "applyNewInviteCode"})
    @DisplayName("FamilyService 的旧邀请码业务方法已整体移除")
    void familyServiceHasNoInviteCodeBusiness(String methodName) {
        assertThat(Arrays.stream(FamilyService.class.getDeclaredMethods()).map(Method::getName))
                .doesNotContain(methodName);
    }

    @ParameterizedTest(name = "Limits 不得声明常量：{0}")
    @ValueSource(strings = {"INVITE_CODE_LENGTH", "INVITE_CODE_TTL", "HEIC_HINT"})
    @DisplayName("Limits 的旧邀请码 / HEIC 专属文案常量已删除")
    void limitsHasNoRetiredConstants(String fieldName) {
        assertThat(Arrays.stream(Limits.class.getDeclaredFields()).map(Field::getName))
                .doesNotContain(fieldName);
    }

    @Test
    @DisplayName("FamilyController 不再暴露 /invite-code 或 /invite-code/reset 端点")
    void familyControllerHasNoInviteCodeEndpoints() {
        for (Method method : FamilyController.class.getDeclaredMethods()) {
            GetMapping get = method.getAnnotation(GetMapping.class);
            PostMapping post = method.getAnnotation(PostMapping.class);
            String[] paths = get != null ? get.value() : post != null ? post.value() : new String[0];
            for (String path : paths) {
                assertThat(path).doesNotContain("invite-code");
            }
        }
    }

    @ParameterizedTest(name = "已删除的源文件不得留在仓库：{0}")
    @ValueSource(strings = {
            "src/main/java/com/beefamily/album/dto/family/InviteCodeResp.java",
            "src/main/java/com/beefamily/album/support/InviteCodes.java",
            "src/test/java/com/beefamily/album/support/InviteCodesTest.java"
    })
    @DisplayName("InviteCodeResp / InviteCodes / InviteCodesTest 源文件不再存在（无双份基线）")
    void retiredSourceFilesAreGone(String relative) {
        Path source = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 5 && source != null; i++, source = source.getParent()) {
            if (Files.isRegularFile(source.resolve("pom.xml"))) {
                break;
            }
        }
        assertThat(source).isNotNull();
        assertThat(Files.exists(source.resolve(relative)))
                .as("%s 必须已删除", relative).isFalse();
    }

    @Test
    @DisplayName("Limits 通用文案不含全站禁用措辞（docs/06 §9.4）")
    void unsupportedHintHasNoBannedWording() {
        assertThat(Limits.UNSUPPORTED_HINT)
                .doesNotContain("兼容性最佳", "不支持 HEIC", "24 小时内有效", "仅 macOS");
    }
}
