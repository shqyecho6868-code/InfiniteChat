// UserInfosResponse.java（位于 Common 模块）
package com.shanyangcode.common.model.vo;

import lombok.Data;

@Data
public class UserInfosResponse {

    private Long userId;

    private String nickname;

    private String avatar;
}