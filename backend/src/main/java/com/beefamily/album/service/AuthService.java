package com.beefamily.album.service;

import com.beefamily.album.common.BusinessException;
import com.beefamily.album.common.ErrorCode;
import com.beefamily.album.domain.Family;
import com.beefamily.album.domain.FamilyMember;
import com.beefamily.album.domain.User;
import com.beefamily.album.domain.enums.Role;
import com.beefamily.album.dto.auth.ChangePasswordReq;
import com.beefamily.album.dto.auth.LoginReq;
import com.beefamily.album.dto.auth.MeResp;
import com.beefamily.album.dto.auth.RegisterReq;
import com.beefamily.album.dto.auth.TokenResp;
import com.beefamily.album.dto.auth.UpdateProfileReq;
import com.beefamily.album.dto.baby.BabyResp;
import com.beefamily.album.dto.family.FamilyResp;
import com.beefamily.album.mapper.FamilyMapper;
import com.beefamily.album.mapper.UserMapper;
import com.beefamily.album.repository.FamilyMemberRepository;
import com.beefamily.album.repository.FamilyRepository;
import com.beefamily.album.repository.UserRepository;
import com.beefamily.album.security.FamilyAccessGuard;
import com.beefamily.album.security.JwtService;
import com.beefamily.album.security.SecurityUtils;
import com.beefamily.album.security.UserPrincipal;
import com.beefamily.album.support.DateTimes;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证与账号服务。
 *
 * <p><b>Q7 旧邀请码已移除</b>：注册不再携带邀请码，也不再凭码加入家庭。
 * {@code user} 表为空时，注册即成为 owner 并自动创建家庭；非首用户在本阶段**不加入任何家庭**
 * （{@link #buildTokenResp} 已支持 {@code member == null} 返回无家庭登录态）。
 * v1.1 的「0 家庭引导 / 凭家庭码申请 / 多家庭切换」由 T02 / T03 按
 * {@code docs/07-architecture-incremental.md} §5.3.1 实现 —— T01 只做基础设施清理，
 * 不实现任何新业务，也不保留任何旧邀请码兼容分支。</p>
 *
 * <p><b>日志纪律</b>：只打印 userId / familyId 这类标识，**绝不打印明文密码、密码哈希或 token**
 * （§7.8）；错误日志也不回显请求体。</p>
 *
 * <p><b>R-3 主动失效</b>：登出与改密码把 {@code user.token_valid_after} 置为 now()，
 * 过滤器中 {@code iat < token_valid_after} 的 token 判为 40103。</p>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** 家庭名后缀。 */
    private static final String FAMILY_NAME_SUFFIX = "的家";

    /** 家庭名最大长度（与 DDL VARCHAR(40) 对齐）。 */
    private static final int FAMILY_NAME_MAX_LENGTH = 40;

    private final UserRepository userRepository;
    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final BabyService babyService;
    private final FamilyAccessGuard guard;

    /**
     * 构造服务。
     *
     * @param userRepository         用户仓储
     * @param familyRepository       家庭仓储
     * @param familyMemberRepository 成员仓储
     * @param passwordEncoder        密码编码器
     * @param jwtService             JWT 服务
     * @param babyService            宝宝服务（登录返回 babies）
     * @param guard                  家庭闸门
     */
    public AuthService(UserRepository userRepository,
                       FamilyRepository familyRepository,
                       FamilyMemberRepository familyMemberRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       BabyService babyService,
                       FamilyAccessGuard guard) {
        this.userRepository = userRepository;
        this.familyRepository = familyRepository;
        this.familyMemberRepository = familyMemberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.babyService = babyService;
        this.guard = guard;
    }

    /**
     * 注册（Q1：无任何码）。
     *
     * @param req 请求
     * @return 登录态（含 token）
     */
    @Transactional
    public TokenResp register(RegisterReq req) {
        String email = normalizeEmail(req.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        String nickname = req.nickname().trim();
        // 必须先取计数：插入后 count 恒 >= 1，会误判「首个用户」
        long existingUserCount = userRepository.count();

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setNickname(nickname);
        user = userRepository.save(user);

        FamilyMember member;
        if (existingUserCount == 0) {
            Family family = createFamily(user, nickname);
            member = joinFamily(family, user, Role.owner);
            log.info("bootstrap family familyId={} ownerId={}", family.getId(), user.getId());
        } else {
            // Q7：旧「凭邀请码加入家庭」链路已整体删除。此处刻意不加入任何家庭，
            // 让 TokenResp 走 member == null 分支（无家庭登录态）；申请 / 引导属 T02 / T03。
            member = null;
            log.info("register without family userId={}", user.getId());
        }
        return buildTokenResp(user, member);
    }

    /**
     * 登录。
     *
     * @param req 请求
     * @return 登录态（含 token）
     */
    @Transactional(readOnly = true)
    public TokenResp login(LoginReq req) {
        String email = normalizeEmail(req.email());
        User user = userRepository.findByEmailIgnoreCase(email)
                // 不暴露「账号是否存在」：用户不存在与密码错误返回同一错误码
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            log.warn("login failed userId={}", user.getId());
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        FamilyMember member = familyMemberRepository.findByUserId(user.getId()).orElse(null);
        log.info("login success userId={} familyId={}", user.getId(),
                member == null ? null : member.getFamilyId());
        return buildTokenResp(user, member);
    }

    /**
     * 当前用户信息。
     *
     * @return 当前用户 + 家庭 + 宝宝
     */
    @Transactional(readOnly = true)
    public MeResp me() {
        UserPrincipal principal = SecurityUtils.requirePrincipal();
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        FamilyMember member = familyMemberRepository.findByUserId(user.getId()).orElse(null);
        if (member == null) {
            return new MeResp(UserMapper.toResp(user), null, null, List.of());
        }
        Family family = familyRepository.findById(member.getFamilyId()).orElse(null);
        FamilyResp familyResp = buildFamilyResp(family, member.getRole());
        List<BabyResp> babies = family == null ? List.of() : babyService.listByFamily(family.getId());
        return new MeResp(UserMapper.toResp(user), familyResp, member.getRole(), babies);
    }

    /**
     * 更新个人资料（昵称 / 头像）。
     *
     * @param req 请求
     * @return 更新后的当前用户信息
     */
    @Transactional
    public MeResp updateProfile(UpdateProfileReq req) {
        UserPrincipal principal = SecurityUtils.requirePrincipal();
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (req.nickname() != null && !req.nickname().isBlank()) {
            // 昵称列 NOT NULL：传空视为不修改，避免把用户改成无名氏
            user.setNickname(req.nickname().trim());
        }
        if (req.avatarUrl() != null) {
            user.setAvatarUrl(req.avatarUrl());
        }
        userRepository.save(user);
        return me();
    }

    /**
     * 修改密码：校验原密码，成功后使既有 token 失效（需重新登录）。
     *
     * @param req 请求
     */
    @Transactional
    public void changePassword(ChangePasswordReq req) {
        UserPrincipal principal = SecurityUtils.requirePrincipal();
        User user = userRepository.findById(principal.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (!passwordEncoder.matches(req.oldPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_OLD_PASSWORD);
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setTokenValidAfter(DateTimes.now());
        userRepository.save(user);
        log.info("password changed userId={}", user.getId());
    }

    /**
     * 登出：无服务端会话，只把 token 水位线推到当前时刻。
     */
    @Transactional
    public void logout() {
        UserPrincipal principal = SecurityUtils.requirePrincipal();
        userRepository.findById(principal.userId()).ifPresent(user -> {
            user.setTokenValidAfter(DateTimes.now());
            userRepository.save(user);
        });
        log.info("logout userId={}", principal.userId());
    }

    /**
     * 创建家庭：默认名 {@code "{昵称}的家"}。
     *
     * <p>Q7 后不再写入邀请码 / 过期时间（V2 DDL 已删除这两列）。多家庭所需的
     * {@code family_code} 由 T02 / T03 补齐。</p>
     *
     * @param user     创建者
     * @param nickname 昵称
     * @return 家庭实体
     */
    private Family createFamily(User user, String nickname) {
        Family family = new Family();
        family.setName(defaultFamilyName(nickname));
        family.setOwnerId(user.getId());
        return familyRepository.save(family);
    }

    /**
     * 加入家庭。
     *
     * @param family 家庭
     * @param user   用户
     * @param role   角色
     * @return 成员关系
     */
    private FamilyMember joinFamily(Family family, User user, Role role) {
        FamilyMember member = new FamilyMember();
        member.setFamilyId(family.getId());
        member.setUserId(user.getId());
        member.setRole(role);
        return familyMemberRepository.save(member);
    }

    /**
     * 默认家庭名：昵称 + 「的家」，超长截断到 40 字。
     *
     * @param nickname 昵称
     * @return 家庭名
     */
    private String defaultFamilyName(String nickname) {
        String base = (nickname == null || nickname.isBlank()) ? "我们" : nickname.trim();
        String name = base + FAMILY_NAME_SUFFIX;
        return name.length() > FAMILY_NAME_MAX_LENGTH ? name.substring(0, FAMILY_NAME_MAX_LENGTH) : name;
    }

    /**
     * 组装登录态。
     *
     * @param user   用户
     * @param member 成员关系（可为 null：用户尚未加入任何家庭）
     * @return 登录态
     */
    private TokenResp buildTokenResp(User user, FamilyMember member) {
        JwtService.IssuedToken issued = jwtService.issue(user.getId(), user.getEmail());
        if (member == null) {
            return new TokenResp(issued.token(), issued.expiresAt(), UserMapper.toResp(user), null, null, null,
                    List.of());
        }
        Family family = familyRepository.findById(member.getFamilyId()).orElse(null);
        List<BabyResp> babies = family == null ? List.of() : babyService.listByFamily(family.getId());
        return new TokenResp(issued.token(), issued.expiresAt(), UserMapper.toResp(user),
                family == null ? null : family.getId(), family == null ? null : family.getName(),
                member.getRole(), babies);
    }

    /**
     * 组装家庭响应。
     *
     * <p>Q7 后旧邀请码链路已整体移除，这里仅保留「邀请码可见性」参数的既有形状
     * （{@code FamilyResp} / {@code FamilyMapper.toResp} 属 T02 / T03 的 domain 改造范围，
     * T01 不动 API 契约形状）。字段值恒为 {@code null}，不产生任何旧码数据。</p>
     *
     * @param family 家庭实体
     * @param role   当前用户角色
     * @return 家庭响应
     */
    private FamilyResp buildFamilyResp(Family family, Role role) {
        if (family == null) {
            return null;
        }
        Optional<User> owner = userRepository.findById(family.getOwnerId());
        long memberCount = familyMemberRepository.countByFamilyId(family.getId());
        return FamilyMapper.toResp(family, owner.map(User::getNickname).orElse(null), memberCount,
                role == Role.owner);
    }

    /**
     * 邮箱规范化：去空格 + 全小写（DDL 注释约定全小写存储）。
     *
     * @param email 原始邮箱
     * @return 规范化邮箱
     */
    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
