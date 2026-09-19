package com.shanyangcode.redpacketservice.model.vo;

import com.shanyangcode.redpacketservice.constant.RedPacketConstant;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 红包领取结果 VO
 * <p>
 * 专用于领取红包接口 /api/chat/redPacket/receive 的返回值
 */
@Data
public class ReceiveResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 红包状态
     * <p>
     * 参见 {@link RedPacketConstant}:
     * <ul>
     *   <li>STATUS_NOT_COMPLETED (0): 红包未领完（领取成功、已经领取过且红包仍有剩余、反常态兜底）</li>
     *   <li>STATUS_COMPLETED (1): 红包已领完</li>
     *   <li>STATUS_EXPIRED (2): 红包已过期</li>
     *   <li>STATUS_NOT_EXIST (-1): 红包不存在</li>
     * </ul>
     */
    private Integer status;

    /**
     * 结果消息
     */
    private String message;

    /**
     * 领取金额（单位：元）
     * <p>
     * 仅当领取成功、或用户已经领取过（可从 records 读回历史金额）时有值
     */
    private BigDecimal amount;
}