package com.shanyangcode.userservice.model.dto;

import java.io.Serializable;

import lombok.Data;

/**
 * 好友申请过期事件DTO
 *
 * 功能说明：
 * - 定时任务扫描到过期的好友申请时发送到Kafka
 * - 消费者接收后执行过期逻辑（更新数据库状态）
 *
 * Topic: friend-request-expiration-topic
 */
@Data
public class FriendRequestExpirationEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 好友申请ID
     */
    private Long applyFriendId;

    /**
     * 过期时间戳（毫秒）
     */
    private Long expireTime;
}