package com.beefamily.album.dto.photo;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * PATCH 三态字段包装：把「字段未出现在请求体里」与「字段显式传了 null」区分开。
 *
 * <p><b>为什么需要它</b>：{@code PATCH /api/photos/{id}} 的 {@code albumId} 有两种完全不同的
 * 业务含义 —— 缺席表示「这次编辑与相册无关」（改标题 / 改标签），显式 {@code null} 表示
 * 「把照片移出相册，落到未分类」。Java {@code record} 的 {@code Long albumId} 把两者都表示成
 * {@code null}，服务端只能二选一，于是「改个标题」会把照片静默踢出相册（QA P1-①）。</p>
 *
 * <p><b>为什么不引进 {@code JsonNullable}</b>：{@code §11.4} 明确否决了「为 PATCH 引入
 * {@code JsonNullable} 依赖」。这个类用 JDK + Jackson 自带能力（{@link JsonDeserialize} +
 * {@code ContextualDeserializer}）实现同样的语义，零新增依赖，且只对真正需要三态的字段生效。</p>
 *
 * <p><b>三态判定规则</b>（反序列化由 {@link PatchFieldDeserializer} 完成）：</p>
 * <ul>
 *   <li>请求体里没有该字段 → 该组件为 {@code null}（或 {@link #absent()}），{@link #isPresent()} 为 false；</li>
 *   <li>请求体里写了 {@code "albumId": null} → {@link #isPresent()} 为 true、{@link #value()} 为 null；</li>
 *   <li>请求体里写了 {@code "albumId": 12} → {@link #isPresent()} 为 true、{@link #value()} 为 12。</li>
 * </ul>
 *
 * @param <T> 字段真实类型（如 {@code Long}）
 */
@JsonDeserialize(using = PatchFieldDeserializer.class)
public final class PatchField<T> {

    /** 共享的「缺席」实例（无状态，可安全复用）。 */
    private static final PatchField<?> ABSENT = new PatchField<>(false, null);

    /** 字段是否出现在请求体里。 */
    private final boolean present;

    /** 字段值（{@link #present} 为 true 时才有意义，允许为 null）。 */
    private final T value;

    /**
     * 构造。
     *
     * @param present 是否出现
     * @param value   值
     */
    private PatchField(boolean present, T value) {
        this.present = present;
        this.value = value;
    }

    /**
     * 「字段缺席」工厂方法。
     *
     * @param <T> 字段类型
     * @return 缺席实例
     */
    @SuppressWarnings("unchecked")
    public static <T> PatchField<T> absent() {
        return (PatchField<T>) ABSENT;
    }

    /**
     * 「字段出现」工厂方法（值允许为 null，表示显式置空）。
     *
     * @param value 字段值
     * @param <T>   字段类型
     * @return 出现实例
     */
    public static <T> PatchField<T> of(T value) {
        return new PatchField<>(true, value);
    }

    /**
     * 字段是否出现在请求体里。
     *
     * @return true 表示调用方明确表达了对该字段的意图（可能是设为 null）
     */
    public boolean isPresent() {
        return present;
    }

    /**
     * 字段值。
     *
     * @return 值，可能为 null
     */
    public T value() {
        return value;
    }

    /**
     * 取值，缺席时返回兜底值。
     *
     * @param fallback 兜底值
     * @return 字段值或兜底值
     */
    public T orElse(T fallback) {
        return present ? value : fallback;
    }

    /**
     * 便于日志排查的字符串形式（不输出敏感数据）。
     *
     * @return 形如 {@code absent} / {@code present(null)} / {@code present(12)}
     */
    @Override
    public String toString() {
        return present ? "present(" + value + ")" : "absent";
    }
}
