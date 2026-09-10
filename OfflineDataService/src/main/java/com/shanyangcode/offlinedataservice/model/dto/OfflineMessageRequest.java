package com.shanyangcode.offlinedataservice.model.dto;

import lombok.Data;

@Data
public class OfflineMessageRequest {
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 离线时间戳（毫秒）
     */
    private Long offlineTime;
    
}
