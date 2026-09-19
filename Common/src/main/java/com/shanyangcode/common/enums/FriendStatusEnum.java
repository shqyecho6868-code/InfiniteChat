package com.shanyangcode.common.enums;

/**
 * 好友状态枚举
 *
 * 用于消息/红包校验时判断好友关系状态
 * 统一 UserService、RealTimeService 的好友状态定义
 *
 * 数据库存储值说明：
 * - 0: 正常好友关系
 * - 1: 已拉黑
 * - 2: 已删除
 */
public enum FriendStatusEnum {

    /**
     * 好友状态：非好友关系
     * 特殊值，用于缓存标记"查询不到好友记录"的情况
     */
    NON_FRIEND(-1, "非好友"),

    /**
     * 好友状态：正常好友关系
     */
    NORMAL(0, "好友"),

    /**
     * 好友状态：已拉黑
     */
    BLOCKED(1, "拉黑"),

    /**
     * 好友状态：已删除
     */
    DELETED(2, "删除");

    private final int code;
    private final String description;

    FriendStatusEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }



}