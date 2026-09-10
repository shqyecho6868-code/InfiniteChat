// Friend.java
package com.shanyangcode.userservice.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 好友关系表实体类
 *
 * 功能说明：
 * - 记录用户之间的好友关系
 * - 使用复合主键（user_id + friend_id）
 * - 支持好友状态管理（好友、拉黑、删除）
 * - 双向关系：A添加B为好友时，需同时创建两条记录
 *
 * 数据库表：friend
 *
 * 注意事项：
 * - 本表使用复合主键，无独立自增ID
 */
@Data
@TableName("friend")
@Accessors(chain = true)
public class Friend {

    /**
     * 用户ID（复合主键之一）
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 好友ID（复合主键之二）
     */
    @TableField("friend_id")
    private Long friendId;

    /**
     * 好友状态
     * 0: 好友（NORMAL）- 正常好友关系
     * 1: 拉黑（BLOCKED）- 已拉黑
     * 2: 删除（DELETED）- 已删除
     *
     */
    @TableField("status")
    private Integer status;

    /**
     * 创建时间
     */
    @TableField("created_time")
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    @TableField("updated_time")
    private LocalDateTime updatedTime;
}