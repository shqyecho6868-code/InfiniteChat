package com.shanyangcode.redpacketservice.config;

import java.util.List;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Redis 配置类
 *
 * @author shanyangcode
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "10m")
public class RedisConfig {

    /**
     * 抢红包 Lua 脚本
     * <p>
     * KEYS[1]: 红包金额池 List Key (使用 {@link com.shanyangcode.redpacketservice.constant.RedisKeyConstant#POOL_TEMPLATE})
     * KEYS[2]: 已领取记录 Hash Key (使用 {@link com.shanyangcode.redpacketservice.constant.RedisKeyConstant#RECORDS_TEMPLATE})
     * ARGV[1]: 当前用户ID (userId)
     * <p>
     * 返回值 (参见 {@link com.shanyangcode.redpacketservice.constant.ReceiveResultConstant}):
     * <ul>
     *   <li>{amount, completed}: 领取成功，amount 为分，completed 表示是否抢完</li>
     *   <li>ALREADY_RECEIVED (-1): 已领取</li>
     *   <li>EMPTY_POOL (-2): Redis 数据为空（需查询数据库获取真实状态）</li>
     * </ul>
     * completed 标识:
     * <ul>
     *   <li>COMPLETION_FLAG_COMPLETED (1): 红包已领完</li>
     *   <li>COMPLETION_FLAG_NOT_COMPLETED (0): 红包未领完</li>
     * </ul>
     */
    /**
     * 抢红包 Lua 脚本
     * <p>
     * KEYS[1]: 红包金额池 List Key (使用 {@link com.shanyangcode.redpacketservice.constant.RedisKeyConstant#POOL_TEMPLATE})
     * KEYS[2]: 已领取记录 Hash Key (使用 {@link com.shanyangcode.redpacketservice.constant.RedisKeyConstant#RECORDS_TEMPLATE})
     * KEYS[3]: 终态墓碑 String Key (使用 {@link com.shanyangcode.redpacketservice.constant.RedisKeyConstant#STATE_TEMPLATE})
     * KEYS[4]: 领取授权 Hash Key (使用 {@link com.shanyangcode.redpacketservice.constant.RedisKeyConstant#GRANT_TEMPLATE})
     * KEYS[5]: 成员快照 Set Key (使用 {@link com.shanyangcode.redpacketservice.constant.RedisKeyConstant#ALLOW_TEMPLATE})
     * ARGV[1]: 当前用户ID (userId)
     * <p>
     * 返回值 (参见 {@link com.shanyangcode.redpacketservice.constant.ReceiveResultConstant}):
     * <ul>
     *   <li>{amount, completed}: 领取成功，amount 为分，completed 表示是否抢完</li>
     *   <li>NO_PERMISSION (-4): 无权领取，第二个元素为拒绝原因（1 自领 / 2 非收款人 / 3 不在会话）</li>
     *   <li>ALREADY_RECEIVED (-1): 已领取，第二个元素为红包此刻状态（0 未领完 / 1 已领完 / 2 已过期）</li>
     *   <li>EMPTY_POOL (-2): 红包已进入终态，第二个元素为终态值（1 已领完 / 2 已过期）</li>
     *   <li>NOT_FOUND (-3): Redis 中查无痕迹（需查询数据库获取真实状态）</li>
     * </ul>
     * completed 标识:
     * <ul>
     *   <li>COMPLETION_FLAG_COMPLETED (1): 红包已领完</li>
     *   <li>COMPLETION_FLAG_NOT_COMPLETED (0): 红包未领完</li>
     * </ul>
     */
    @Bean
    public DefaultRedisScript<List> receiveRedPacketScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        String luaScript = """
            local poolKey   = KEYS[1]
            local recordKey = KEYS[2]
            local stateKey  = KEYS[3]
            local grantKey  = KEYS[4]
            local allowKey  = KEYS[5]
            local userId    = ARGV[1]

            local RECORD_TTL = 25 * 60 * 60  -- 25H，兜底 TTL

            -- 0. 权限校验（顺序不可移动）
            local grant = redis.call('HMGET', grantKey, 'sessionType', 'senderId', 'receiverId')
            local sessionType = grant[1]
            local senderId    = grant[2]
            local receiverId  = grant[3]

            if sessionType == '0' then
                -- 0.1 单聊：有且仅有 receiverId 可领；
                if receiverId ~= userId then
                    if senderId == userId then
                        return {-4, 1}   -- REASON_SENDER_SELF_RECEIVE
                    end
                    return {-4, 2}       -- REASON_NOT_THE_RECEIVER
                end
            elseif sessionType == '1' then
                -- 0.2 群聊：只认发红包那一刻的成员快照；
                if redis.call('SISMEMBER', allowKey, userId) == 0 then
                    return {-4, 3}       -- REASON_NOT_IN_CONVERSATION
                end
            end
            -- 0.3 sessionType 为 false 表示 grant 已随 TTL 消失
            --     不做校验，交给下面台阶返回 -2 / -3

            -- 1. 幂等性检查：判断用户是否已领取
            if redis.call('HEXISTS', recordKey, userId) == 1 then
                -- 1.1 读墓碑：终态已写入，把红包此刻的状态一并带回
                local state = redis.call('GET', stateKey)
                if state then
                    return {-1, tonumber(state)}
                end

                -- 1.2 墓碑还没写：池子还在说明红包仍有剩余，池子没了说明刚被抢完
                if redis.call('EXISTS', poolKey) == 1 then
                    return {-1, 0}
                end
                return {-1, 1}
            end

            -- 2. 从列表中弹出一个金额 (原子)
            local amount = redis.call('LPOP', poolKey)
            if not amount then
                -- 2.1 读墓碑：终态已写入，直接把死因返回给调用方
                local state = redis.call('GET', stateKey)
                if state then
                    return {-2, tonumber(state)}
                end

                -- 2.2 墓碑还没写，但领取记录还在 => 刚被抢完
                if redis.call('EXISTS', recordKey) == 1 then
                    return {-2, 1}
                end

                -- 2.3 池、领取记录、墓碑三者皆空 => Redis 查无此红包
                return {-3}
            end

            -- 3. 记录该用户已领取
            redis.call('HSET', recordKey, userId, amount)
            redis.call('EXPIRE', recordKey, RECORD_TTL, 'NX')

            -- 4. 是否为最后一个
            local left = redis.call('LLEN', poolKey)
            local completed = 0
            if left == 0 then
                completed = 1
            end

            -- 5. 返回抢到的金额（分）和领取完标识符
            return {tonumber(amount), completed}
            """;
        script.setScriptText(luaScript);
        script.setResultType(List.class);
        return script;
    }

    /**
     * 扫描过期红包 Lua 脚本
     * <p>
     * KEYS[1]: 过期任务的 ZSet 键名 (使用 {@link com.shanyangcode.redpacketservice.constant.RedisKeyConstant#EXPIRE_ZSET})
     * ARGV[1]: 当前时间戳（毫秒）。如果 <=0 或为 nil，则使用 Redis 的 TIME 命令获取当前时间
     * ARGV[2]: 最大获取条数（正整数，默认 500）
     * <p>
     * 返回值: 已到期的红包ID列表
     */
    @Bean
    public DefaultRedisScript<List> scanExpiredRedPacketsScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        String luaScript = """
                local zsetKey   = KEYS[1]
                local nowArg    = ARGV[1]
                local maxCount  = tonumber(ARGV[2])

                if not maxCount or maxCount <= 0 then
                    maxCount = 500
                end

                -- 如未显式传入"当前时间"，则使用 Redis TIME 决定"现在"的毫秒时间戳
                local nowMs
                if not nowArg or nowArg == '' or tonumber(nowArg) <= 0 then
                    local t = redis.call('TIME')
                    nowMs = (tonumber(t[1]) * 1000) + math.floor(tonumber(t[2]) / 1000)
                else
                    nowMs = tonumber(nowArg)
                end

                -- 1) 取出最多 maxCount 条已到期成员
                local expired = redis.call('ZRANGEBYSCORE', zsetKey, '-inf', nowMs, 'LIMIT', 0, maxCount)

                if #expired == 0 then
                    return {}
                end

                -- 2) 精确删除本次取到的这些成员
                for i = 1, #expired do
                    redis.call('ZREM', zsetKey, expired[i])
                end

                -- 3) 将这批已到期成员返回给调用方
                return expired
                """;
        script.setScriptText(luaScript);
        script.setResultType(List.class);
        return script;
    }

    /**
     * 计算红包剩余金额 Lua 脚本
     * <p>
     * KEYS[1]: 红包金额池 List Key (使用 {@link com.shanyangcode.redpacketservice.constant.RedisKeyConstant#POOL_TEMPLATE})
     * <p>
     * 返回值: 剩余总金额 (单位：分)
     */
    @Bean
    public DefaultRedisScript<Long> calculateRemainAmountScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        String luaScript = """
                local lkey = KEYS[1]
                local len = redis.call('LLEN', lkey)
                if len == 0 then
                    return 0
                end

                local sum = 0
                local batch = 200
                local startIdx = 0
                while startIdx < len do
                    local stopIdx = math.min(startIdx + batch - 1, len - 1)
                    local vals = redis.call('LRANGE', lkey, startIdx, stopIdx)
                    for i = 1, #vals do
                        sum = sum + tonumber(vals[i])
                    end
                    startIdx = stopIdx + 1
                end
                -- 结算与销毁必须原子完成，否则退款期间池子仍可被 LPOP，同一份钱会付两次
                redis.call('DEL', lkey)
                
                return sum
                """;
        script.setScriptText(luaScript);
        script.setResultType(Long.class);
        return script;
    }

    /**
     * ShedLock 分布式锁提供者
     */
    @Bean
    public LockProvider lockProvider(RedisConnectionFactory connectionFactory) {
        return new RedisLockProvider(connectionFactory);
    }

    /**
     * StringRedisTemplate Bean
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}