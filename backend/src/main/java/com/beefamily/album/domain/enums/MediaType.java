package com.beefamily.album.domain.enums;

/**
 * 媒体类型（P2 视频预留）。
 *
 * <p>本期只写入 {@link #photo}；{@link #video} 仅为 DDL CHECK 与后续演进保留。</p>
 */
public enum MediaType {

    /** 照片。 */
    photo,

    /** 视频（本期不实现）。 */
    video
}
