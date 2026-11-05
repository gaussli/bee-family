package com.beefamily.album.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.beefamily.album.common.BusinessException;
import com.beefamily.album.common.ErrorCode;
import com.beefamily.album.common.PageQuery;
import com.beefamily.album.common.PageResponse;
import com.beefamily.album.domain.Baby;
import com.beefamily.album.domain.GrowthRecord;
import com.beefamily.album.dto.growth.GrowthCurveResp;
import com.beefamily.album.dto.growth.GrowthRecordReq;
import com.beefamily.album.dto.growth.GrowthRecordResp;
import com.beefamily.album.repository.GrowthRecordRepository;
import com.beefamily.album.security.FamilyAccessGuard;
import com.beefamily.album.support.AuthTestSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * {@link GrowthService} 单元测试（任务点 1 与 T07 区间语义）。
 *
 * <p>核心命题：**同一 {@code (babyId, recordedAt)} 重复提交必须只保留 1 行、值被覆盖、
 * 且不返回 409**。这里用 mock 仓储断言「调用语义」—— 即 {@code save()} 收到的是同一个实体实例
 * （走 UPDATE）而不是新建实例（会撞唯一键）。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GrowthServiceTest {

    /** 测试宝宝 id。 */
    private static final Long BABY_ID = 10L;

    /** 测试记录的原始创建者（与当前登录用户不同，用于验证 UPSERT 不篡改 createdBy）。 */
    private static final Long ORIGINAL_CREATOR = 55L;

    /** 当前登录用户。 */
    private static final Long CURRENT_USER = 1L;

    @Mock
    private GrowthRecordRepository growthRecordRepository;

    @Mock
    private BabyService babyService;

    /** 使用真实闸门：familyId / 角色断言是被测契约的一部分。 */
    private final FamilyAccessGuard guard = new FamilyAccessGuard();

    private GrowthService growthService;

    private Baby baby;

    @BeforeEach
    void setUp() {
        growthService = new GrowthService(growthRecordRepository, babyService, guard);
        AuthTestSupport.loginAsOwner(CURRENT_USER);
        baby = new Baby();
        baby.setId(BABY_ID);
        baby.setFamilyId(AuthTestSupport.FAMILY_ID);
        baby.setName("团团");
        baby.setBirthday(LocalDate.of(2025, 8, 18));
        when(babyService.requireBaby(BABY_ID)).thenReturn(baby);
        when(babyService.nicknameOf(any())).thenReturn("团团妈");
        when(babyService.nicknames(any())).thenReturn(Map.of());
        when(growthRecordRepository.save(any(GrowthRecord.class))).thenAnswer(invocation -> {
            GrowthRecord entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(99L);
            }
            return entity;
        });
    }

    @AfterEach
    void tearDown() {
        AuthTestSupport.logout();
    }

    /**
     * 构造请求。
     *
     * @param date   日期
     * @param height 身高
     * @param weight 体重
     * @return 请求
     */
    private GrowthRecordReq req(LocalDate date, String height, String weight) {
        return new GrowthRecordReq(date,
                height == null ? null : new BigDecimal(height),
                weight == null ? null : new BigDecimal(weight),
                null, null);
    }

    /**
     * 构造既有记录。
     *
     * @param id     记录 id
     * @param date   日期
     * @param height 身高
     * @return 实体
     */
    private GrowthRecord existing(long id, LocalDate date, String height) {
        GrowthRecord record = new GrowthRecord();
        record.setId(id);
        record.setBabyId(BABY_ID);
        record.setRecordedAt(date);
        record.setHeightCm(new BigDecimal(height));
        record.setCreatedBy(ORIGINAL_CREATOR);
        return record;
    }

    @Nested
    @DisplayName("UPSERT")
    class Upsert {

        @Test
        @DisplayName("无同日期记录 → 新建一行，createdBy 取当前用户")
        void createsWhenAbsent() {
            LocalDate date = LocalDate.of(2026, 3, 1);
            when(growthRecordRepository.findByBabyIdAndRecordedAt(BABY_ID, date)).thenReturn(Optional.empty());

            GrowthRecordResp resp = growthService.upsert(BABY_ID, req(date, "72.5", "8.40"));

            ArgumentCaptor<GrowthRecord> captor = ArgumentCaptor.forClass(GrowthRecord.class);
            verify(growthRecordRepository, times(1)).save(captor.capture());
            GrowthRecord saved = captor.getValue();
            assertThat(saved.getId()).isEqualTo(99L);
            assertThat(saved.getBabyId()).isEqualTo(BABY_ID);
            assertThat(saved.getCreatedBy()).isEqualTo(CURRENT_USER);
            assertThat(saved.getHeightCm()).isEqualByComparingTo("72.5");
            assertThat(saved.getWeightKg()).isEqualByComparingTo("8.40");
            assertThat(resp.id()).isEqualTo(99L);
            assertThat(resp.babyName()).isEqualTo("团团");
        }

        @Test
        @DisplayName("同 (babyId, recordedAt) 重复提交 → 复用同一实体（UPDATE，不新增），测量值被覆盖")
        void overwritesWhenPresent() {
            LocalDate date = LocalDate.of(2026, 3, 1);
            GrowthRecord previous = existing(7L, date, "70.0");
            when(growthRecordRepository.findByBabyIdAndRecordedAt(BABY_ID, date)).thenReturn(Optional.of(previous));

            GrowthRecordResp resp = growthService.upsert(BABY_ID, req(date, "73.0", "8.90"));

            ArgumentCaptor<GrowthRecord> captor = ArgumentCaptor.forClass(GrowthRecord.class);
            verify(growthRecordRepository, times(1)).save(captor.capture());
            GrowthRecord saved = captor.getValue();

            // 「只保留 1 行」的语义证明：save 收到的是带原 id 的同一实例，不是新对象
            assertThat(saved).isSameAs(previous);
            assertThat(saved.getId()).isEqualTo(7L);
            assertThat(saved.getHeightCm()).isEqualByComparingTo("73.0");
            assertThat(saved.getWeightKg()).isEqualByComparingTo("8.90");
            assertThat(resp.id()).isEqualTo(7L);
            assertThat(resp.heightCm()).isEqualByComparingTo("73.0");
        }

        @Test
        @DisplayName("UPSERT 不覆盖原始记录人 createdBy（历史归属保持）")
        void keepsOriginalCreatorOnOverwrite() {
            LocalDate date = LocalDate.of(2026, 3, 1);
            GrowthRecord previous = existing(7L, date, "70.0");
            when(growthRecordRepository.findByBabyIdAndRecordedAt(BABY_ID, date)).thenReturn(Optional.of(previous));

            growthService.upsert(BABY_ID, req(date, "71.0", null));

            assertThat(previous.getCreatedBy()).isEqualTo(ORIGINAL_CREATOR);
        }

        @Test
        @DisplayName("三项测量值全空 → 40001，且不写库")
        void rejectsEmptyMeasures() {
            LocalDate date = LocalDate.of(2026, 3, 1);
            GrowthRecordReq empty = new GrowthRecordReq(date, null, null, null, "只有备注");

            assertThatThrownBy(() -> growthService.upsert(BABY_ID, empty))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("请至少填写身高 / 体重 / 头围中的一项")
                    .extracting(ex -> ((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_PARAM);

            verify(growthRecordRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("PATCH 更新")
    class Update {

        @Test
        @DisplayName("改到已被别的记录占用的日期 → 40001 且文案可操作（不抛 SQL 唯一键异常）")
        void rejectsDateCollision() {
            LocalDate taken = LocalDate.of(2026, 4, 1);
            GrowthRecord target = existing(7L, LocalDate.of(2026, 3, 1), "70.0");
            GrowthRecord other = existing(9L, taken, "71.0");
            when(growthRecordRepository.findByIdAndFamilyId(7L, AuthTestSupport.FAMILY_ID))
                    .thenReturn(Optional.of(target));
            when(growthRecordRepository.findByBabyIdAndRecordedAt(BABY_ID, taken)).thenReturn(Optional.of(other));

            assertThatThrownBy(() -> growthService.update(7L, req(taken, "72.0", null)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("该日期已有测量记录")
                    .extracting(ex -> ((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_PARAM);

            verify(growthRecordRepository, never()).save(any());
        }

        @Test
        @DisplayName("日期不变（命中的是自己）→ 允许更新")
        void allowsSameDate() {
            LocalDate date = LocalDate.of(2026, 3, 1);
            GrowthRecord target = existing(7L, date, "70.0");
            when(growthRecordRepository.findByIdAndFamilyId(7L, AuthTestSupport.FAMILY_ID))
                    .thenReturn(Optional.of(target));
            when(growthRecordRepository.findByBabyIdAndRecordedAt(BABY_ID, date)).thenReturn(Optional.of(target));

            GrowthRecordResp resp = growthService.update(7L, req(date, "70.5", null));

            assertThat(resp.id()).isEqualTo(7L);
            assertThat(target.getHeightCm()).isEqualByComparingTo("70.5");
        }

        @Test
        @DisplayName("跨家庭 / 不存在的记录 → 40301")
        void rejectsCrossFamily() {
            when(growthRecordRepository.findByIdAndFamilyId(anyLong(), any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> growthService.delete(7L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.CROSS_FAMILY_ACCESS);
        }
    }

    @Nested
    @DisplayName("成长曲线区间语义：从最新一条记录往前推，而不是从今天推")
    class Curve {

        /**
         * 三条记录：2026-01-10 / 2026-02-10 / 2026-03-10。
         *
         * @return 升序记录
         */
        private List<GrowthRecord> threePoints() {
            return List.of(existing(1L, LocalDate.of(2026, 1, 10), "70.0"),
                    existing(2L, LocalDate.of(2026, 2, 10), "71.0"),
                    existing(3L, LocalDate.of(2026, 3, 10), "72.0"));
        }

        @Test
        @DisplayName("range=3m → from = 最新日期 - 3 个月")
        void threeMonthsAnchoredOnLatest() {
            when(growthRecordRepository.findByBabyIdOrderByRecordedAtAsc(BABY_ID)).thenReturn(threePoints());

            GrowthCurveResp curve = growthService.curve(BABY_ID, "3m");

            assertThat(curve.to()).isEqualTo(LocalDate.of(2026, 3, 10));
            assertThat(curve.from()).isEqualTo(LocalDate.of(2025, 12, 10));
            assertThat(curve.points()).hasSize(3);
            assertThat(curve.range()).isEqualTo("3m");
        }

        @Test
        @DisplayName("range=6m 排除区间外记录（2026-01-10 之外更早的不进曲线）")
        void sixMonthsFilters() {
            GrowthRecord tooOld = existing(0L, LocalDate.of(2025, 6, 1), "62.0");
            List<GrowthRecord> records = new java.util.ArrayList<>(threePoints());
            records.add(0, tooOld);
            when(growthRecordRepository.findByBabyIdOrderByRecordedAtAsc(BABY_ID)).thenReturn(records);

            GrowthCurveResp curve = growthService.curve(BABY_ID, "6m");

            assertThat(curve.from()).isEqualTo(LocalDate.of(2025, 9, 10));
            assertThat(curve.points()).hasSize(3);
            assertThat(curve.points()).noneMatch(point -> point.recordedAt().equals(LocalDate.of(2025, 6, 1)));
        }

        @Test
        @DisplayName("range=all 覆盖全部记录，from 取最早一条")
        void allRange() {
            when(growthRecordRepository.findByBabyIdOrderByRecordedAtAsc(BABY_ID)).thenReturn(threePoints());

            GrowthCurveResp curve = growthService.curve(BABY_ID, "ALL");

            assertThat(curve.range()).isEqualTo("all");
            assertThat(curve.from()).isEqualTo(LocalDate.of(2026, 1, 10));
            assertThat(curve.to()).isEqualTo(LocalDate.of(2026, 3, 10));
            assertThat(curve.points()).hasSize(3);
        }

        @Test
        @DisplayName("增量 = 最后一点 - 倒数第二点")
        void deltaUsesLastTwo() {
            when(growthRecordRepository.findByBabyIdOrderByRecordedAtAsc(BABY_ID)).thenReturn(threePoints());

            GrowthCurveResp curve = growthService.curve(BABY_ID, "all");

            assertThat(curve.delta().heightCm()).isEqualByComparingTo("1.0");
            assertThat(curve.latest().recordedAt()).isEqualTo(LocalDate.of(2026, 3, 10));
        }

        @Test
        @DisplayName("无记录 → 空点集、无 latest、不抛异常")
        void emptyCurve() {
            when(growthRecordRepository.findByBabyIdOrderByRecordedAtAsc(BABY_ID)).thenReturn(List.of());

            GrowthCurveResp curve = growthService.curve(BABY_ID, "3m");

            assertThat(curve.points()).isEmpty();
            assertThat(curve.latest()).isNull();
            assertThat(curve.from()).isNull();
            assertThat(curve.to()).isNull();
        }

        @Test
        @DisplayName("非法 range → 40002")
        void invalidRange() {
            assertThatThrownBy(() -> growthService.curve(BABY_ID, "7d"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("3m/6m/12m/all")
                    .extracting(ex -> ((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_ARGUMENT);
            assertThatThrownBy(() -> growthService.curve(BABY_ID, null))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("分页列表")
    class Listing {

        @Test
        @DisplayName("size 超上限被截断到 100（防止一次拉全表）")
        void sizeIsCapped() {
            org.springframework.data.domain.Page<GrowthRecord> emptyPage =
                    new org.springframework.data.domain.PageImpl<>(List.of());
            when(growthRecordRepository.findByBabyIdOrderByRecordedAtDesc(anyLong(), any()))
                    .thenReturn(emptyPage);

            PageResponse<GrowthRecordResp> resp = growthService.list(BABY_ID, new PageQuery(0, 5000));

            ArgumentCaptor<org.springframework.data.domain.Pageable> captor =
                    ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
            verify(growthRecordRepository).findByBabyIdOrderByRecordedAtDesc(anyLong(), captor.capture());
            assertThat(captor.getValue().getPageSize()).isEqualTo(PageQuery.MAX_SIZE);
            assertThat(resp.size()).isEqualTo(PageQuery.MAX_SIZE);
        }
    }
}
