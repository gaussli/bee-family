package com.beefamily.album.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.beefamily.album.common.BusinessException;
import com.beefamily.album.common.ErrorCode;
import com.beefamily.album.domain.PhotoTag;
import com.beefamily.album.domain.PhotoTagId;
import com.beefamily.album.domain.Tag;
import com.beefamily.album.repository.PhotoTagRepository;
import com.beefamily.album.repository.TagRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
 * {@link TagService} 单元测试（任务点 3）。
 *
 * <p>核心命题（T11 关键约束⑤）：请求的标签集合是**完整集合**，
 * 差分 = 「新增 请求−已有」「删除关联 已有−请求」，而 {@code tag} 行**只增不删**。
 * 断言重点不是「代码跑了」，而是「有没有误删用户没删的标签 / 有没有给未改动的标签重复建行」。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TagServiceTest {

    /** 测试家庭 id。 */
    private static final Long FAMILY_ID = 100L;

    /** 测试照片 id。 */
    private static final Long PHOTO_ID = 500L;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private PhotoTagRepository photoTagRepository;

    /** 标签创建者：独立事务建行（见 TagWriter 类注释，P1-② 的修复点）。 */
    @Mock
    private TagWriter tagWriter;

    private TagService tagService;

    @BeforeEach
    void setUp() {
        tagService = new TagService(tagRepository, photoTagRepository, tagWriter);
        when(photoTagRepository.findByPhotoIdIn(anyList())).thenReturn(List.of());
    }

    /**
     * 构造标签。
     *
     * @param id   标签 id
     * @param name 名称
     * @return 实体
     */
    private Tag tag(long id, String name) {
        Tag tag = new Tag();
        tag.setId(id);
        tag.setFamilyId(FAMILY_ID);
        tag.setName(name);
        return tag;
    }

    /**
     * 构造既有标签关联。
     *
     * @param tagId 标签 id
     * @return 关联
     */
    private PhotoTag relation(long tagId) {
        PhotoTag photoTag = new PhotoTag();
        photoTag.setId(new PhotoTagId(PHOTO_ID, tagId));
        return photoTag;
    }

    @Nested
    @DisplayName("名称规范化")
    class Normalisation {

        @Test
        @DisplayName("[\"海边\",\"第一次\",\"海边\"] 去重为 2 个且保持首次出现顺序")
        void deduplicatesKeepingOrder() {
            assertThat(tagService.normalizeNames(List.of("海边", "第一次", "海边")))
                    .containsExactly("海边", "第一次");
        }

        @Test
        @DisplayName("去首尾空格、丢弃空白与 null")
        void trimsAndDropsBlanks() {
            List<String> raw = new ArrayList<>();
            raw.add("  海边  ");
            raw.add("   ");
            raw.add(null);
            raw.add("海边");
            assertThat(tagService.normalizeNames(raw)).containsExactly("海边");
        }

        @Test
        @DisplayName("null / 空集合返回空列表")
        void emptyInput() {
            assertThat(tagService.normalizeNames(null)).isEmpty();
            assertThat(tagService.normalizeNames(List.of())).isEmpty();
        }

        @Test
        @DisplayName("超过 20 字符的标签 → 40001（TAG_NAME_MAX_LENGTH）")
        void rejectsOverlongName() {
            String tooLong = "海".repeat(21);
            assertThatThrownBy(() -> tagService.normalizeNames(List.of(tooLong)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("20")
                    .extracting(ex -> ((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_PARAM);
        }

        @Test
        @DisplayName("恰好 20 字符允许")
        void acceptsExactlyMaxLength() {
            assertThat(tagService.normalizeNames(List.of("海".repeat(20)))).hasSize(1);
        }
    }

    @Nested
    @DisplayName("applyToPhoto：差分写入")
    class ApplyToPhoto {

        @Test
        @DisplayName("去重后再解析标签：重复名只落 1 行关联")
        void dedupeBeforeResolve() {
            when(tagRepository.findByFamilyIdAndName(FAMILY_ID, "海边")).thenReturn(Optional.of(tag(1L, "海边")));
            when(tagWriter.createOrFind(FAMILY_ID, "第一次")).thenReturn(tag(2L, "第一次"));

            List<String> result = tagService.applyToPhoto(FAMILY_ID, PHOTO_ID, List.of("海边", "第一次", "海边"));

            assertThat(result).containsExactly("海边", "第一次");
            // 只创建一个新标签（第一次），海边复用既有行
            verify(tagWriter, times(1)).createOrFind(FAMILY_ID, "第一次");
            // 关联插入走幂等语句（联合主键冲突收敛成 1 行），不再用 saveAll
            verify(photoTagRepository).insertIfAbsent(PHOTO_ID, 1L);
            verify(photoTagRepository).insertIfAbsent(PHOTO_ID, 2L);
        }

        @Test
        @DisplayName("改为 [\"海边\"] 只删关联、不删 tag 行（孤儿标签留给 P1 清理）")
        void removalDeletesRelationButKeepsTagRow() {
            when(photoTagRepository.findByPhotoIdIn(List.of(PHOTO_ID)))
                    .thenReturn(List.of(relation(1L), relation(2L)));
            when(tagRepository.findByFamilyIdAndName(FAMILY_ID, "海边")).thenReturn(Optional.of(tag(1L, "海边")));

            List<String> result = tagService.applyToPhoto(FAMILY_ID, PHOTO_ID, List.of("海边"));

            assertThat(result).containsExactly("海边");
            ArgumentCaptor<List<PhotoTagId>> deleted = ArgumentCaptor.forClass(List.class);
            verify(photoTagRepository).deleteAllById(deleted.capture());
            assertThat(deleted.getValue())
                    .as("只删掉「沙滩」的关联，保留「海边」")
                    .containsExactly(new PhotoTagId(PHOTO_ID, 2L));
            verify(photoTagRepository, never()).insertIfAbsent(any(), any());
            verify(tagRepository, never()).delete(any());
            verify(tagRepository, never()).deleteById(any());
        }

        @Test
        @DisplayName("清空标签：删除全部关联、不新建任何标签")
        void clearAll() {
            when(photoTagRepository.findByPhotoIdIn(List.of(PHOTO_ID)))
                    .thenReturn(List.of(relation(1L), relation(2L)));

            assertThat(tagService.applyToPhoto(FAMILY_ID, PHOTO_ID, List.of())).isEmpty();

            ArgumentCaptor<List<PhotoTagId>> deleted = ArgumentCaptor.forClass(List.class);
            verify(photoTagRepository).deleteAllById(deleted.capture());
            assertThat(deleted.getValue()).hasSize(2);
            verify(tagWriter, never()).createOrFind(any(), any());
            verify(photoTagRepository, never()).insertIfAbsent(any(), any());
        }

        @Test
        @DisplayName("标签集合未变 → 零删除零新增（不给未改动标签刷新 created_at，避免打乱「最近使用」）")
        void noOpWhenUnchanged() {
            when(photoTagRepository.findByPhotoIdIn(List.of(PHOTO_ID)))
                    .thenReturn(List.of(relation(1L)));
            when(tagRepository.findByFamilyIdAndName(FAMILY_ID, "海边")).thenReturn(Optional.of(tag(1L, "海边")));

            tagService.applyToPhoto(FAMILY_ID, PHOTO_ID, List.of("海边"));

            verify(photoTagRepository, never()).deleteAllById(any());
            verify(photoTagRepository, never()).insertIfAbsent(any(), any());
            verify(tagWriter, never()).createOrFind(any(), any());
        }

        @Test
        @DisplayName("并发建同名标签 → 幂等 upsert 拿回既有行，不抛 500")
        void tagRaceFallsBackToExisting() {
            Tag existing = tag(42L, "生日");
            when(tagRepository.findByFamilyIdAndName(FAMILY_ID, "生日")).thenReturn(Optional.empty());
            // TagWriter 在独立事务里做幂等 upsert，冲突不抛异常；返回的就是既有行
            when(tagWriter.createOrFind(FAMILY_ID, "生日")).thenReturn(existing);

            List<String> result = tagService.applyToPhoto(FAMILY_ID, PHOTO_ID, List.of("生日"));

            assertThat(result).containsExactly("生日");
            verify(photoTagRepository).insertIfAbsent(PHOTO_ID, 42L);
        }

        @Test
        @DisplayName("标签归属家庭：解析时始终带 familyId（禁止跨家庭复用标签）")
        void resolvesWithinFamily() {
            when(tagRepository.findByFamilyIdAndName(any(), any())).thenReturn(Optional.empty());
            when(tagWriter.createOrFind(any(), any())).thenReturn(tag(7L, "海边"));

            tagService.applyToPhoto(FAMILY_ID, PHOTO_ID, List.of("海边"));

            verify(tagRepository).findByFamilyIdAndName(eq(FAMILY_ID), eq("海边"));
            verify(tagWriter).createOrFind(eq(FAMILY_ID), eq("海边"));
        }
    }
}
