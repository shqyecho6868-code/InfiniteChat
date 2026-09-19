package com.shanyangcode.redpacketservice.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 红包领取事件（Kafka 消息体）
 * 生产者：RedPacketServiceImpl#receiveRedPacket
 * 消费者：RedPacketConsumer#handleRedPacketReceive
 */
@Data
@Builder
@NoArgsConstructor   // JSONUtil.toBean 反序列化依赖无参构造 + setter
@AllArgsConstructor  // 配合 @Builder
public class RedPacketReceiveEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private Long userId;

    /** 红包 ID */
    private Long redPacketId;

    /** 领取金额（单位：分） */
    private Long receivedAmount;

    /** 领取时间（毫秒时间戳） */
    private Long receiveTime;
}