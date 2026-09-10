// RealTimeService - SystemNotificationConsumer.java
package com.shanyangcode.realtimeservice.consumer;

import cn.hutool.json.JSONUtil;
import com.shanyangcode.common.constant.CommonConstant;
import com.shanyangcode.realtimeservice.websocket.ChannelManager;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 系统通知消息消费者
 *
 * 消息类型：
 * - 101：收到好友申请通知
 * - 102：新会话创建通知
 * - 103：新群聊会话创建通知
 */
@Slf4j
@Component
public class SystemNotificationConsumer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    public SystemNotificationConsumer(KafkaTemplate<String, String> kafkaTemplate,
                                      StringRedisTemplate stringRedisTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @KafkaListener(
            topics = CommonConstant.KAFKA_SYSTEM_NOTIFICATION_TOPIC,
            groupId = "system-notification-consumer-group",
            concurrency = "3"
    )
    public void consumeSystemNotification(String message) {
        try {
            log.debug("收到系统通知消息: {}", message);

            // 1. 解析消息获取接收者ID和messageId（用于日志）
            Map<String, Object> notificationMap = JSONUtil.toBean(message, Map.class);
            String messageId = (String) notificationMap.get("messageId");
            Integer type = (Integer) notificationMap.get("type");
            Long receiverId = Long.parseLong(notificationMap.get("receiverId").toString());

            // 2. 获取用户的WebSocket Channel
            Channel channel = ChannelManager.getChannelByUserId(String.valueOf(receiverId));

            if (channel != null && channel.isActive()) {
                // 3. 用户在线，直接转发完整消息
                TextWebSocketFrame frame = new TextWebSocketFrame(message);
                channel.writeAndFlush(frame).addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        log.info("系统通知推送成功，messageId: {}, receiverId: {}, type: {}",
                                messageId, receiverId, type);
                    } else {
                        log.error("系统通知推送失败，messageId: {}, receiverId: {}, type: {}, 错误: {}",
                                messageId, receiverId, type,
                                future.cause() != null ? future.cause().getMessage() : "未知错误");
                    }
                });
            } else {
                // 4. 用户离线，转入持久化 topic（让用户上线后能补拉历史）
                if (OnlineStatusUtil.isUserOffline(stringRedisTemplate, receiverId)) {
                    log.info("用户离线，系统通知发送到Kafka进行持久化，messageId: {}, receiverId: {}, type: {}",
                            messageId, receiverId, type);

                    kafkaTemplate.send(CommonConstant.KAFKA_STORE_NOTIFICATION_TOPIC, message)
                            .whenComplete((result, ex) -> {
                                if (ex == null) {
                                    log.info("系统通知持久化消息发送成功，messageId: {}, receiverId: {}, type: {}",
                                            messageId, receiverId, type);
                                } else {
                                    log.error("系统通知持久化消息发送失败，messageId: {}, receiverId: {}, type: {}, 错误: {}",
                                            messageId, receiverId, type, ex.getMessage());
                                }
                            });
                }
            }

        } catch (Exception e) {
            log.error("处理系统通知消息失败，消息: {}, 错误: {}", message, e.getMessage(), e);
        }
    }
}