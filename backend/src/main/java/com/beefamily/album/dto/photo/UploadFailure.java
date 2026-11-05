package com.beefamily.album.dto.photo;

/**
 * 单个文件的上传失败明细。
 *
 * <p>{@code fileName} 仅用于前端定位队列项，**不入库、不落盘**（§7.6「文件名」）。</p>
 *
 * @param fileName 客户端文件名（仅回显）
 * @param code     错误码
 * @param message  面向用户的提示
 */
public record UploadFailure(String fileName, int code, String message) {
}
