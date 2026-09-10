package com.shanyangcode.realtimeservice.utils;


import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import org.springframework.beans.factory.annotation.Value;

/**
 * 雪花 ID 生成工具类
 *
 * @author shanyang
 */
public class SnowflakeDynamicUtil {

    @Value("${snowflake.workerId}")
    private static long workerId;

    @Value("${snowflake.datacenterId}")
    private static long dataCenterId;

    private static final Snowflake SNOWFLAKE = IdUtil.getSnowflake(workerId, dataCenterId);

    /**
     * 生成雪花 ID
     *
     * @return 雪花 ID
     */
    public static long nextId() {
        return SNOWFLAKE.nextId();
    }
}