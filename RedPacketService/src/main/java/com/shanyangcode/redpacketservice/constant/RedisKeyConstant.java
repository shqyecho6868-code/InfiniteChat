package com.shanyangcode.redpacketservice.constant;

/**
 * Redis Key 常量类
 * <p>
 * 定义红包模块使用的 Redis Key 模板和前缀
 *
 * @author shanyangcode
 */
public final class RedisKeyConstant {

    private RedisKeyConstant() {
        // 私有构造函数，防止实例化
    }

    // ==================== 红包 Redis Key 模板 ====================

    /**
     * 红包金额池 Key 模板
     * <p>
     * 数据结构：List
     * <p>
     * 用法：String.format(POOL_TEMPLATE, redPacketId)
     * <p>
     * 示例：redpacket:1001:pool
     */
    public static final String POOL_TEMPLATE = "redpacket:%s:pool";

    /**
     * 红包领取记录 Key 模板
     * <p>
     * 数据结构：Hash (userId -> amount)
     * <p>
     * 用法：String.format(RECORDS_TEMPLATE, redPacketId)
     * <p>
     * 示例：redpacket:1001:records
     */
    public static final String RECORDS_TEMPLATE = "redpacket:%s:records";

    /**
     * 红包终态墓碑 Key 模板
     * <p>
     * 数据结构：String，值为 RedPacketConstant.STATUS_COMPLETED(1) 或 STATUS_EXPIRED(2)
     * <p>
     * 用法：String.format(STATE_TEMPLATE, redPacketId)
     * <p>
     * 示例：redpacket:1001:state
     */
    public static final String STATE_TEMPLATE = "redpacket:%s:state";

    /**
     * 红包过期任务队列 Key
     * <p>
     * 数据结构：ZSet (member=redPacketId, score=expireTimestamp)
     * <p>
     * 用于时间轮算法的延迟任务调度
     */
    public static final String EXPIRE_ZSET = "redpacket-expire-zset";

    /**
     * 红包领取授权 Key 模板
     * <p>
     * 数据结构：Hash，字段 sessionType / senderId / receiverId（receiverId 仅单聊写入）
     * <p>
     * 与 pool 同生同 TTL（25 小时）；终态清理不删它，靠 TTL 自然消失
     * <p>
     * 用法：String.format(GRANT_TEMPLATE, redPacketId)
     * <p>
     * 示例：redpacket:1001:grant
     */
    public static final String GRANT_TEMPLATE = "redpacket:%s:grant";

    /**
     * 红包可领成员快照 Key 模板
     * <p>
     * 数据结构：Set&lt;userId&gt;，「发红包那一刻」的群成员，写入后永不更新
     * <p>
     * 与 pool 同生同 TTL（25 小时）；终态清理不删它，靠 TTL 自然消失
     * <p>
     * 用法：String.format(ALLOW_TEMPLATE, redPacketId)
     * <p>
     * 示例：redpacket:1001:allow
     */
    public static final String ALLOW_TEMPLATE = "redpacket:%s:allow";

    // ==================== 辅助方法 ====================

    /**
     * 生成红包金额池 Key
     *
     * @param redPacketId 红包ID
     * @return Redis Key
     */
    public static String getPoolKey(Long redPacketId) {
        return String.format(POOL_TEMPLATE, redPacketId);
    }

    /**
     * 生成红包领取记录 Key
     *
     * @param redPacketId 红包ID
     * @return Redis Key
     */
    public static String getRecordsKey(Long redPacketId) {
        return String.format(RECORDS_TEMPLATE, redPacketId);
    }

    /**
     * 生成红包终态墓碑 Key
     *
     * @param redPacketId 红包ID
     * @return Redis Key
     */
    public static String getStateKey(Long redPacketId) {
        return String.format(STATE_TEMPLATE, redPacketId);
    }

    /**
     * 生成红包领取授权 Key
     *
     * @param redPacketId 红包ID
     * @return Redis Key
     */
    public static String getGrantKey(Long redPacketId) {
        return String.format(GRANT_TEMPLATE, redPacketId);
    }

    /**
     * 生成红包可领成员快照 Key
     *
     * @param redPacketId 红包ID
     * @return Redis Key
     */
    public static String getAllowKey(Long redPacketId) {
        return String.format(ALLOW_TEMPLATE, redPacketId);
    }


}