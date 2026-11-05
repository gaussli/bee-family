package com.beefamily.album.domain.enums;

/**
 * 宝宝性别。
 *
 * <p>小写常量与 DDL 的 CHECK ('male','female','unknown') 对齐，序列化为小写字符串。</p>
 */
public enum Gender {

    /** 男。 */
    male,

    /** 女。 */
    female,

    /** 未知 / 不透露。 */
    unknown
}
