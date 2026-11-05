package com.beefamily.album.service;

import com.beefamily.album.support.DateTimes;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Optional;
import java.util.TimeZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * EXIF 服务：读取拍摄时间（{@code DateTimeOriginal}）。
 *
 * <p>业务规则（PRD Q5）：有 EXIF 用 EXIF，缺失回退为上传时间。因此本类只负责「读」，
 * 回退逻辑放在 {@link PhotoService}，避免「文件无关的时间决策」散落在解析器里。</p>
 *
 * <p>容错：手机照片的 EXIF 常常损坏或时区标记残缺，解析失败一律返回 {@link Optional#empty()}，
 * 绝不让 EXIF 问题阻断上传（宁可时间不精确，也不能传不上去）。</p>
 */
@Service
public class ExifService {

    private static final Logger log = LoggerFactory.getLogger(ExifService.class);

    /** 与「今天」相差超过该天数的拍摄时间视为相机时钟错误。 */
    private static final long MAX_FUTURE_DAYS = 1L;

    private static final TimeZone TIME_ZONE = TimeZone.getTimeZone(DateTimes.ZONE);

    /**
     * 读取拍摄时间。
     *
     * @param bytes 文件内容
     * @return 拍摄时间，解析不到时为空
     */
    public Optional<LocalDateTime> readTakenAt(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return Optional.empty();
        }
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(bytes));
            Date takenAt = readOriginalDate(metadata);
            if (takenAt == null) {
                return Optional.empty();
            }
            LocalDateTime dateTime = LocalDateTime.ofInstant(takenAt.toInstant(), DateTimes.ZONE);
            if (dateTime.isAfter(DateTimes.now().plusDays(MAX_FUTURE_DAYS))) {
                // 相机时钟错乱（例如被重置为 2030 年）：不采信，回退为上传时间
                log.debug("ignore future exif time {}", dateTime);
                return Optional.empty();
            }
            return Optional.of(dateTime);
        } catch (Exception ex) {
            log.debug("read exif failed: {}", ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /**
     * 从元数据中提取原始拍摄时间：优先 {@code DateTimeOriginal}，其次 {@code DateTime}。
     *
     * @param metadata 元数据
     * @return 时间，未找到返回 null
     */
    private Date readOriginalDate(Metadata metadata) {
        ExifSubIFDDirectory subIfd = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
        if (subIfd != null) {
            Date date = subIfd.getDateOriginal(TIME_ZONE);
            if (date != null) {
                return date;
            }
        }
        ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
        if (ifd0 != null) {
            return ifd0.getDate(ExifIFD0Directory.TAG_DATETIME, TIME_ZONE);
        }
        return null;
    }
}
