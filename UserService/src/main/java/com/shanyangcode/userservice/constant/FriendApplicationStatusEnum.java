// FriendApplicationStatusEnum.java
package com.shanyangcode.userservice.constant;

/**
 * 好友申请状态枚举
 *
 * 数据库存储值说明：
 * - 0: 未读（接收者未查看）
 * - 1: 通过（接收者同意好友申请）
 * - 2: 拒绝（接收者拒绝好友申请）
 * - 3: 已读（接收者已查看但未处理）
 * - 4: 过期（申请超时未处理）
 */
public enum FriendApplicationStatusEnum {

    UNREAD(0, "未读"),
    ACCEPTED(1, "通过"),
    REJECTED(2, "拒绝"),
    READ(3, "已读"),
    EXPIRED(4, "过期");

    private final int code;
    private final String description;

    FriendApplicationStatusEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() { return code; }
    public String getDescription() { return description; }

    public static FriendApplicationStatusEnum fromCode(int code) {
        for (FriendApplicationStatusEnum status : FriendApplicationStatusEnum.values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("无效的好友申请状态码: " + code);
    }
}