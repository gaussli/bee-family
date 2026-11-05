package com.beefamily.album.dto.photo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 更新照片请求（PATCH）。
 *
 * <p><b>字段语义（§11.4「全量提交」+ 两处明确例外）</b>：</p>
 * <ul>
 *   <li>{@code title} / {@code description} / {@code locationName}：全量提交，{@code null} 即置空；</li>
 *   <li>{@code takenAt}：{@code null} 表示不修改（列是 {@code NOT NULL}，置空会写非法值）；</li>
 *   <li>{@code tags}：**完整集合语义** —— 传 {@code []} 清空、传 {@code null} 表示不修改标签；</li>
 *   <li>{@code albumId}：三态（{@link PatchField}）—— **缺席 = 不修改**、{@code null} = 移到未分类、
 *       数字 = 移到该相册。前端编辑标题 / 标签时不会带这个字段，绝不能把「缺席」当成「移出相册」。</li>
 * </ul>
 *
 * <h2>为什么这里没有把 DTO 改成 class（对「DTO 用 record」约定的一次有意例外，但只例外到字段粒度）</h2>
 *
 * <p>本项目的 DTO 约定是 {@code record}（不可变、无样板代码）。但 {@code record} 的组件无法区分
 * 「JSON 里没这个键」与「这个键的值是 null」——两者都会让组件变成 {@code null}。为了让
 * {@code albumId} 具备三态，可选方案有三个，这里的选择与理由如下：</p>
 *
 * <ol>
 *   <li><b>把本类改成 class + {@code @JsonSetter("albumId")} 记录 {@code albumIdPresent} 标志</b>
 *       （架构师建议的 A 方案）：可行，但要引入一个可变字段、一个非规范构造器，以及「先 set 再读」
 *       的两段式状态；{@code record} 的不可变性和「一行构造器即契约」全部丢失，而且 present 标志
 *       会成为一个可以被漏设的隐式状态。**不选**。</li>
 *   <li><b>引入 {@code JsonNullable}（jackson-databind-nullable）</b>：{@code §11.4} 明确否决过
 *       「为了 PATCH 三态引入额外依赖」。**不选**。</li>
 *   <li><b>保留 {@code record}，只把需要三态的那一个字段包成 {@link PatchField}，用自定义
 *       {@code @JsonDeserialize} 在反序列化阶段把「键是否出现」记录下来</b>（本次实现）：
 *       对外仍然是 {@code record}，其余字段照旧享受 Bean Validation 与「全量提交」语义；
 *       代价只是多了一个 30 行的值对象，且这个值对象可以复用到将来任何需要三态的字段上。
 *       <b>选它</b>。</li>
 * </ol>
 *
 * <p>换句话说：**例外不在「DTO 是不是 record」这一层，而在「某个字段是不是裸类型」这一层** ——
 * 只有 {@code albumId} 一个字段被包起来，契约形状、校验注解、字段顺序全部不变。</p>
 *
 * @param title        标题 ≤80
 * @param description  描述 ≤500
 * @param takenAt      拍摄时间（null 表示不使用自定义值，保持原值）
 * @param locationName 地点 ≤100
 * @param albumId      目标相册：缺席 = 不修改，null = 移出相册（未分类），数字 = 移到该相册
 * @param tags         完整标签名集合
 */
public record PhotoUpdateReq(
        @Size(max = 80, message = "标题不能超过 80 个字符")
        String title,

        @Size(max = 500, message = "描述不能超过 500 个字符")
        String description,

        LocalDateTime takenAt,

        @Size(max = 100, message = "地点不能超过 100 个字符")
        String locationName,

        PatchField<Long> albumId,

        List<@NotBlank(message = "标签不能为空") @Size(max = 20, message = "单个标签不能超过 20 个字符") String> tags) {
}
