package com.beefamily.album.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.beefamily.album.common.PageQuery;
import com.beefamily.album.domain.Baby;
import com.beefamily.album.domain.GrowthRecord;
import com.beefamily.album.domain.Milestone;
import com.beefamily.album.domain.Photo;
import com.beefamily.album.dto.milestone.MilestoneResp;
import com.beefamily.album.dto.photo.PhotoResp;
import com.beefamily.album.dto.timeline.TimelineGroupResp;
import com.beefamily.album.dto.timeline.TimelineItemResp;
import com.beefamily.album.dto.timeline.TimelinePageResp;
import com.beefamily.album.repository.BabyRepository;
import com.beefamily.album.repository.GrowthRecordRepository;
import com.beefamily.album.repository.MilestoneRepository;
import com.beefamily.album.repository.PhotoRepository;
import com.beefamily.album.repository.TimelineQueryRepository;
import com.beefamily.album.security.FamilyAccessGuard;
import com.beefamily.album.support.AuthTestSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * {@link TimelineService} 单元测试（任务点 8）。
 *
 * <p><b>为什么这组断言最值钱</b>：时间轴第 2 页起出现「重复 / 漏项」的根本原因不是 SQL，
 * 而是 Java 侧重建 items 时顺序漂移 —— 明细查询的返回顺序与 SQL 全局序无关。
 * 因此这里刻意让 mock 明细仓储**乱序返回**，再看输出是否严格按 keys 顺序。</p>
 *
 * <p>同时钉住同秒三级 tie-break 的顺序：{@code photo(0) → milestone(1) → growth(2)}。
 * 注意这不是字典序（字典序是 growth &lt; milestone &lt; photo，正好相反）。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TimelineServiceTest {

    /** 同一个月内三个类型的共同时间点。 */
    private static final LocalDateTime TIE_TIME = LocalDateTime.of(2026, 10, 5, 10, 0, 0);

    /** 更早的一个月。 */
    private static final LocalDateTime OLDER_TIME = LocalDateTime.of(2026, 8, 1, 9, 0, 0);

    @Mock
    private TimelineQueryRepository timelineQueryRepository;

    @Mock
    private PhotoRepository photoRepository;

    @Mock
    private MilestoneRepository milestoneRepository;

    @Mock
    private GrowthRecordRepository growthRecordRepository;

    @Mock
    private BabyRepository babyRepository;

    @Mock
    private PhotoService photoService;

    @Mock
    private MilestoneService milestoneService;

    @Mock
    private BabyService babyService;

    /** 真实闸门：familyId 走真实主体解析。 */
    private final FamilyAccessGuard guard = new FamilyAccessGuard();

    private TimelineService timelineService;

    @BeforeEach
    void setUp() {
        timelineService = new TimelineService(timelineQueryRepository, photoRepository, milestoneRepository,
                growthRecordRepository, babyRepository, photoService, milestoneService, babyService, guard);
        AuthTestSupport.loginAsOwner(1L);

        when(photoService.toRespBatch(any(), eq(AuthTestSupport.FAMILY_ID))).thenAnswer(invocation -> {
            List<Photo> photos = invocation.getArgument(0);
            return photos.stream().map(photo -> photoResp(photo.getId())).toList();
        });
        when(milestoneService.toRespBatch(any(), eq(AuthTestSupport.FAMILY_ID))).thenAnswer(invocation -> {
            List<Milestone> milestones = invocation.getArgument(0);
            return milestones.stream().map(milestone -> milestoneResp(milestone.getId())).toList();
        });
        when(babyService.nicknames(any())).thenReturn(Map.of(1L, "妈妈"));
    }

    @AfterEach
    void tearDown() {
        AuthTestSupport.logout();
    }

    /**
     * 构造照片响应。
     *
     * @param id 照片 id
     * @return 响应
     */
    private PhotoResp photoResp(long id) {
        return new PhotoResp(id, null, null, "标题" + id, null, "thumb-" + id, "preview-" + id, "image/jpeg",
                1024L, 4032, 3024, TIE_TIME, null, List.of(), TIE_TIME, 1L, "妈妈");
    }

    /**
     * 构造里程碑响应。
     *
     * @param id 里程碑 id
     * @return 响应
     */
    private MilestoneResp milestoneResp(long id) {
        return new MilestoneResp(id, 1L, "团团", "第一次走路" + id, null, LocalDate.of(2026, 10, 5),
                "1岁1个月17天", null, null, 1L, "妈妈", TIE_TIME);
    }

    /**
     * 构造照片实体。
     *
     * @param id 照片 id
     * @return 实体
     */
    private Photo photo(long id) {
        Photo photo = new Photo();
        photo.setId(id);
        photo.setFamilyId(AuthTestSupport.FAMILY_ID);
        photo.setTakenAt(TIE_TIME);
        return photo;
    }

    /**
     * 构造里程碑实体。
     *
     * @param id 里程碑 id
     * @return 实体
     */
    private Milestone milestone(long id) {
        Milestone milestone = new Milestone();
        milestone.setId(id);
        milestone.setFamilyId(AuthTestSupport.FAMILY_ID);
        milestone.setAchievedAt(LocalDate.of(2026, 10, 5));
        return milestone;
    }

    /**
     * 构造成长记录实体。
     *
     * @param id 记录 id
     * @return 实体
     */
    private GrowthRecord growth(long id) {
        GrowthRecord record = new GrowthRecord();
        record.setId(id);
        record.setBabyId(1L);
        record.setRecordedAt(LocalDate.of(2026, 10, 5));
        record.setHeightCm(new BigDecimal("75.0"));
        record.setCreatedBy(1L);
        return record;
    }

    /**
     * 统计一页内的条目总数（跨分组求和）。
     *
     * @param page 时间轴页
     * @return 条目数
     */
    private int countItems(TimelinePageResp page) {
        return page.groups().stream().mapToInt(group -> group.items().size()).sum();
    }

    /**
     * 打桩四类明细查询。
     *
     * @param photos     照片
     * @param milestones 里程碑
     * @param growths    成长记录
     */
    private void stubDetails(List<Photo> photos, List<Milestone> milestones, List<GrowthRecord> growths) {
        when(photoRepository.findAllByIdIn(any())).thenReturn(photos);
        when(milestoneRepository.findAllByIdIn(any())).thenReturn(milestones);
        when(growthRecordRepository.findAllByIdIn(any())).thenReturn(growths);
        Baby baby = new Baby();
        baby.setId(1L);
        baby.setName("团团");
        when(babyRepository.findByIdInAndFamilyId(any(), any())).thenReturn(List.of(baby));
    }

    @Test
    @DisplayName("严格按 keys 顺序重建条目：同秒 tie-break 为 photo→milestone→growth（非字典序）")
    void preservesKeyOrderForSameTimestamp() {
        // keys 已是 SQL 返回的全局序：同秒 photo 在前、growth 在后
        when(timelineQueryRepository.countAll(AuthTestSupport.FAMILY_ID)).thenReturn(4L);
        when(timelineQueryRepository.fetchKeys(eq(AuthTestSupport.FAMILY_ID), eq(0), any(Integer.class)))
                .thenReturn(List.of(
                        new TimelineQueryRepository.TimelineKey(TimelineQueryRepository.TYPE_PHOTO, 31L, TIE_TIME),
                        new TimelineQueryRepository.TimelineKey(TimelineQueryRepository.TYPE_MILESTONE, 21L, TIE_TIME),
                        new TimelineQueryRepository.TimelineKey(TimelineQueryRepository.TYPE_GROWTH, 11L, TIE_TIME),
                        new TimelineQueryRepository.TimelineKey(TimelineQueryRepository.TYPE_PHOTO, 41L, OLDER_TIME)));
        // 明细仓储故意乱序返回
        stubDetails(List.of(photo(41L), photo(31L)), List.of(milestone(21L)), List.of(growth(11L)));

        TimelinePageResp page = timelineService.page(new PageQuery(0, 10));

        assertThat(page.groups()).hasSize(2);

        TimelineGroupResp october = page.groups().get(0);
        assertThat(october.month()).isEqualTo("2026-10");
        assertThat(october.label()).isEqualTo("2026 年 10 月");
        assertThat(october.items())
                .extracting(TimelineItemResp::type)
                .as("同秒必须 photo → milestone → growth；若按类型名排序会变成 growth 在前，即 bug")
                .containsExactly("photo", "milestone", "growth");
        assertThat(october.items()).extracting(TimelineItemResp::id).containsExactly(31L, 21L, 11L);

        TimelineGroupResp august = page.groups().get(1);
        assertThat(august.month()).isEqualTo("2026-08");
        assertThat(august.items()).extracting(TimelineItemResp::id).containsExactly(41L);
        assertThat(august.items().get(0).photo()).isNotNull();
    }

    @Test
    @DisplayName("类型过滤下发：明细查询只带本类型 id（不把 milestone id 混进 photo 查询）")
    void detailQueriesAreTypeScoped() {
        when(timelineQueryRepository.countAll(AuthTestSupport.FAMILY_ID)).thenReturn(3L);
        when(timelineQueryRepository.fetchKeys(eq(AuthTestSupport.FAMILY_ID), eq(0), any(Integer.class)))
                .thenReturn(List.of(
                        new TimelineQueryRepository.TimelineKey(TimelineQueryRepository.TYPE_PHOTO, 31L, TIE_TIME),
                        new TimelineQueryRepository.TimelineKey(TimelineQueryRepository.TYPE_PHOTO, 41L, OLDER_TIME),
                        new TimelineQueryRepository.TimelineKey(TimelineQueryRepository.TYPE_MILESTONE, 21L, OLDER_TIME)));
        stubDetails(List.of(photo(31L), photo(41L)), List.of(milestone(21L)), List.of());

        timelineService.page(new PageQuery(0, 10));

        verify(photoRepository).findAllByIdIn(List.of(31L, 41L));
        verify(milestoneRepository).findAllByIdIn(List.of(21L));
    }

    @Test
    @DisplayName("hasMore 由 size+1 探测决定，多取的那条不进入结果")
    void hasMoreUsesExtraProbeRow() {
        when(timelineQueryRepository.countAll(AuthTestSupport.FAMILY_ID)).thenReturn(9L);
        when(timelineQueryRepository.fetchKeys(eq(AuthTestSupport.FAMILY_ID), eq(0), any(Integer.class)))
                .thenReturn(List.of(
                        new TimelineQueryRepository.TimelineKey(TimelineQueryRepository.TYPE_PHOTO, 31L, TIE_TIME),
                        new TimelineQueryRepository.TimelineKey(TimelineQueryRepository.TYPE_PHOTO, 32L, TIE_TIME),
                        new TimelineQueryRepository.TimelineKey(TimelineQueryRepository.TYPE_PHOTO, 33L, TIE_TIME)));
        stubDetails(List.of(photo(31L), photo(32L)), List.of(), List.of());

        TimelinePageResp page = timelineService.page(new PageQuery(0, 2));

        assertThat(page.hasMore()).isTrue();
        assertThat(countItems(page)).isEqualTo(2);
        assertThat(page.size()).isEqualTo(2);
        assertThat(page.totalPages()).isEqualTo(5);
    }

    @Test
    @DisplayName("空页：groups 为空、hasMore=false、total 原样上报（前端不无限翻页）")
    void emptyPage() {
        when(timelineQueryRepository.countAll(AuthTestSupport.FAMILY_ID)).thenReturn(0L);
        when(timelineQueryRepository.fetchKeys(eq(AuthTestSupport.FAMILY_ID), any(Integer.class), any(Integer.class)))
                .thenReturn(List.of());

        TimelinePageResp page = timelineService.page(new PageQuery(3, 24));

        assertThat(page.groups()).isEmpty();
        assertThat(page.hasMore()).isFalse();
        assertThat(page.total()).isZero();
        assertThat(page.totalPages()).isZero();
        assertThat(page.page()).isEqualTo(3);
    }

    @Test
    @DisplayName("分页参数归一化：page=-1 → 0，size=0 → 默认 24，offset 随之计算")
    void normalisesPaging() {
        when(timelineQueryRepository.countAll(AuthTestSupport.FAMILY_ID)).thenReturn(1L);
        when(timelineQueryRepository.fetchKeys(eq(AuthTestSupport.FAMILY_ID), eq(0), any(Integer.class)))
                .thenReturn(List.of(
                        new TimelineQueryRepository.TimelineKey(TimelineQueryRepository.TYPE_GROWTH, 11L, TIE_TIME)));
        stubDetails(List.of(), List.of(), List.of(growth(11L)));

        TimelinePageResp page = timelineService.page(new PageQuery(-1, 0));

        verify(timelineQueryRepository).fetchKeys(AuthTestSupport.FAMILY_ID, 0, PageQuery.DEFAULT_TIMELINE_SIZE + 1);
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(PageQuery.DEFAULT_TIMELINE_SIZE);
    }

    @Test
    @DisplayName("跨月边界：同一条目跨月时正确开启新分组（不合并、不丢组）")
    void groupsAcrossMonths() {
        when(timelineQueryRepository.countAll(AuthTestSupport.FAMILY_ID)).thenReturn(2L);
        when(timelineQueryRepository.fetchKeys(eq(AuthTestSupport.FAMILY_ID), eq(0), any(Integer.class)))
                .thenReturn(List.of(
                        new TimelineQueryRepository.TimelineKey(TimelineQueryRepository.TYPE_PHOTO, 31L,
                                LocalDateTime.of(2026, 12, 31, 23, 59, 59)),
                        new TimelineQueryRepository.TimelineKey(TimelineQueryRepository.TYPE_PHOTO, 32L,
                                LocalDateTime.of(2027, 1, 1, 0, 0, 0))));
        stubDetails(List.of(photo(31L), photo(32L)), List.of(), List.of());

        TimelinePageResp page = timelineService.page(new PageQuery(0, 10));

        assertThat(page.groups()).extracting(TimelineGroupResp::month).containsExactly("2026-12", "2027-01");
        assertThat(page.groups()).extracting(TimelineGroupResp::label).containsExactly("2026 年 12 月", "2027 年 1 月");
    }
}
