package com.shanyangcode.common.model.dto;

import java.io.Serial;
import java.io.Serializable;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.Data;

/**
 * 统一分页请求参数
 * <p>
 * 使用方式: Query Params: ?pageNum=1&pageSize=20
 */
@Data
public class PageRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 当前页码（从 1 开始）
     */
    private Integer pageNum = 1;

    /**
     * 每页大小
     */
    private Integer pageSize = 20;

    public Integer getPageNum() {
        return pageNum == null ? 1 : pageNum;
    }

    public Integer getPageSize() {
        return pageSize == null ? 20 : Math.min(pageSize, 100);
    }

    /**
     * 转换为 MyBatis-Plus Page 对象
     */
    public <T> Page<T> toPage() {
        int num = getPageNum();
        int size = getPageSize();
        return new Page<>(num, size);
    }
}