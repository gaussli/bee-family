package com.beefamily.album.config;

import com.beefamily.album.support.DateTimes;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 全局配置：{@code LocalDateTime} / {@code LocalDate} 的统一格式。
 *
 * <p>为什么不用 {@code spring.jackson.date-format}：该属性只对 {@code java.util.Date} 生效，
 * {@code java.time} 类型必须显式注册序列化器（docs/03-architecture.md §11.2）。</p>
 *
 * <p>BigDecimal 不注册自定义序列化器：默认即输出 JSON 数字且保留 DB scale（§11.3），
 * 严禁在此处调用 {@code stripTrailingZeros}。</p>
 */
@Configuration
public class JacksonConfig {

    /**
     * 注册 java.time 序列化 / 反序列化格式。
     *
     * @return 定制器
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer javaTimeCustomizer() {
        return builder -> {
            JavaTimeModule module = new JavaTimeModule();
            module.addSerializer(java.time.LocalDateTime.class,
                    new LocalDateTimeSerializer(DateTimes.FMT_DATE_TIME));
            module.addDeserializer(java.time.LocalDateTime.class,
                    new LocalDateTimeDeserializer(DateTimes.FMT_DATE_TIME));
            module.addSerializer(java.time.LocalDate.class, new LocalDateSerializer(DateTimes.FMT_DATE));
            module.addDeserializer(java.time.LocalDate.class, new LocalDateDeserializer(DateTimes.FMT_DATE));
            builder.modulesToInstall(module);
            // 禁止把日期序列化为时间戳数组
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        };
    }
}
