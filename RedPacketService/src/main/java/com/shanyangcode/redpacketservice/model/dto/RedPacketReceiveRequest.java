package com.shanyangcode.redpacketservice.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 红包领取请求 DTO
 */
@Data
public class RedPacketReceiveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户 ID
     */
     @NotNull
    private Long userId;

    /**
     * 红包 ID
     */
     @NotNull
    private Long redPacketId;
}