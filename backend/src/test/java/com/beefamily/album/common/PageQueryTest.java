package com.beefamily.album.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link PageQuery} 边界测试（任务点 5）。
 *
 * <p>分页参数是唯一由客户端直接控制、又直接拼进 SQL OFFSET/LIMIT 的输入：
 * 负数 page 会让 OFFSET 变负（MySQL 报语法错），超大 size 会让一次查询拖死进程。</p>
 */
class PageQueryTest {

    @Test
    @DisplayName("page 为负数归一化为 0（否则 SQL OFFSET 为负会直接报错）")
    void negativePageClampedToZero() {
        assertThat(new PageQuery(-1, 24).normalize(24)).isEqualTo(new PageQuery(0, 24));
        assertThat(new PageQuery(-999, 24).normalize(24).offset()).isZero();
    }

    @Test
    @DisplayName("size <= 0 回退到默认值")
    void nonPositiveSizeFallsBackToDefault() {
        assertThat(new PageQuery(0, 0).normalize(60)).isEqualTo(new PageQuery(0, 60));
        assertThat(new PageQuery(0, -5).normalize(60)).isEqualTo(new PageQuery(0, 60));
    }

    @Test
    @DisplayName("size 超过上限被截断到 MAX_SIZE（100）")
    void oversizedSizeTruncated() {
        assertThat(new PageQuery(0, 500).normalize(24)).isEqualTo(new PageQuery(0, PageQuery.MAX_SIZE));
        assertThat(new PageQuery(0, Integer.MAX_VALUE).normalize(24).size()).isEqualTo(100);
    }

    @Test
    @DisplayName("size 恰好等于上限 / 上限内不截断")
    void sizeBoundaries() {
        assertThat(new PageQuery(0, 100).normalize(24).size()).isEqualTo(100);
        assertThat(new PageQuery(0, 99).normalize(24).size()).isEqualTo(99);
        assertThat(new PageQuery(0, 1).normalize(24).size()).isEqualTo(1);
    }

    @Test
    @DisplayName("offset = page * size")
    void offsetComputation() {
        assertThat(new PageQuery(0, 24).offset()).isZero();
        assertThat(new PageQuery(2, 24).offset()).isEqualTo(48);
        assertThat(new PageQuery(3, 60).offset()).isEqualTo(180);
    }

    @Test
    @DisplayName("of(...) 等价于构造后 normalize")
    void factoryNormalises() {
        assertThat(PageQuery.of(-2, 0, PageQuery.DEFAULT_TIMELINE_SIZE))
                .isEqualTo(new PageQuery(0, PageQuery.DEFAULT_TIMELINE_SIZE));
        assertThat(PageQuery.of(1, 1000, PageQuery.DEFAULT_PHOTO_SIZE))
                .isEqualTo(new PageQuery(1, PageQuery.MAX_SIZE));
    }

    @Test
    @DisplayName("三个默认每页条数常量与文档一致（时间轴 24 / 网格 60 / 列表 20）")
    void defaultSizes() {
        assertThat(PageQuery.DEFAULT_TIMELINE_SIZE).isEqualTo(24);
        assertThat(PageQuery.DEFAULT_PHOTO_SIZE).isEqualTo(60);
        assertThat(PageQuery.DEFAULT_LIST_SIZE).isEqualTo(20);
        assertThat(PageQuery.MAX_SIZE).isEqualTo(100);
    }
}
