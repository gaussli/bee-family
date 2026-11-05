package com.beefamily.album.dto.photo;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import java.io.IOException;

/**
 * {@link PatchField} 的反序列化器：把「JSON 里出现了这个键」这件事记录下来。
 *
 * <p><b>核心机制</b>：Jackson 只在键**真正出现**时才会调用属性的 deserializer；
 * 键缺席时组件保持 {@code null}。因此「被调用 → {@link PatchField#of(Object)}」
 * 「没被调用 → {@link PatchField#absent()}」就构成了三态判定，不需要把整个请求体降级成
 * {@code Map} 解析，也不影响其余字段的 {@code @Size} 等 Bean Validation。</p>
 *
 * <p><b>显式 null 的两条路径都要接住</b>：普通 bean 属性走 {@link #getNullValue}，
 * 而 record 的构造器属性（{@code PropertyBasedCreator}）会把 {@code VALUE_NULL} 直接送进
 * {@link #deserialize}，所以两处都返回「出现 + 值为 null」。</p>
 */
public final class PatchFieldDeserializer extends JsonDeserializer<PatchField<?>>
        implements ContextualDeserializer {

    /** 被包装的值类型；为 null 时退化为 {@code Object}（由调用方做数值兜底转换）。 */
    private final JavaType valueType;

    /**
     * 默认构造（Jackson 实例化入口）。
     */
    public PatchFieldDeserializer() {
        this(null);
    }

    /**
     * 构造。
     *
     * @param valueType 被包装的值类型
     */
    private PatchFieldDeserializer(JavaType valueType) {
        this.valueType = valueType;
    }

    /**
     * 取出 {@code PatchField<T>} 里的 {@code T}，交给值反序列化器处理。
     *
     * @param ctxt     反序列化上下文
     * @param property 当前属性（record 组件也会传入）
     * @return 带具体值类型的反序列化器
     */
    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
        JavaType wrapper = property == null ? ctxt.getContextualType() : property.getType();
        JavaType contained = wrapper != null && wrapper.containedTypeCount() > 0
                ? wrapper.containedType(0)
                : ctxt.constructType(Object.class);
        return new PatchFieldDeserializer(contained);
    }

    /**
     * 反序列化：键出现即视为 present，值为 JSON null 时 present 且 value 为 null。
     *
     * @param parser JSON 解析器
     * @param ctxt   反序列化上下文
     * @return 三态字段
     * @throws IOException 解析失败
     */
    @Override
    public PatchField<?> deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NULL) {
            return PatchField.of(null);
        }
        return PatchField.of(ctxt.readValue(parser, valueType));
    }

    /**
     * 显式 null 的兜底路径（bean 属性走这里）。
     *
     * @param ctxt 反序列化上下文
     * @return 「出现 + 值为 null」
     */
    @Override
    public PatchField<?> getNullValue(DeserializationContext ctxt) {
        return PatchField.of(null);
    }

    /**
     * 「字段缺席」的兜底路径。
     *
     * <p><b>这条覆盖是必需的不是可选的</b>：record 的构造器属性缺省时，Jackson 2.13+ 会调用
     * {@code getAbsentValue}（默认实现直接委托给 {@link #getNullValue}）。若不覆盖，
     * 「字段没传」会被当成「传了 null」，三态退化成两态 —— QA P1-① 就是这个形态。</p>
     *
     * @param ctxt 反序列化上下文
     * @return {@code null}：组件保持 null，调用方按「未出现」处理
     */
    @Override
    public Object getAbsentValue(DeserializationContext ctxt) {
        return null;
    }
}
