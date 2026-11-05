package com.beefamily.album.dto.photo;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link PatchField} 三态反序列化测试。
 *
 * <p>为什么值得单独立一条：这里是「PATCH 不传 albumId 把照片移出相册」（QA P1-①）的唯一防线，
 * 而且它**踩过一个不容易发现的坑** —— record 组件缺省时 Jackson 走的是
 * {@code getAbsentValue}（默认实现委托给 {@code getNullValue}），只覆写 {@code getNullValue}
 * 会让「没传」变成「传了 null」，三态静默退化成两态，只有真实 HTTP 请求才看得出来。
 * 这条用例把三种形态钉死在 DTO 层，不需要起服务就能守住。</p>
 */
class PatchFieldDeserializerTest {

    /** 与 Spring Boot 默认一致：注册 JavaTime 模块以支持 LocalDateTime。 */
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    /**
     * 解析 JSON。
     *
     * @param json 请求体
     * @return 请求对象
     * @throws Exception 解析失败
     */
    private PhotoUpdateReq parse(String json) throws Exception {
        return mapper.readValue(json, PhotoUpdateReq.class);
    }

    @Test
    @DisplayName("字段缺席 → 组件为 null（服务端按「不修改」处理）")
    void absentFieldStaysNull() throws Exception {
        PhotoUpdateReq req = parse("{\"title\":\"海边的一天\",\"tags\":[\"海边\"]}");

        assertThat(req.albumId())
                .as("缺省不能变成 PatchField.of(null)，否则「改标题」会把照片移出相册")
                .isNull();
        assertThat(req.title()).isEqualTo("海边的一天");
    }

    @Test
    @DisplayName("显式 null → 出现且值为 null（= 移到未分类）")
    void explicitNullIsPresent() throws Exception {
        PhotoUpdateReq req = parse("{\"albumId\":null}");

        assertThat(req.albumId()).isNotNull();
        assertThat(req.albumId().isPresent()).isTrue();
        assertThat(req.albumId().value()).isNull();
    }

    @Test
    @DisplayName("显式数字 → 出现且值可安全转成 Long（= 移到该相册）")
    void explicitNumberIsPresent() throws Exception {
        PhotoUpdateReq req = parse("{\"albumId\":12}");

        assertThat(req.albumId()).isNotNull();
        assertThat(req.albumId().isPresent()).isTrue();
        assertThat(req.albumId().value()).isEqualTo(12L);
    }

    @Test
    @DisplayName("三态互不干扰：同一个 body 里 title 为 null 与 albumId 缺席可以共存")
    void threeStatesAreIndependent() throws Exception {
        PhotoUpdateReq req = parse("{\"title\":null,\"albumId\":7}");

        assertThat(req.title()).isNull();
        assertThat(req.albumId().isPresent()).isTrue();
        assertThat(req.albumId().value()).isEqualTo(7L);
    }

    @Test
    @DisplayName("PatchField.absent()/of() 的语义与反序列化结果一致")
    void factoryMethodsMatch() {
        assertThat(PatchField.<Long>absent().isPresent()).isFalse();
        assertThat(PatchField.<Long>absent().orElse(99L)).isEqualTo(99L);
        assertThat(PatchField.of((Long) null).isPresent()).isTrue();
        assertThat(PatchField.of((Long) null).orElse(99L)).isNull();
        assertThat(PatchField.of(5L).orElse(99L)).isEqualTo(5L);
    }
}
