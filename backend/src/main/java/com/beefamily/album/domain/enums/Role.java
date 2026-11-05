package com.beefamily.album.domain.enums;

/**
 * 家庭成员角色。
 *
 * <p>枚举常量刻意使用小写：DDL 以 {@code VARCHAR(10)} + CHECK 存 {@code 'owner'/'member'}，
 * 实体的 {@code @Enumerated(STRING)} 与 Jackson 的默认序列化都直接得到小写字符串，
 * 无需任何 Jackson 注解（实体禁止携带 JSON 注解，docs §3.2）。</p>
 */
public enum Role {

    /** 家庭创建者。 */
    owner,

    /** 普通成员。 */
    member
}
