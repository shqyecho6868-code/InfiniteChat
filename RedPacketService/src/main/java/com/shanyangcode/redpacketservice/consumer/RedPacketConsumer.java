package com.shanyangcode.redpacketservice.consumer;

import java.util.Map;

import com.shanyangcode.redpacketservice.config.KafkaConfig;
import com.shanyangcode.redpacketservice.constant.KafkaConstant;
import com.shanyangcode.redpacketservice.model.dto.RedPacketReceiveEvent;
import com.shanyangcode.redpacketservice.service.RedPacketService;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 红包Kafka消费者
 * <p>
 * 负责处理红包领取记录和完成事件
 *
 * @author shanyangcode
 */
@Component
@Slf4j
public class RedPacketConsumer {

    @Autowired
    private RedPacketService redPacketService;

    /**
     * 消费红包领取事件
     * <p>
     * Topic: topic-redpacket-receive
     * <p>
     * 消息体对应 {@link RedPacketReceiveEvent}，由 RedPacketServiceImpl#receiveRedPacket 生产：
     * <pre>
     * {
     *     "userId": 1829109273758666752,
     *     "redPacketId": 1847283058810687488,
     *     "receivedAmount": 100,
     *     "receiveTime": 1678886400000
     * }
     * </pre>
     * <p>
     * 处理逻辑：
     * 1. 插入领取记录到数据库
     * 2. 增加用户余额
     * 3. 记录余额变动日志
     *
     * @param message Kafka消息
     */
    @KafkaListener(
            topics = KafkaConfig.TOPIC_REDPACKET_RECEIVE,
            groupId = KafkaConstant.GROUP_RECEIVE_HANDLER,
            concurrency = KafkaConstant.DEFAULT_CONCURRENCY
    )
    public void handleRedPacketReceive(String message) {
        log.info("收到红包领取事件: {}", message);

        // 1. 反序列化：报文格式错误属于脏消息，重试也不会成功，直接丢弃避免阻塞分区
        RedPacketReceiveEvent event;
        try {
            event = JSONUtil.toBean(message, RedPacketReceiveEvent.class);
        } catch (Exception e) {
            log.error("红包领取事件反序列化失败，丢弃该消息: {}", message, e);
            return;
        }

        // 2. 必填字段校验，防止生产者漏传字段导致后续 NPE
        if (event == null
                || event.getUserId() == null
                || event.getRedPacketId() == null
                || event.getReceivedAmount() == null) {
            log.error("红包领取事件字段缺失，丢弃该消息: {}", message);
            return;
        }

        Long userId = event.getUserId();
        Long redPacketId = event.getRedPacketId();
        Long receivedAmount = event.getReceivedAmount();
        // 领取时间非关键字段，缺失时用当前时间兜底
        Long receiveTime = event.getReceiveTime() != null
                ? event.getReceiveTime()
                : System.currentTimeMillis();

        // 3. 业务处理：这里的失败（如数据库抖动）才是可重试的
        try {
            redPacketService.handleRedPacketReceive(userId, redPacketId, receivedAmount, receiveTime);
            log.info("红包领取事件处理成功，红包ID: {}, 用户ID: {}", redPacketId, userId);
        } catch (Exception e) {
            log.error("处理红包领取事件失败，红包ID: {}, 用户ID: {}", redPacketId, userId, e);
            // 如需交给 Kafka 重试，在此处 throw new RuntimeException(e);
        }
    }

    /**
     * 消费红包领完事件
     * <p>
     * Topic: topic-redpacket-completed
     * <p>
     * 消息格式：
     * <pre>
     * {
     *     "redPacketId": 1847283058810687488
     * }
     * </pre>
     * <p>
     * 处理逻辑：
     * 1. 更新红包状态为"已领取完"
     * 2. 清理Redis缓存（写终态墓碑、删除金额池、领取记录转 TTL、移出过期ZSET）
     *
     * @param message Kafka消息
     */
    @KafkaListener(
            topics = KafkaConfig.TOPIC_REDPACKET_COMPLETED,
            groupId = KafkaConstant.GROUP_COMPLETED_HANDLER,
            concurrency = KafkaConstant.DEFAULT_CONCURRENCY
    )
    public void handleRedPacketCompleted(String message) {
        try {
            log.info("收到红包领完事件: {}", message);

            // 解析消息
            Map<String, Object> eventData = JSONUtil.toBean(message, Map.class);
            Long redPacketId = Long.parseLong(eventData.get("redPacketId").toString());

            // 调用服务层处理领完事件
            redPacketService.handleRedPacketCompleted(redPacketId);

            log.info("红包领完事件处理成功，红包ID: {}", redPacketId);

        } catch (Exception e) {
            log.error("处理红包领完事件失败: {}", message, e);
        }
    }
}