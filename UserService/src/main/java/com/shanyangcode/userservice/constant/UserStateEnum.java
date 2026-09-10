package com.shanyangcode.userservice.constant;

/**
 * 用户状态枚举
 *
 * 数据库存储值说明：
 * - 0: 正常
 * - 1: 封禁
 * - 2: 注销
 */
public enum UserStateEnum {

    /**
     * 用户状态：正常
     */
    NORMAL(0, "正常"),

    /**
     * 用户状态：封禁
     */
    BANNED(1, "封禁"),

    /**
     * 用户状态：注销
     */
    CANCELLED(2, "注销");

    private final int code;
    private final String description;

    UserStateEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据状态码获取枚举值
     *
     * @param code 状态码
     * @return 对应的枚举值
     * @throws IllegalArgumentException 如果状态码无效
     */
    public static UserStateEnum fromCode(int code) {
        for (UserStateEnum state : UserStateEnum.values()) {
            if (state.code == code) {
                return state;
            }
        }
        throw new IllegalArgumentException("无效的用户状态码: " + code);
    }
}