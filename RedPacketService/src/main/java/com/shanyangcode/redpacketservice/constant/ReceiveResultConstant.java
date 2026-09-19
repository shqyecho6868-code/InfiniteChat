package com.shanyangcode.redpacketservice.constant;

/**
 * 红包领取结果常量类
 * <p>
 * 定义 Redis Lua 脚本的返回码
 *
 * @author shanyangcode
 */
public final class ReceiveResultConstant {

    private ReceiveResultConstant() {
        // 私有构造函数，防止实例化
    }

    // ==================== Lua 脚本返回码 ====================

    /**
     * 领取失败：用户已经领取过该红包
     * 注意：返回值的第二个元素为红包此刻的状态，取 RedPacketConstant.STATUS_NOT_COMPLETED(0)、STATUS_COMPLETED(1) 或 STATUS_EXPIRED(2)
     */
    public static final int ALREADY_RECEIVED = -1;

    /**
     * 领取失败：红包已进入终态（已领完 / 已过期）
     *
     * 注意：返回值的第二个元素即终态值，取 RedPacketConstant.STATUS_COMPLETED(1)
     * 或 STATUS_EXPIRED(2)，调用方无需查询数据库
     */
    public static final int EMPTY_POOL = -2;

    /**
     * 领取失败：Redis 中查无痕迹（金额池、领取记录、终态墓碑三者皆空）
     *
     * 注意：此状态需查询数据库确认红包的真实状态
     */
    public static final int NOT_FOUND = -3;

    /**
     * 领取失败：无权领取该红包
     *
     * 注意：返回值的第二个元素为拒绝原因，取 REASON_* 常量。
     * 该分支不产生任何 SQL、不写入 records，可被安全地高频拒绝
     */
    public static final int NO_PERMISSION = -4;

    // ==================== 拒绝原因 ====================

    /** 拒绝原因：单聊红包的发送者本人尝试自领 */
    public static final int REASON_SENDER_SELF_RECEIVE = 1;

    /** 拒绝原因：单聊红包的非指定收款人 */
    public static final int REASON_NOT_THE_RECEIVER = 2;

    /** 拒绝原因：不在发红包时的会话成员快照内 */
    public static final int REASON_NOT_IN_CONVERSATION = 3;

    // ==================== 红包完成标识 ====================

    /**
     * 完成标识：红包未领完
     */
    public static final int COMPLETION_FLAG_NOT_COMPLETED = 0;

    /**
     * 完成标识：红包已领完
     */
    public static final int COMPLETION_FLAG_COMPLETED = 1;

    // ==================== 辅助方法 ====================

    /**
     * 判断红包是否已领完
     *
     * @param completedFlag 完成标识
     * @return true if 红包已领完
     */
    public static boolean isCompleted(int completedFlag) {
        return completedFlag == COMPLETION_FLAG_COMPLETED;
    }

    /**
     * 将 Lua 返回的拒绝原因映射为业务异常枚举
     *
     * @param reason 拒绝原因，取 REASON_* 常量
     * @return 对应的校验错误枚举
     */
    public static com.shanyangcode.common.enums.ValidationError toValidationError(int reason) {
        return switch (reason) {
            case REASON_SENDER_SELF_RECEIVE -> com.shanyangcode.common.enums.ValidationError.REDPACKET_SENDER_CANNOT_RECEIVE;
            case REASON_NOT_THE_RECEIVER    -> com.shanyangcode.common.enums.ValidationError.REDPACKET_NOT_THE_RECEIVER;
            default                         -> com.shanyangcode.common.enums.ValidationError.REDPACKET_NOT_IN_CONVERSATION;
        };
    }

}