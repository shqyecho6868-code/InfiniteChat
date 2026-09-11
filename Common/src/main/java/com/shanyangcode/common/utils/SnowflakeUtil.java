package com.shanyangcode.common.utils;

import com.shanyangcode.common.constant.SnowflakeConstant;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;

/**
 * 雪花 ID 生成工具类
 *
 * @author shanyang
 */
public class SnowflakeUtil {

    private static final Snowflake SNOWFLAKE = IdUtil.getSnowflake(
            SnowflakeConstant.WORKER_ID,
            SnowflakeConstant.DATA_CENTER_ID
    );

    /**
     * 生成雪花 ID
     *
     * @return 雪花 ID
     */
    public static long nextId() {
        return SNOWFLAKE.nextId();
    }
}