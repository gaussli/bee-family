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
import com.beefamily.album.support.InviteCodes;
import com.beefamily.album.support.Limits;
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
 * <p><b>首用户自建家庭</b>：{@code user} 表为空时，注册即成为 owner 并自动创建家庭 + 24h 邀请码；
 * 之后注册必须带有效邀请码（40902）且家庭未满 6 人（40903）。整个注册在同一事务内完成。</p>
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
     * 注册。
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

        Family family;
        FamilyMember member;
        if (existingUserCount == 0) {
            family = createFamily(user, nickname);
            member = joinFamily(family, user, Role.owner);
            log.info("bootstrap family familyId={} ownerId={}", family.getId(), user.getId());
        } else {
            family = resolveFamilyByInviteCode(req.inviteCode());
            member = joinFamily(family, user, Role.member);
            log.info("join family familyId={} userId={}", family.getId(), user.getId());
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
     * 创建家庭：默认名 {@code "{昵称}的家"} + 8 位邀请码（24h）。
     *
     * @param user     创建者
     * @param nickname 昵称
     * @return 家庭实体
     */
    private Family createFamily(User user, String nickname) {
        Family family = new Family();
        family.setName(defaultFamilyName(nickname));
        family.setOwnerId(user.getId());
        family.setInviteCode(generateUniqueInviteCode());
        family.setInviteCodeExpiresAt(DateTimes.now().plus(Limits.INVITE_CODE_TTL));
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
     * 校验邀请码并返回家庭。
     *
     * @param inviteCode 邀请码
     * @return 家庭
     */
    private Family resolveFamilyByInviteCode(String inviteCode) {
        String code = inviteCode == null ? "" : inviteCode.trim().toUpperCase(Locale.ROOT);
        if (code.isEmpty()) {
            throw new BusinessException(ErrorCode.INVITE_CODE_INVALID);
        }
        Family family = familyRepository.findByInviteCode(code)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVITE_CODE_INVALID));
        if (family.getInviteCodeExpiresAt() == null
                || family.getInviteCodeExpiresAt().isBefore(DateTimes.now())) {
            throw new BusinessException(ErrorCode.INVITE_CODE_INVALID);
        }
        long memberCount = familyMemberRepository.countByFamilyId(family.getId());
        if (memberCount >= Limits.FAMILY_MEMBER_LIMIT) {
            throw new BusinessException(ErrorCode.FAMILY_MEMBER_LIMIT);
        }
        return family;
    }

    /**
     * 生成不被占用的邀请码（撞唯一索引时重试）。
     *
     * @return 8 位邀请码
     */
    private String generateUniqueInviteCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String code = InviteCodes.generate();
            if (!familyRepository.existsByInviteCode(code)) {
                return code;
            }
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "邀请码生成失败，请重试");
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
     * @param member 成员关系（可为 null：用户已被移出家庭）
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
     * 组装家庭响应（邀请码仅 owner 可见）。
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
