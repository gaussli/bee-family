package com.beefamily.album.dto.album;

/**
 * 相册删除时照片的处置策略。
 *
 * <p>放在 dto 包（而非 domain/enums）：它只服务于接口参数，不参与持久化。</p>
 */
public enum PhotoStrategy {

    /** 照片移到未分类（默认，推荐）。 */
    move,

    /** 照片一并删除（磁盘文件在事务提交后删除）。 */
    delete
}
