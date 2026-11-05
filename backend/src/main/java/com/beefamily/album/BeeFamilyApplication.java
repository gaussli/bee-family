package com.beefamily.album;

import jakarta.annotation.PostConstruct;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 蜜记 · Bee Family 后端入口。
 *
 * <p>单进程单体应用：内嵌 Tomcat + Spring MVC + Spring Data JPA + Flyway，无缓存 / 无队列 / 无反向代理。
 * 依赖方向严格单向 {@code controller -> service -> repository -> domain}，跨层反向引用一律视为缺陷。</p>
 *
 * <p>关闭 {@link UserDetailsServiceAutoConfiguration}：本项目认证完全由 JWT 过滤器链完成，
 * 不需要内存用户；保留它会在启动日志里打印一个随机生成的口令（违反「日志不出现任何口令」）。</p>
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan(basePackages = "com.beefamily.album")
public class BeeFamilyApplication {

    /** Asia/Shanghai 时区常量，见 docs/03-architecture.md §11.2。 */
    private static final String DEFAULT_TIME_ZONE = "Asia/Shanghai";

    /**
     * 启动入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(BeeFamilyApplication.class, args);
    }

    /**
     * 统一 JVM 默认时区。
     *
     * <p>原因：DDL 全部使用 {@code DATETIME}（不带时区），metadata-extractor 解析 EXIF 时间、
     * {@code Date -> LocalDateTime} 转换都依赖默认时区。显式设置一次可避免不同机器（CI / 家用机）
     * 因时区差异导致「拍摄时间漂移 8 小时」这类难以复现的问题。</p>
     */
    @PostConstruct
    void initTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(DEFAULT_TIME_ZONE));
    }
}
