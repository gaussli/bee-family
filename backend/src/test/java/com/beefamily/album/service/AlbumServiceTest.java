package com.beefamily.album.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.beefamily.album.common.BusinessException;
import com.beefamily.album.common.ErrorCode;
import com.beefamily.album.domain.Album;
import com.beefamily.album.domain.Photo;
import com.beefamily.album.dto.album.AlbumResp;
import com.beefamily.album.dto.album.AlbumUpdateReq;
import com.beefamily.album.dto.album.DeleteAlbumResp;
import com.beefamily.album.dto.album.PhotoStrategy;
import com.beefamily.album.repository.AlbumRepository;
import com.beefamily.album.repository.PhotoRepository;
import com.beefamily.album.repository.UserRepository;
import com.beefamily.album.security.FamilyAccessGuard;
import com.beefamily.album.support.AuthTestSupport;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * {@link AlbumService} 单元测试（任务点 2）。
 *
 * <p>核心命题：删相册的两种照片处置策略**不能互相串味**——
 * {@code move} 绝不能删照片（一删就是用户不可恢复的家庭回忆），
 * {@code delete} 必须走统一的照片删除编排（清标签 / 清封面 / 维护计数 / 提交后删文件）。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AlbumServiceTest {

    /** 测试相册 id。 */
    private static final Long ALBUM_ID = 20L;

    /** 测试照片 id。 */
    private static final Long PHOTO_ID = 300L;

    @Mock
    private AlbumRepository albumRepository;

    @Mock
    private PhotoRepository photoRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PhotoService photoService;

    /** 使用真实闸门（而非 mock）：角色/家庭隔离规则本身就是被测对象。 */
    private final FamilyAccessGuard guard = new FamilyAccessGuard();

    private AlbumService albumService;

    private Album album;

    @BeforeEach
    void setUp() {
        albumService = new AlbumService(albumRepository, photoRepository, userRepository, photoService, guard);
        AuthTestSupport.loginAsOwner(1L);

        album = new Album();
        album.setId(ALBUM_ID);
        album.setFamilyId(AuthTestSupport.FAMILY_ID);
        album.setName("2026 夏天");
        album.setPhotoCount(2);
        album.setCreatedBy(1L);

        when(albumRepository.findByIdAndFamilyId(ALBUM_ID, AuthTestSupport.FAMILY_ID))
                .thenReturn(Optional.of(album));
        when(albumRepository.save(any(Album.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findById(anyLong())).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        AuthTestSupport.logout();
    }

    /**
     * 构造照片。
     *
     * @param id        照片 id
     * @param albumId   所属相册
     * @param thumbKey  缩略图 key
     * @return 实体
     */
    private Photo photo(long id, Long albumId, String thumbKey) {
        Photo photo = new Photo();
        photo.setId(id);
        photo.setFamilyId(AuthTestSupport.FAMILY_ID);
        photo.setAlbumId(albumId);
        photo.setThumbKey(thumbKey);
        return photo;
    }

    @Nested
    @DisplayName("删除相册：photoStrategy 分支")
    class DeleteStrategy {

        @Test
        @DisplayName("move 策略：照片移到未分类，绝不调用删除编排")
        void moveStrategyKeepsPhotos() {
            when(photoRepository.moveToUnclassified(AuthTestSupport.FAMILY_ID, ALBUM_ID)).thenReturn(2);

            DeleteAlbumResp resp = albumService.delete(ALBUM_ID, PhotoStrategy.move);

            assertThat(resp.movedPhotos()).isEqualTo(2);
            assertThat(resp.deletedPhotos()).isZero();
            verify(photoRepository).moveToUnclassified(AuthTestSupport.FAMILY_ID, ALBUM_ID);
            verify(photoService, never()).deletePhotosBulk(any(), any());
            verify(albumRepository).delete(album);
        }

        @Test
        @DisplayName("delete 策略：整册照片走统一删除编排，且不执行 move")
        void deleteStrategyRemovesPhotos() {
            List<Photo> photos = List.of(photo(1L, ALBUM_ID, "a"), photo(2L, ALBUM_ID, "b"));
            when(photoRepository.findByFamilyIdAndAlbumId(AuthTestSupport.FAMILY_ID, ALBUM_ID)).thenReturn(photos);

            DeleteAlbumResp resp = albumService.delete(ALBUM_ID, PhotoStrategy.delete);

            assertThat(resp.deletedPhotos()).isEqualTo(2);
            assertThat(resp.movedPhotos()).isZero();
            verify(photoService).deletePhotosBulk(AuthTestSupport.FAMILY_ID, photos);
            verify(photoRepository, never()).moveToUnclassified(anyLong(), anyLong());
            verify(albumRepository).delete(album);
        }

        @Test
        @DisplayName("strategy 为 null 时默认 move（删除照片必须是显式选择，不能是默认行为）")
        void nullStrategyDefaultsToMove() {
            when(photoRepository.moveToUnclassified(AuthTestSupport.FAMILY_ID, ALBUM_ID)).thenReturn(0);

            DeleteAlbumResp resp = albumService.delete(ALBUM_ID, null);

            assertThat(resp.movedPhotos()).isZero();
            assertThat(resp.deletedPhotos()).isZero();
            verify(photoService, never()).deletePhotosBulk(any(), any());
        }

        @Test
        @DisplayName("相册不存在 / 跨家庭 → 40301，且不执行任何删除")
        void unknownAlbumIsForbidden() {
            when(albumRepository.findByIdAndFamilyId(999L, AuthTestSupport.FAMILY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> albumService.delete(999L, PhotoStrategy.delete))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.CROSS_FAMILY_ACCESS);

            verify(photoService, never()).deletePhotosBulk(any(), any());
            verify(albumRepository, never()).delete(any());
        }

        @Test
        @DisplayName("非创建者且非 owner → 40303，且不执行删除")
        void nonCreatorCannotDelete() {
            AuthTestSupport.loginAsMember(77L);

            assertThatThrownBy(() -> albumService.delete(ALBUM_ID, PhotoStrategy.delete))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.NOT_RESOURCE_CREATOR);

            verify(albumRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("封面解析与设置")
    class Cover {

        @Test
        @DisplayName("未设封面 → 兜底取相册内最新照片的缩略图")
        void fallsBackToLatestPhoto() {
            when(photoRepository.findFirstByFamilyIdAndAlbumIdOrderByTakenAtDescIdDesc(
                    AuthTestSupport.FAMILY_ID, ALBUM_ID)).thenReturn(Optional.of(photo(PHOTO_ID, ALBUM_ID, "thumb-300")));
            when(photoRepository.findMinTakenAt(AuthTestSupport.FAMILY_ID, ALBUM_ID)).thenReturn(null);
            when(photoRepository.findMaxTakenAt(AuthTestSupport.FAMILY_ID, ALBUM_ID)).thenReturn(null);

            assertThat(albumService.detail(ALBUM_ID).coverThumbKey()).isEqualTo("thumb-300");
        }

        @Test
        @DisplayName("封面照片已被删除（悬挂引用）→ 降级为最新照片，不返回破图 key")
        void danglingCoverFallsBack() {
            album.setCoverPhotoId(999L);
            when(photoRepository.findByIdAndFamilyId(999L, AuthTestSupport.FAMILY_ID))
                    .thenReturn(Optional.empty());
            when(photoRepository.findFirstByFamilyIdAndAlbumIdOrderByTakenAtDescIdDesc(
                    AuthTestSupport.FAMILY_ID, ALBUM_ID)).thenReturn(Optional.of(photo(PHOTO_ID, ALBUM_ID, "thumb-300")));

            assertThat(albumService.detail(ALBUM_ID).coverThumbKey())
                    .as("悬挂 cover_photo_id 不能让前端拿到已删照片的 key")
                    .isEqualTo("thumb-300");
        }

        @Test
        @DisplayName("设置封面必须属于本相册，否则 40002")
        void coverMustBelongToAlbum() {
            when(photoRepository.findByIdAndFamilyId(PHOTO_ID, AuthTestSupport.FAMILY_ID))
                    .thenReturn(Optional.of(photo(PHOTO_ID, 999L, "other-album")));
            AlbumUpdateReq req = new AlbumUpdateReq("2026 夏天", null, PHOTO_ID);

            assertThatThrownBy(() -> albumService.update(ALBUM_ID, req))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("封面只能选择本相册内的照片")
                    .extracting(ex -> ((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_ARGUMENT);
        }

        @Test
        @DisplayName("封面照片不存在 / 跨家庭 → 40301")
        void coverOfAnotherFamilyIsForbidden() {
            when(photoRepository.findByIdAndFamilyId(PHOTO_ID, AuthTestSupport.FAMILY_ID))
                    .thenReturn(Optional.empty());
            AlbumUpdateReq req = new AlbumUpdateReq("2026 夏天", null, PHOTO_ID);

            assertThatThrownBy(() -> albumService.update(ALBUM_ID, req))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.CROSS_FAMILY_ACCESS);
        }

        @Test
        @DisplayName("coverPhotoId=null → 清除自定义封面（窄 UPDATE，绝不整行回写 photo_count）")
        void nullCoverClears() {
            album.setCoverPhotoId(PHOTO_ID);
            AlbumUpdateReq req = new AlbumUpdateReq(null, "新描述", null);

            albumService.update(ALBUM_ID, req);

            verify(albumRepository).updateCover(eq(ALBUM_ID), eq(AuthTestSupport.FAMILY_ID), isNull(), any());
            verify(albumRepository).updateMetadata(eq(ALBUM_ID), eq(AuthTestSupport.FAMILY_ID),
                    eq("2026 夏天"), eq("新描述"), any());
            // 回归护栏：整行 save 会把读出来的 photo_count 覆盖回旧值（并发上传刚 +1 就丢了）
            verify(albumRepository, never()).save(any(Album.class));
        }

        @Test
        @DisplayName("元信息与封面都没变 → 一条 UPDATE 都不发（不打乱相册列表的 updated_at 排序）")
        void noopUpdateWritesNothing() {
            AlbumUpdateReq req = new AlbumUpdateReq("2026 夏天", null, null);

            albumService.update(ALBUM_ID, req);

            verify(albumRepository, never()).updateMetadata(any(), any(), any(), any(), any());
            verify(albumRepository, never()).updateCover(any(), any(), any(), any());
            verify(albumRepository, never()).save(any(Album.class));
        }
    }

    @Nested
    @DisplayName("创建相册")
    class Create {

        @Test
        @DisplayName("同名相册 → 40905")
        void duplicateNameRejected() {
            when(albumRepository.existsByFamilyIdAndName(AuthTestSupport.FAMILY_ID, "2026 夏天")).thenReturn(true);

            assertThatThrownBy(() -> albumService.create(
                    new com.beefamily.album.dto.album.AlbumReq("2026 夏天", null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.ALBUM_NAME_DUPLICATED);

            verify(albumRepository, never()).save(any());
        }

        @Test
        @DisplayName("新建相册 photoCount 从 0 开始（计数由 PhotoService 维护，这里不能写别的值）")
        void newAlbumStartsAtZero() {
            when(albumRepository.existsByFamilyIdAndName(any(), any())).thenReturn(false);
            when(albumRepository.save(any(Album.class))).thenAnswer(invocation -> {
                Album saved = invocation.getArgument(0);
                saved.setId(88L);
                return saved;
            });

            AlbumResp resp = albumService.create(new com.beefamily.album.dto.album.AlbumReq(" 2026 秋天 ", "  "));

            assertThat(resp.name()).isEqualTo("2026 秋天");
            assertThat(resp.photoCount()).isZero();
        }
    }
}
