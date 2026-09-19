package com.shanyangcode.redpacketservice.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shanyangcode.common.common.BaseResponse;
import com.shanyangcode.common.common.ErrorCode;
import com.shanyangcode.common.constant.CommonConstant;
import com.shanyangcode.common.constant.MessageTypeConstant;
import com.shanyangcode.common.constant.SessionTypeConstant;
import com.shanyangcode.common.enums.ValidationError;
import com.shanyangcode.common.exception.BusinessException;
import com.shanyangcode.common.exception.ThrowUtils;
import com.shanyangcode.common.model.dto.MessageBody;
import com.shanyangcode.common.model.dto.MessageRequest;
import com.shanyangcode.common.utils.SnowflakeUtil;
import com.shanyangcode.redpacketservice.client.UserServiceClient;
import com.shanyangcode.redpacketservice.config.KafkaConfig;
import com.shanyangcode.redpacketservice.constant.BalanceLogConstant;
import com.shanyangcode.redpacketservice.constant.ReceiveResultConstant;
import com.shanyangcode.redpacketservice.constant.RedPacketConstant;
import com.shanyangcode.redpacketservice.constant.RedisKeyConstant;
import com.shanyangcode.redpacketservice.mapper.BalanceLogMapper;
import com.shanyangcode.redpacketservice.mapper.RedPacketMapper;
import com.shanyangcode.redpacketservice.mapper.RedPacketReceiveMapper;
import com.shanyangcode.redpacketservice.mapper.UserBalanceMapper;
import com.shanyangcode.redpacketservice.model.dto.*;
import com.shanyangcode.redpacketservice.model.entity.BalanceLog;
import com.shanyangcode.redpacketservice.model.entity.RedPacket;
import com.shanyangcode.redpacketservice.model.entity.RedPacketReceive;
import com.shanyangcode.redpacketservice.model.entity.UserBalance;
import com.shanyangcode.redpacketservice.model.vo.*;
import com.shanyangcode.redpacketservice.service.RedPacketService;
import com.shanyangcode.redpacketservice.service.RedPacketValidationService;
import com.shanyangcode.redpacketservice.util.RedPacketAlgorithm;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;


import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 红包服务实现类
 *
 * @author shanyangcode
 */
@Service
@Slf4j
public class RedPacketServiceImpl implements RedPacketService {

    private final RedPacketMapper redPacketMapper;

    private final UserBalanceMapper userBalanceMapper;

    private final BalanceLogMapper balanceLogMapper;

    private final StringRedisTemplate stringRedisTemplate;

    private final KafkaTemplate<String, String> kafkaTemplate;

    private final RedPacketValidationService redPacketValidationService;

    private final DefaultRedisScript<Long> calculateRemainAmountScript;

    private final DefaultRedisScript<List> receiveRedPacketScript;

    private final RedPacketReceiveMapper redPacketReceiveMapper;

    private final UserServiceClient userServiceClient;

    public RedPacketServiceImpl(RedPacketMapper redPacketMapper,
                                UserBalanceMapper userBalanceMapper,
                                BalanceLogMapper balanceLogMapper,
                                StringRedisTemplate stringRedisTemplate,
                                KafkaTemplate<String, String> kafkaTemplate,
                                RedPacketValidationService redPacketValidationService,
                                DefaultRedisScript<Long> calculateRemainAmountScript,
                                DefaultRedisScript<List> receiveRedPacketScript,
                                RedPacketReceiveMapper redPacketReceiveMapper,
                                UserServiceClient userServiceClient) {
        this.redPacketMapper = redPacketMapper;
        this.userBalanceMapper = userBalanceMapper;
        this.balanceLogMapper = balanceLogMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.redPacketValidationService = redPacketValidationService;
        this.calculateRemainAmountScript = calculateRemainAmountScript;
        this.receiveRedPacketScript = receiveRedPacketScript;
        this.redPacketReceiveMapper = redPacketReceiveMapper;
        this.userServiceClient = userServiceClient;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RedPacketSendVO sendRedPacket(RedPacketSendRequest request) {
        // 1. 校验（群聊顺带取回成员快照）
        validateSendRequest(request);
        List<Long> memberIds = redPacketValidationService.validateSendPermission(request);

        // 2. 提取参数
        RedPacketBody body = request.getBody();
        Long senderId = request.getSenderId();
        Long totalAmount = body.getTotalAmount().multiply(BigDecimal.valueOf(RedPacketConstant.YUAN_TO_FEN_MULTIPLIER)).longValue(); // 转为分
        Integer totalCount = body.getTotalCount();
        Integer redPacketType = body.getRedPacketType();

        // 3. 扣减发送者余额
        deductSenderBalance(senderId, totalAmount);

        // 4. 创建红包记录
        long redPacketId = createRedPacket(request, senderId, totalAmount, totalCount, redPacketType);

        // 5. 记录余额变动日志
        recordBalanceLog(senderId, -totalAmount, BalanceLogConstant.TYPE_SEND, redPacketId);

        // 6. 红包金额预分配并初始化 Redis 缓存
        initRedPacketCache(redPacketId, request, totalAmount, totalCount, memberIds);

        // 7. 发送红包消息（消息持久化与推送）
        Long messageId = sendRedPacketMessage(request, redPacketId);

        // 8. 发送红包创建事件（过期处理注册）
        RedPacketCreationEvent creationEvent = new RedPacketCreationEvent();
        creationEvent.setRedPacketId(redPacketId);
        creationEvent.setCreateTime(System.currentTimeMillis());
        kafkaTemplate.send(KafkaConfig.TOPIC_REDPACKET_CREATION, JSONUtil.toJsonStr(creationEvent));

        log.info("红包发送成功，红包ID: {}, 消息ID: {}, 发送者: {}, 金额: {}, 数量: {}",
                redPacketId, messageId, senderId, totalAmount, totalCount);

        // 9. 构建返回结果
        RedPacketSendVO result = new RedPacketSendVO();
        result.setRedPacketId(redPacketId);
        result.setMessageId(messageId);
        return result;
    }

    /**
     * 记录余额变动日志
     *
     * @param userId    用户 ID
     * @param amount    变动金额（发送红包为负数，领取/退回为正数）
     * @param type      变动类型，见 {@link BalanceLogConstant}
     * @param relatedId 关联业务 ID（如红包 ID）
     */
    private void recordBalanceLog(Long userId, Long amount, Integer type, Long relatedId) {
        BalanceLog balanceLog = new BalanceLog();
        balanceLog.setBalanceLogId(SnowflakeUtil.nextId());
        balanceLog.setUserId(userId);
        balanceLog.setAmount(amount);
        balanceLog.setType(type);
        balanceLog.setRelatedId(relatedId);
        balanceLog.setCreatedTime(new Date());
        balanceLog.setUpdatedTime(new Date());
        balanceLogMapper.insert(balanceLog);
    }

    /**
     * 扣减发送者余额
     * <p>
     * ge 条件保证余额充足（防止透支），setSql 原子自减，单条 UPDATE 并发安全。
     */
    private void deductSenderBalance(Long senderId, Long totalAmount) {
        LambdaUpdateWrapper<UserBalance> wrapper = Wrappers.<UserBalance>lambdaUpdate()
                .eq(UserBalance::getUserId, senderId)
                .ge(UserBalance::getBalance, totalAmount)
                .setSql("balance = balance - " + totalAmount);
        int result = userBalanceMapper.update(null, wrapper);
        ThrowUtils.throwIf(result == 0, ErrorCode.OPERATION_ERROR, "余额不足或扣款失败");
    }

    /**
     * 创建红包记录
     *
     * @return 红包 ID
     */
    private long createRedPacket(RedPacketSendRequest request, Long senderId,
                                 Long totalAmount, Integer totalCount, Integer redPacketType) {
        RedPacket redPacket = new RedPacket();
        redPacket.setRedPacketId(SnowflakeUtil.nextId());
        redPacket.setSenderId(senderId);
        redPacket.setSessionId(request.getSessionId());
        redPacket.setSessionType(request.getSessionType());
        redPacket.setRedPacketWrapperText(request.getBody().getRedPacketWrapperText());
        redPacket.setRedPacketType(redPacketType);
        redPacket.setTotalAmount(totalAmount);
        redPacket.setTotalCount(totalCount);
        redPacket.setStatus(RedPacketConstant.STATUS_NOT_COMPLETED);
        redPacket.setCreatedTime(new Date());
        redPacket.setUpdatedTime(new Date());
        int insertResult = redPacketMapper.insert(redPacket);
        ThrowUtils.throwIf(insertResult == 0, ErrorCode.SYSTEM_ERROR, "红包创建失败");
        return redPacket.getRedPacketId();
    }

    /**
     * 红包金额预分配并初始化 Redis 缓存
     * <p>
     * 写入顺序刻意为 grant → allow → pool：让「权限」永远比「钱」先落地，
     * 任何时刻都不会出现「有钱可抢、无权可判」的缝隙
     * （与 13.5.9「墓碑先写、金额池后删」同一条原则）
     *
     * @param redPacketId 红包 ID
     * @param request     红包发送请求
     * @param totalAmount 红包总金额（分）
     * @param totalCount  红包数量
     * @param memberIds   群成员快照（群聊人数业务上限 500，一次 SADD 全量写入）；单聊传 null
     */
    private void initRedPacketCache(Long redPacketId, RedPacketSendRequest request,
                                    Long totalAmount, Integer totalCount, List<Long> memberIds) {
        Integer sessionType = request.getSessionType();

        // 1. 先写授权 grant（字段名必须与 Lua 里 HMGET 的三个字面量完全一致）
        String grantKey = RedisKeyConstant.getGrantKey(redPacketId);
        Map<String, String> grant = new HashMap<>(4);
        grant.put("sessionType", String.valueOf(sessionType));
        grant.put("senderId", String.valueOf(request.getSenderId()));
        if (Objects.equals(sessionType, SessionTypeConstant.SIGNAL_TYPE)) {
            grant.put("receiverId", String.valueOf(request.getReceiverId()));
        }
        stringRedisTemplate.opsForHash().putAll(grantKey, grant);
        stringRedisTemplate.expire(grantKey, RedPacketConstant.REDIS_CACHE_EXPIRE_HOURS, TimeUnit.HOURS);

        // 2. 群聊写成员快照 allow（单聊 memberIds 为 null，直接跳过）
        //    群聊人数业务上限 500，一次 SADD 全量写完，不分档、不分批
        if (memberIds != null && !memberIds.isEmpty()) {
            String allowKey = RedisKeyConstant.getAllowKey(redPacketId);
            String[] members = memberIds.stream().map(String::valueOf).toArray(String[]::new);
            stringRedisTemplate.opsForSet().add(allowKey, members);
            stringRedisTemplate.expire(allowKey, RedPacketConstant.REDIS_CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
        }

        // 3. 后写钱：金额预分配 + 写入金额池（原有逻辑原样后移）
        Integer redPacketType = request.getBody().getRedPacketType();
        List<Long> amounts = redPacketType == RedPacketConstant.TYPE_NORMAL
                ? RedPacketAlgorithm.allocateNormalRedPacket(totalAmount, totalCount)
                : RedPacketAlgorithm.allocateRandomRedPacket(totalAmount, totalCount);

        String poolKey = RedisKeyConstant.getPoolKey(redPacketId);
        List<String> amountStrings = amounts.stream()
                .map(String::valueOf)
                .collect(Collectors.toList());
        stringRedisTemplate.opsForList().rightPushAll(poolKey, amountStrings);
        stringRedisTemplate.expire(poolKey, RedPacketConstant.REDIS_CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
    }

    /**
     * 验证发送红包请求参数
     */
    private void validateSendRequest(RedPacketSendRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(request.getBody() == null, ErrorCode.PARAMS_ERROR, "红包信息为空");
        ThrowUtils.throwIf(request.getSenderId() == null, ErrorCode.PARAMS_ERROR, "发送者ID为空");
        ThrowUtils.throwIf(request.getSessionId() == null, ErrorCode.PARAMS_ERROR, "会话ID为空");
        // 单聊必须有接收者：receiverId 会被写进 grant 作为唯一可领人，为空则谁都领不了
        ThrowUtils.throwIf(SessionTypeConstant.SIGNAL_TYPE == request.getSessionType()
                        && request.getReceiverId() == null,
                ErrorCode.PARAMS_ERROR, "单聊接收者ID为空");

        RedPacketBody body = request.getBody();
        ThrowUtils.throwIf(body.getTotalAmount() == null || body.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0,
                ErrorCode.PARAMS_ERROR, "红包金额必须大于0");
        ThrowUtils.throwIf(body.getTotalCount() == null || body.getTotalCount() <= 0,
                ErrorCode.PARAMS_ERROR, "红包数量必须大于0");
        ThrowUtils.throwIf(body.getRedPacketType() == null || !RedPacketConstant.isValidType(body.getRedPacketType()),
                ErrorCode.PARAMS_ERROR, "红包类型错误");

        // 验证金额和数量的关系（金额单位是元，每个红包至少1分=0.01元）
        // 计算红包数量×0.01元得到最小总金额，如果用户输入的总金额小于该值，则抛出参数错误异常
        BigDecimal minAmountYuan = new BigDecimal(body.getTotalCount()).multiply(new BigDecimal("0.01"));
        ThrowUtils.throwIf(body.getTotalAmount().compareTo(minAmountYuan) < 0,
                ErrorCode.PARAMS_ERROR, "红包总金额不能少于红包数量（每个红包至少1分）");

        // 验证单个红包金额上限（单个红包不超过 200 元）
        // 计算平均每个红包的金额（总金额÷数量，保留2位小数四舍五入），如果超过200元限制则抛出参数错误异常。
        BigDecimal singleAmount = body.getTotalAmount().divide(
                new BigDecimal(body.getTotalCount()), 2, RoundingMode.HALF_UP);
        ThrowUtils.throwIf(
                singleAmount.compareTo(BigDecimal.valueOf(RedPacketConstant.MAX_SINGLE_AMOUNT_YUAN)) > 0,
                ErrorCode.PARAMS_ERROR,
                "单个红包金额不能超过" + RedPacketConstant.MAX_SINGLE_AMOUNT_YUAN + "元");
    }

    /**
     * 发送红包消息到 Kafka（用于消息持久化和推送）
     *
     * @param request     红包发送请求
     * @param redPacketId 红包 ID
     * @return 消息 ID
     */
    private Long sendRedPacketMessage(RedPacketSendRequest request, Long redPacketId) {
        // 1) 构建 MessageRequest
        MessageRequest messageRequest = new MessageRequest();
        messageRequest.setSessionId(request.getSessionId());
        messageRequest.setSenderId(request.getSenderId());
        messageRequest.setType(MessageTypeConstant.RED_PACKET_MESSAGE);
        messageRequest.setSessionType(request.getSessionType());
        messageRequest.setClientMessageId(request.getClientMessageId());

        // - 单聊(SIGNAL_TYPE)：receiverId 必须不为 null
        // - 群聊(GROUP_TYPE)：receiverId 必须为null
        if (Objects.equals(request.getSessionType(), SessionTypeConstant.SIGNAL_TYPE)) {
            messageRequest.setReceiverId(request.getReceiverId());
        } else if (Objects.equals(request.getSessionType(), SessionTypeConstant.GROUP_TYPE)) {
            messageRequest.setReceiverId(null);
        }

        // 2) 构建红包消息体，包含 redPacketId 和 redPacketWrapperText
        MessageBody body = new MessageBody();
        body.setRedPacketId(String.valueOf(redPacketId));
        body.setRedPacketWrapperText(request.getBody().getRedPacketWrapperText());
        messageRequest.setBody(body);

        // 3) 交给统一入口（内部会：补 messageId/createdTime、check、发 store/push 两个 topic）
        Long messageId = sendMessageKafka(messageRequest);

        log.info("红包消息已入队，红包ID: {}, 消息ID: {}", redPacketId, messageId);
        return messageId;
    }

    /**
     * 发送消息到 Kafka
     *
     * @param messageRequest 消息请求
     * @return 消息 ID
     */
    public Long sendMessageKafka(MessageRequest messageRequest) {
        // 转成消息体
        Long messageId = SnowflakeUtil.nextId();
        messageRequest.setMessageId(messageId);
        messageRequest.setCreatedTime(new Date());

        // 校验消息
        checkMessage(messageRequest.getSessionType(), messageRequest.getReceiverId());

        // 消息存储, 存储只存储一次，避免重复消费
        kafkaTemplate.send(CommonConstant.KAFKA_MESSAGE_TOPIC_STORE, JSONUtil.toJsonStr(messageRequest)).whenComplete((success, failure) -> {
            if (failure != null) {
                // 生产者生产失败
                System.err.println("生产者生产失败: " + failure.getMessage());
                // 记录日志、告警、补偿等
            } else {
                // 生产者生产成功
                System.out.println("生产者生产成功，offset: " + success.getRecordMetadata().offset());
            }
        });

        // 消息推送消息
        kafkaTemplate.send(CommonConstant.KAFKA_MESSAGE_TOPIC_PUSH, messageRequest.getSessionId().toString(), JSONUtil.toJsonStr(messageRequest)).whenComplete((success, failure) -> {
            if (failure != null) {
                // 生产者生产失败
                System.err.println("生产者生产失败: " + failure.getMessage());
                // 记录日志、告警、补偿等
            } else {
                // 生产者生产成功
                System.out.println("生产者生产成功，offset: " + success.getRecordMetadata().offset());
            }
        });

        return messageId;
    }

    public void checkMessage(Integer sessionType, Long receiverId) {
        ThrowUtils.throwIf(sessionType == SessionTypeConstant.SIGNAL_TYPE && receiverId == null, ErrorCode.SIGNAL_TYPE_ERROR);
        ThrowUtils.throwIf(sessionType == SessionTypeConstant.GROUP_TYPE && receiverId != null, ErrorCode.GROUP_TYPE_ERROR);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleRedPacketExpiration(Long redPacketId) {
        // 1. 查询红包信息
        RedPacket redPacket = redPacketMapper.selectById(redPacketId);
        if (redPacket == null) {
            log.warn("红包不存在，红包ID: {}", redPacketId);
            return;
        }

        // 2. CAS 抢占终态：只有把 未领完 -> 已过期 改成功的线程才继续（见第 4 条）
        LambdaUpdateWrapper<RedPacket> cas = Wrappers.<RedPacket>lambdaUpdate()
                .eq(RedPacket::getRedPacketId, redPacketId)
                .eq(RedPacket::getStatus, RedPacketConstant.STATUS_NOT_COMPLETED)
                .set(RedPacket::getStatus, RedPacketConstant.STATUS_EXPIRED)
                .set(RedPacket::getUpdatedTime, new Date());
        if (redPacketMapper.update(null, cas) == 0) {
            RedPacket current = redPacketMapper.selectById(redPacketId);
            if (current == null || !Objects.equals(current.getStatus(), RedPacketConstant.STATUS_EXPIRED)) {
                log.info("红包终态已被其他流程写入，跳过过期处理。红包ID: {}, 状态: {}",
                        redPacketId, current == null ? null : current.getStatus());
                return;
            }
            log.info("红包过期事件重复投递，补做清理。红包ID: {}", redPacketId);
        }

        String poolKey = RedisKeyConstant.getPoolKey(redPacketId);
        String recordsKey = RedisKeyConstant.getRecordsKey(redPacketId);
        String stateKey = RedisKeyConstant.getStateKey(redPacketId);

        // 3. 先立碑：墓碑必须早于金额池消失，"无碑 + 池空" 才能唯一指向 "刚被抢完"
        stringRedisTemplate.opsForValue().set(stateKey,
                String.valueOf(RedPacketConstant.STATUS_EXPIRED),
                RedPacketConstant.REDIS_CACHE_EXPIRE_HOURS, TimeUnit.HOURS);

        // 4. 后封盘：结算剩余金额并销毁金额池，同一段 Lua，不留双花窗口
        Long remainAmount = stringRedisTemplate.execute(
                calculateRemainAmountScript, Collections.singletonList(poolKey));
        if (remainAmount == null) {
            remainAmount = 0L;
        }

        // 5. 有剩余则退回发送者
        if (remainAmount > 0) {
            Long senderId = redPacket.getSenderId();
            addBalance(senderId, remainAmount);
            recordBalanceLog(senderId, remainAmount, BalanceLogConstant.TYPE_REFUND, redPacketId);
            log.info("红包过期，退回金额: {}，用户ID: {}", remainAmount, senderId);
        }

        // 6. 领取记录转 TTL 保留，移出过期队列
        stringRedisTemplate.expire(recordsKey, RedPacketConstant.REDIS_CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
        stringRedisTemplate.opsForZSet().remove(RedisKeyConstant.EXPIRE_ZSET, String.valueOf(redPacketId));

        log.info("红包过期处理完成。红包ID: {}, 退回金额: {}", redPacketId, remainAmount);
    }

    /**
     * 增加用户余额（领取红包或退回）
     * <p>
     * Service 层构建 LambdaUpdateWrapper 调用 mapper.update：
     * - eq: user_id = userId
     * - setSql: balance = balance + amount（原子自增，amount 为 Long 类型，拼接安全）
     * 返回值为受影响行数，0 表示用户余额行不存在或更新失败，抛业务异常。
     */
    private void addBalance(Long userId, Long amount) {
        LambdaUpdateWrapper<UserBalance> wrapper = Wrappers.<UserBalance>lambdaUpdate()
                .eq(UserBalance::getUserId, userId)
                .setSql("balance = balance + " + amount);
        int result = userBalanceMapper.update(null, wrapper);
        ThrowUtils.throwIf(result == 0, ErrorCode.OPERATION_ERROR, "用户余额更新失败");
    }

    @Override
    public ReceiveResultVO receiveRedPacket(RedPacketReceiveRequest request) {
        Long userId = request.getUserId();
        Long redPacketId = request.getRedPacketId();

        String poolKey = RedisKeyConstant.getPoolKey(redPacketId);
        String recordsKey = RedisKeyConstant.getRecordsKey(redPacketId);
        String stateKey = RedisKeyConstant.getStateKey(redPacketId);
        String grantKey = RedisKeyConstant.getGrantKey(redPacketId);
        String allowKey = RedisKeyConstant.getAllowKey(redPacketId);

        // 执行 Lua 脚本领取红包（顺序必须与脚本 KEYS[1..5] 一一对应）
        List<String> keys = Arrays.asList(poolKey, recordsKey, stateKey, grantKey, allowKey);

        List result = stringRedisTemplate.execute(
                receiveRedPacketScript,
                keys,
                String.valueOf(userId)
        );

        if (result == null || result.isEmpty()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "红包领取失败");
        }

        Object firstElement = result.get(0);
        int resultCode = ((Number) firstElement).intValue();
        // 第 0 层：无权领取。
        if (resultCode == ReceiveResultConstant.NO_PERMISSION) {
            int reason = ((Number) result.get(1)).intValue();
            ValidationError error = ReceiveResultConstant.toValidationError(reason);
            log.warn("红包领取权限校验未通过，用户ID: {}, 红包ID: {}, 原因: {}",
                    userId, redPacketId, error.name());
            throw new BusinessException(error.getCode(), error.getMessage());
        }

        ReceiveResultVO vo = new ReceiveResultVO();

        // 处理不同的返回值
        if (resultCode == ReceiveResultConstant.ALREADY_RECEIVED) {
            // 已领取过：status 取 Lua 带回的红包实时状态
            vo.setStatus(((Number) result.get(1)).intValue());
            vo.setMessage("您已经领取过该红包了");
            Object amountObj = stringRedisTemplate.opsForHash().get(recordsKey, String.valueOf(userId));
            if (amountObj != null) {
                vo.setAmount(convertFenToYuan(Long.parseLong(amountObj.toString())));
            }
            return vo;
        } else if (resultCode == ReceiveResultConstant.EMPTY_POOL) {
            // 红包已进入终态，墓碑直接给出死因，零 SQL
            int state = ((Number) result.get(1)).intValue();
            vo.setStatus(state);
            vo.setMessage(state == RedPacketConstant.STATUS_EXPIRED ? "红包已过期" : "红包已被领完");
            return vo;

        } else if (resultCode == ReceiveResultConstant.NOT_FOUND) {
            // Redis 中查无痕迹，查询数据库获取真实状态
            RedPacket redPacket = redPacketMapper.selectById(redPacketId);
            if (redPacket == null) {
                vo.setStatus(RedPacketConstant.STATUS_NOT_EXIST);
                vo.setMessage("红包不存在");
                return vo;
            }

            // 返回数据库中的真实状态
            vo.setStatus(redPacket.getStatus());
            if (redPacket.getStatus() == RedPacketConstant.STATUS_COMPLETED) {
                vo.setMessage("红包已被领完");
            } else if (redPacket.getStatus() == RedPacketConstant.STATUS_EXPIRED) {
                vo.setMessage("红包已过期");
            } else {
                // 理论上不应该出现，Redis 无痕迹但数据库状态为未领完
                vo.setMessage("红包暂时无法领取");
            }
            return vo;
        }

        // 领取成功
        long amount = ((Number) firstElement).longValue();
        int completed = result.size() > 1 ? ((Number) result.get(1)).intValue() : ReceiveResultConstant.COMPLETION_FLAG_NOT_COMPLETED;

        vo.setStatus(RedPacketConstant.STATUS_NOT_COMPLETED);
        vo.setAmount(convertFenToYuan(amount));
        vo.setMessage("恭喜您，领取成功");

        // 发送领取记录到 Kafka
        RedPacketReceiveEvent event = RedPacketReceiveEvent.builder()
                .userId(userId)
                .redPacketId(redPacketId)
                .receivedAmount(amount)
                .receiveTime(System.currentTimeMillis())
                .build();

        kafkaTemplate.send(
                KafkaConfig.TOPIC_REDPACKET_RECEIVE,
                JSONUtil.toJsonStr(event));

        // 如果红包已领完，返回状态为已领取完，发送领完事件
        if (ReceiveResultConstant.isCompleted(completed)) {
            vo.setStatus(RedPacketConstant.STATUS_COMPLETED);
            Map<String, Object> completedEvent = new HashMap<>();
            completedEvent.put("redPacketId", redPacketId);
            kafkaTemplate.send(KafkaConfig.TOPIC_REDPACKET_COMPLETED, JSONUtil.toJsonStr(completedEvent));
        }

        log.info("用户 {} 领取红包 {} 成功，金额: {}", userId, redPacketId, amount);

        return vo;
    }

    /**
     * 分转元
     *
     * @param amountFen 金额（分）
     * @return 金额（元）
     */
    private BigDecimal convertFenToYuan(Long amountFen) {
        if (amountFen == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(amountFen)
                .divide(BigDecimal.valueOf(RedPacketConstant.YUAN_TO_FEN_MULTIPLIER), 2, RoundingMode.HALF_UP);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleRedPacketReceive(Long userId, Long redPacketId, Long receivedAmount, Long receiveTime) {
        // 幂等性检查：判断是否已经插入过
        QueryWrapper<RedPacketReceive> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("red_packet_id", redPacketId);
        queryWrapper.eq("receiver_id", userId);
        Long count = redPacketReceiveMapper.selectCount(queryWrapper);

        if (count > 0) {
            log.warn("红包领取记录已存在，跳过处理。红包ID: {}, 用户ID: {}", redPacketId, userId);
            return;
        }

        // 1. 插入领取记录
        RedPacketReceive receive = new RedPacketReceive();
        receive.setRedPacketReceiveId(SnowflakeUtil.nextId());
        receive.setRedPacketId(redPacketId);
        receive.setReceiverId(userId);
        receive.setAmount(receivedAmount);
        receive.setReceivedAt(new Date(receiveTime));
        receive.setCreatedTime(new Date());
        receive.setUpdatedTime(new Date());
        redPacketReceiveMapper.insert(receive);

        // 2. 增加用户余额
        addBalance(userId, receivedAmount);

        // 3. 记录余额变动日志
        recordBalanceLog(userId, receivedAmount, BalanceLogConstant.TYPE_RECEIVE, redPacketId);

        log.info("红包领取记录处理完成。红包ID: {}, 用户ID: {}, 金额: {}", redPacketId, userId, receivedAmount);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handleRedPacketCompleted(Long redPacketId) {
        // 1. CAS 抢占终态：未领完 -> 已领完
        LambdaUpdateWrapper<RedPacket> cas = Wrappers.<RedPacket>lambdaUpdate()
                .eq(RedPacket::getRedPacketId, redPacketId)
                .eq(RedPacket::getStatus, RedPacketConstant.STATUS_NOT_COMPLETED)
                .set(RedPacket::getStatus, RedPacketConstant.STATUS_COMPLETED)
                .set(RedPacket::getUpdatedTime, new Date());

        if (redPacketMapper.update(null, cas) == 0) {
            RedPacket current = redPacketMapper.selectById(redPacketId);
            if (current == null || !Objects.equals(current.getStatus(), RedPacketConstant.STATUS_COMPLETED)) {
                // 红包不存在，或终态已被过期流程写成"已过期"，让位
                log.info("红包不存在或红包终态已被其他流程写入，跳过领完处理。红包ID: {}, 状态: {}",
                        redPacketId, current == null ? null : current.getStatus());
                return;
            }
            // 状态已经是"已领完"，说明是本 handler 的重复投递，继续把 Redis 清理补做一遍
            log.info("红包领完事件重复投递，补做清理。红包ID: {}", redPacketId);
        }

        String poolKey = RedisKeyConstant.getPoolKey(redPacketId);
        String recordsKey = RedisKeyConstant.getRecordsKey(redPacketId);
        String stateKey = RedisKeyConstant.getStateKey(redPacketId);

        // 2. 立墓碑 -> 删金额池 -> 领取记录转 TTL -> 移出过期队列
        stringRedisTemplate.opsForValue().set(stateKey,
                String.valueOf(RedPacketConstant.STATUS_COMPLETED),
                RedPacketConstant.REDIS_CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
        stringRedisTemplate.delete(poolKey);
        stringRedisTemplate.expire(recordsKey, RedPacketConstant.REDIS_CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
        stringRedisTemplate.opsForZSet().remove(RedisKeyConstant.EXPIRE_ZSET, String.valueOf(redPacketId));

        log.info("红包已领取完，清理完成。红包ID: {}", redPacketId);
    }

    @Override
    public RedPacketDetailVO getRedPacketDetail(Long redPacketId, int pageNum, int pageSize) {
        // 1. 查询红包基本信息
        RedPacket redPacket = redPacketMapper.selectById(redPacketId);
        ThrowUtils.throwIf(redPacket == null, ErrorCode.NOT_FOUND_ERROR, "红包不存在");

        RedPacketDetailVO vo = new RedPacketDetailVO();
        BeanUtils.copyProperties(redPacket, vo, "totalAmount");  // 排除 totalAmount，手动转换
        // 分转元
        vo.setTotalAmount(convertFenToYuan(redPacket.getTotalAmount()));

        // 2. 从数据库分页查询领取记录
        Page<RedPacketReceive> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<RedPacketReceive> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RedPacketReceive::getRedPacketId, redPacketId)
                .orderByDesc(RedPacketReceive::getReceivedAt) // 按领取时间倒序
                .orderByAsc(RedPacketReceive::getReceiverId); // 再按领取人ID正序排序
        Page<RedPacketReceive> result = redPacketReceiveMapper.selectPage(page, wrapper);

        List<RedPacketReceive> receiveList = result.getRecords();

        // 3. 统计信息
        int receivedCount = (int) result.getTotal(); // 已领取的红包数量
        long receivedAmountFen = sumAmountFromDB(redPacketId); //已领取的红包总金额

        // 4. 批量获取用户信息（发送者 + 所有领取者）
        Set<Long> userIds = new HashSet<>();
        userIds.add(redPacket.getSenderId());
        receiveList.forEach(r -> userIds.add(r.getReceiverId()));

        Map<Long, com.shanyangcode.common.model.vo.UserInfosResponse> userInfoMap = batchGetUserInfos(userIds);

        // 5. 填充发送者信息
        com.shanyangcode.common.model.vo.UserInfosResponse senderInfo = userInfoMap.get(redPacket.getSenderId());
        if (senderInfo != null) {
            vo.setSenderNickname(senderInfo.getNickname());
            vo.setSenderAvatar(senderInfo.getAvatar());
        }

        // 6. 将 RedPacketReceive 转换为 RedPacketReceiveVO，并填充领取者信息
        List<RedPacketReceiveVO> receiveRecords = receiveList.stream() // 把领取记录集合转成 Stream，准备做一对一映射。
                .map(receive -> { // 每条记录映射成一个新的 VO 对象：
                    RedPacketReceiveVO receiveVO = new RedPacketReceiveVO();
                    receiveVO.setReceiverId(receive.getReceiverId());
                    // 分转元
                    receiveVO.setAmount(convertFenToYuan(receive.getAmount()));
                    receiveVO.setReceivedAt(receive.getReceivedAt());

                    // 填充领取者用户信息
                    com.shanyangcode.common.model.vo.UserInfosResponse receiverInfo = userInfoMap.get(receive.getReceiverId());
                    if (receiverInfo != null) {
                        receiveVO.setReceiverNickname(receiverInfo.getNickname());
                        receiveVO.setReceiverAvatar(receiverInfo.getAvatar());
                    }
                    return receiveVO;
                })
                .collect(Collectors.toList());

        vo.setReceiveRecords(receiveRecords);

        // 7. 设置统计信息
        vo.setReceivedCount(receivedCount);
        vo.setReceivedAmount(convertFenToYuan(receivedAmountFen));

        return vo;
    }




    /**
     * 从数据库统计已领取总金额
     *
     * @param redPacketId 红包ID
     * @return 已领取总金额（分）
     */
    private long sumAmountFromDB(Long redPacketId) {
        LambdaQueryWrapper<RedPacketReceive> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RedPacketReceive::getRedPacketId, redPacketId)
                .select(RedPacketReceive::getAmount);
        return redPacketReceiveMapper.selectList(wrapper).stream()
                .mapToLong(RedPacketReceive::getAmount)
                .sum();
    }



    /**
     * 批量获取用户信息
     *
     * @param userIds 用户ID集合
     * @return 用户ID到用户信息的映射
     */
    private Map<Long, com.shanyangcode.common.model.vo.UserInfosResponse> batchGetUserInfos(Set<Long> userIds) {
        Map<Long, com.shanyangcode.common.model.vo.UserInfosResponse> userInfoMap = new HashMap<>();
        try {
            BaseResponse<Map<Long, com.shanyangcode.common.model.vo.UserInfosResponse>> response =
                    userServiceClient.batchGetUserInfos(new ArrayList<>(userIds));

            if (response != null && response.getCode() == 200 && response.getData() != null) {
                // JSON 反序列化时 Map 的 Long key 会被解析为 String/Integer，需要手动转换
                // 嵌套对象可能被解析为 LinkedHashMap，也需要处理
                Map<?, ?> rawMap = response.getData();
                for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                    Long key = Long.parseLong(entry.getKey().toString());
                    Object value = entry.getValue();

                    com.shanyangcode.common.model.vo.UserInfosResponse userInfo;
                    if (value instanceof com.shanyangcode.common.model.vo.UserInfosResponse) {
                        userInfo = (com.shanyangcode.common.model.vo.UserInfosResponse) value;
                    } else if (value instanceof Map) {
                        Map<?, ?> map = (Map<?, ?>) value;
                        userInfo = new com.shanyangcode.common.model.vo.UserInfosResponse();
                        userInfo.setUserId(map.get("userId") != null ? Long.parseLong(map.get("userId").toString()) : null);
                        userInfo.setNickname(map.get("nickname") != null ? map.get("nickname").toString() : null);
                        userInfo.setAvatar(map.get("avatar") != null ? map.get("avatar").toString() : null);
                    } else {
                        continue;
                    }
                    userInfoMap.put(key, userInfo);
                }
            }
        } catch (Exception e) {
            log.warn("批量获取用户信息失败，用户ID列表={}，错误={}", userIds, e.getMessage());
        }
        return userInfoMap;
    }

    @Override
    public RedPacketBasicVO getRedPacketBasicInfo(Long redPacketId) {
        // 1. 查询红包基本信息
        RedPacket redPacket = redPacketMapper.selectById(redPacketId);
        ThrowUtils.throwIf(redPacket == null, ErrorCode.NOT_FOUND_ERROR, "红包不存在");

        RedPacketBasicVO vo = new RedPacketBasicVO();
        vo.setRedPacketId(redPacket.getRedPacketId());
        vo.setRedPacketType(redPacket.getRedPacketType());
        vo.setTotalAmount(convertFenToYuan(redPacket.getTotalAmount()));// 分转元
        vo.setTotalCount(redPacket.getTotalCount());
        vo.setStatus(redPacket.getStatus());
        vo.setCreatedTime(redPacket.getCreatedTime());

        // 2. 计算已领取统计信息
        // 从数据库查询
        Long receivedCount = redPacketReceiveMapper.selectCount(
                Wrappers.<RedPacketReceive>lambdaQuery()
                        .eq(RedPacketReceive::getRedPacketId, redPacketId)
        );
        vo.setReceivedCount(receivedCount.intValue());
        long receivedAmountFen = sumAmountFromDB(redPacketId);
        vo.setReceivedAmount(convertFenToYuan(receivedAmountFen));// 分转元
        return vo;
    }
}
