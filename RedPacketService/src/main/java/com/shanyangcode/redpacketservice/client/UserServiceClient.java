package com.shanyangcode.redpacketservice.client;

import java.util.List;
import java.util.Map;

import com.shanyangcode.common.common.BaseResponse;
import com.shanyangcode.common.model.dto.validation.*;
import com.shanyangcode.common.model.vo.UserInfosResponse;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * UserService Feign 客户端
 *
 * 用于调用 UserService 的内部校验接口
 */
@FeignClient(name = "UserService", fallbackFactory = UserServiceClientFallbackFactory.class)
public interface UserServiceClient {

    /**
     * 查询用户状态
     *
     * @param userId 用户 ID
     * @return 用户状态信息
     */
    @GetMapping("/api/internal/user/status")
    BaseResponse<UserStatusResponse> getUserStatus(@RequestParam("userId") Long userId);


    /**
     * 验证群成员资格
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID（群聊 ID）
     * @return 群成员资格信息
     */
    @GetMapping("/api/internal/group/isMember")
    BaseResponse<GroupMembershipResponse> checkGroupMembership(
            @RequestParam("userId") Long userId,
            @RequestParam("sessionId") Long sessionId);

    /**
     * 单聊消息发送权限校验（复用现有接口）
     *
     * 校验内容：
     * 1. 发送者与接收者是否为正常好友关系
     * 2. 发送者是否被接收者拉黑
     * 3. 好友关系是否已删除
     *
     * 注意：该接口内部已包含 Redis 缓存逻辑
     *
     * @param request 校验请求
     * @return 校验结果
     */
    @PostMapping("/api/internal/validation/single-message")
    BaseResponse<MessageValidateResponse> validateSingleMessage(@RequestBody SingleMessageValidateRequest request);

    /**
     * 批量获取用户基本信息（昵称、头像）
     *
     * @param userIds 用户 ID 列表
     * @return Map<userId, UserInfosResponse>
     */
    @GetMapping("/api/internal/users/batch")
    BaseResponse<Map<Long, UserInfosResponse>> batchGetUserInfos(@RequestParam("userIds") List<Long> userIds);

    /**
     * 获取会话内全部成员 userId
     * <p>
     * UserService 已有该端点（UserSessionController /api/user/get/receivers），
     * 与 RealTimeService 推送侧使用的是同一个接口
     * <p>
     * 注意：该接口直接返回 List<Long>，没有包 BaseResponse
     *
     * @param sessionId 会话 ID
     * @return 会话成员 ID 列表
     */
    @GetMapping("/api/user/get/receivers")
    List<Long> getUserIdBySessionId(@RequestParam("sessionId") Long sessionId);
}