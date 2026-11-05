package com.beefamily.album.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.beefamily.album.dto.album.DeleteAlbumResp;
import com.beefamily.album.dto.album.PhotoStrategy;
import com.beefamily.album.security.JwtAuthenticationFilter;
import com.beefamily.album.service.AlbumService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link AlbumController} 参数绑定测试（@WebMvcTest）。
 *
 * <p>核心命题（任务点 2 的「非法 photoStrategy 的报错」）：
 * 非法枚举值必须落到 {@code 40002}（参数取值非法）而不是 500，
 * 且默认值必须是 {@code move}（删照片必须是用户显式选择）。</p>
 */
@WebMvcTest(controllers = AlbumController.class)
@AutoConfigureMockMvc(addFilters = false)
class AlbumControllerParamTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AlbumService albumService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    @DisplayName("photoStrategy=purge（非法枚举）→ 400 + 40002，且 service 未被调用")
    void invalidStrategyIsBadRequest() throws Exception {
        mockMvc.perform(delete("/api/albums/1").param("photoStrategy", "purge"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40002));

        verifyNoInteractions(albumService);
    }

    @Test
    @DisplayName("photoStrategy 大小写敏感：DELETE 视为非法（40002）")
    void strategyIsCaseSensitive() throws Exception {
        mockMvc.perform(delete("/api/albums/1").param("photoStrategy", "DELETE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40002));

        verifyNoInteractions(albumService);
    }

    @Test
    @DisplayName("缺省 photoStrategy → 默认 move（不删照片）")
    void defaultStrategyIsMove() throws Exception {
        when(albumService.delete(eq(1L), any())).thenReturn(new DeleteAlbumResp(0, 3));

        mockMvc.perform(delete("/api/albums/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.movedPhotos").value(3));

        verify(albumService).delete(1L, PhotoStrategy.move);
    }

    @Test
    @DisplayName("photoStrategy=delete → 透传 delete 策略")
    void explicitDeleteStrategy() throws Exception {
        when(albumService.delete(eq(1L), any())).thenReturn(new DeleteAlbumResp(5, 0));

        mockMvc.perform(delete("/api/albums/1").param("photoStrategy", "delete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deletedPhotos").value(5));

        verify(albumService).delete(1L, PhotoStrategy.delete);
    }

    @Test
    @DisplayName("相册名为空 → 400 + 40001（@Size min=1 生效）")
    void blankNameRejected() throws Exception {
        mockMvc.perform(post("/api/albums")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"description\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));

        verifyNoInteractions(albumService);
    }

    @Test
    @DisplayName("相册名超 40 字 → 400 + 40001")
    void overlongNameRejected() throws Exception {
        String body = "{\"name\":\"" + "长".repeat(41) + "\"}";

        mockMvc.perform(post("/api/albums")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }
}
