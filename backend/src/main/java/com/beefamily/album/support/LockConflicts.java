package com.beefamily.album.support;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.PessimisticLockingFailureException;

/**
 * 死锁/锁等待失败的一次性重试工具。
 *
 * <p><b>为什么放在 controller 层调用</b>：InnoDB 报 {@code Deadlock found} 时会把**整个事务**回滚，
 * 事务内的任何重试都跑在一个已经废掉的事务里（Spring 还会把它标成 rollback-only）。
 * 所以要重试就必须重开一个事务 —— 而 service 方法的 {@code @Transactional} 就是事务边界，
 * 在它外面再调一次自然就是新事务。Controller 正好是这个边界外侧，且能让「重试」这件事对
 * 所有写路径保持一致。</p>
 *
 * <p><b>为什么不是无脑重试</b>：只有 {@link PessimisticLockingFailureException}
 * （{@code DeadlockLoserDataAccessException} / {@code CannotAcquireLockException} 的共同父类）
 * 才重试，且只重试一次 —— 这类异常是数据库主动选出的「牺牲者」，重跑通常立刻成功；
 * 业务异常、约束冲突重试没有意义，重试多了还会放大故障。</p>
 *
 * <p><b>为什么退避 20–50ms</b>：死锁的对手事务此刻正在提交，立即重试有概率再次撞上同一把锁；
 * 一个抖动过的短退避足以错开，又不会让用户感知到延迟。用随机而不是固定值，避免多个被牺牲的
 * 请求在同一个时间点再次同步冲撞（惊群）。</p>
 */
public final class LockConflicts {

    private static final Logger log = LoggerFactory.getLogger(LockConflicts.class);

    /** 退避下限（毫秒）。 */
    private static final int BACKOFF_MIN_MS = 20;

    /** 退避上限（毫秒）。 */
    private static final int BACKOFF_MAX_MS = 50;

    /**
     * 工具类不允许实例化。
     */
    private LockConflicts() {
    }

    /**
     * 执行一次；遇到锁冲突退避后重试一次。
     *
     * @param operation 操作名（进日志，便于定位是哪条写路径在抖）
     * @param action    真正的业务动作（必须自带事务边界，即一次调用 = 一个事务）
     * @param <T>       返回值类型
     * @return 业务结果
     */
    public static <T> T retryOnce(String operation, Supplier<T> action) {
        try {
            return action.get();
        } catch (PessimisticLockingFailureException ex) {
            log.warn("{} lock conflict, retrying once cause={}", operation, ex.getClass().getSimpleName());
            backoff();
            return action.get();
        }
    }

    /**
     * 无返回值的版本。
     *
     * @param operation 操作名
     * @param action    业务动作
     */
    public static void retryOnce(String operation, Runnable action) {
        try {
            action.run();
        } catch (PessimisticLockingFailureException ex) {
            log.warn("{} lock conflict, retrying once cause={}", operation, ex.getClass().getSimpleName());
            backoff();
            action.run();
        }
    }

    /**
     * 随机退避 20–50ms。
     */
    private static void backoff() {
        int millis = BACKOFF_MIN_MS + ThreadLocalRandom.current().nextInt(BACKOFF_MAX_MS - BACKOFF_MIN_MS + 1);
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            // 恢复中断标记后继续重试：被中断不代表不该把这次请求做完
            Thread.currentThread().interrupt();
        }
    }
}
