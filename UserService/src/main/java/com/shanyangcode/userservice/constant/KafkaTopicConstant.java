package com.shanyangcode.userservice.constant;

/**
 * Kafka Topic 常量定义
 *
 * 本类定义了UserService模块使用的所有Kafka Topic名称
 */
public class KafkaTopicConstant {

    /**
     * 系统通知消息Topic
     * 用途：发送系统通知消息（新会话、好友申请、群聊邀请等）
     * Producer：UserService
     * Consumer：RealTimeService
     */
    public static final String TOPIC_SYSTEM_NOTIFICATION = "system-notification-topic";

    /**
     * 好友申请创建事件Topic
     * 用途：好友申请创建时发送事件，用于注册延迟过期任务
     * Producer：UserService (ApplyFriendService)
     * Consumer：UserService (FriendRequestExpirationEnqueuer)
     */
    public static final String TOPIC_FRIEND_REQUEST_CREATION = "friend-request-creation-topic";

    /**
     * 好友申请过期事件Topic
     * 用途：定时任务扫描到过期的好友申请时发送事件
     * Producer：UserService (FriendRequestExpirationDispatcher)
     * Consumer：UserService (FriendRequestExpirationExecutor)
     */
    public static final String TOPIC_FRIEND_REQUEST_EXPIRATION = "friend-request-expiration-topic";

    /**
     * 系统通知消费者组ID
     */
    public static final String GROUP_SYSTEM_NOTIFICATION_CONSUMER = "system-notification-consumer-group";

    /**
     * 好友申请创建事件消费者组ID
     */
    public static final String GROUP_FRIEND_REQUEST_ENQUEUER = "friend-request-enqueuer-group";

    /**
     * 好友申请过期事件消费者组ID
     */
    public static final String GROUP_FRIEND_REQUEST_EXECUTOR = "friend-request-executor-group";
}