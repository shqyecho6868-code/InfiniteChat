package com.shanyangcode.userservice.controller;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shanyangcode.common.common.BaseResponse;
import com.shanyangcode.common.common.ErrorCode;
import com.shanyangcode.common.common.ResultUtils;
import com.shanyangcode.common.exception.BusinessException;
import com.shanyangcode.common.model.dto.PageRequest;
import com.shanyangcode.userservice.model.dto.ApplyFriendDTO;
import com.shanyangcode.userservice.model.dto.FriendDTO;
import com.shanyangcode.common.model.dto.PageResponse;
import com.shanyangcode.userservice.model.dto.ModifyFriendApplicationResponse;
import com.shanyangcode.userservice.model.dto.request.AddFriendRequest;
import com.shanyangcode.userservice.model.dto.request.ModifyFriendApplicationRequest;
import com.shanyangcode.userservice.model.vo.FriendDetailVO;
import com.shanyangcode.userservice.service.ApplyFriendService;
import com.shanyangcode.userservice.service.FriendService;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 联系人Controller
 * <p>
 * 功能说明：
 * - 提供好友相关的REST API接口
 * - 包含好友申请、好友管理、好友查询等功能
 * - 支持分页查询和关键字搜索
 */
@Slf4j
@RestController
@RequestMapping("/api/contact")
public class ContactController {
    private final FriendService friendService;
    private final ApplyFriendService applyFriendService;

    public ContactController(FriendService friendService, ApplyFriendService applyFriendService) {
        this.friendService = friendService;
        this.applyFriendService = applyFriendService;
    }

/**
 * 搜索用户（手机号或邮箱）
 *
 * @param userId  用户ID
 * @param keyword 搜索关键字（手机号或邮箱）
 * @return 用户详情
 */
    @GetMapping("/{userId}/user/search")
    public BaseResponse<?> searchUser(
            @PathVariable("userId") String userId,
            @RequestParam(value = "keyword") String keyword) {
        try {
            FriendDetailVO friendDetail = friendService.searchUserByKeyword(userId, keyword);
            return ResultUtils.success(friendDetail);
        } catch (BusinessException e) {
            log.error("搜索用户失败，用户ID：{}，关键字：{}，原因：{}", userId, keyword, e.getMessage());
            return ResultUtils.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("搜索用户失败，用户ID：{}，关键字：{}，原因：{}", userId, keyword, e.getMessage(), e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }

    /**
     * 获取联系人列表
     *
     * @param userId      用户ID
     * @param pageRequest 分页参数
     * @param key         查询关键字
     * @return 联系人列表（分页）
     */
    @GetMapping("/{userId}/friend")
    public BaseResponse<?> getFriends(
            @PathVariable("userId") String userId,
            PageRequest pageRequest,
            @RequestParam(value = "key", defaultValue = "") String key) {
        try {
            // 查询分页数据
            IPage<FriendDTO> friendsPage = friendService.getFriends(userId, pageRequest, key);

            // 使用 PageResponse 统一返回格式
            return ResultUtils.success(PageResponse.of(friendsPage));
        } catch (BusinessException e) {
            log.error("获取好友列表失败，用户ID：{}，原因：{}", userId, e.getMessage());
            return ResultUtils.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("获取好友列表失败，用户ID：{}，原因：{}", userId, e.getMessage(), e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }

    /**
     * 发送好友申请
     *
     * @param userId        发送者用户ID
     * @param receiveuserId 接收者用户ID
     * @param request         申请信息
     * @return 是否成功
     */
    @PostMapping("/{userId}/friend/{receiveuserId}")
    public BaseResponse<?> sendFriendRequest(
            @PathVariable("userId") String userId,
            @PathVariable("receiveuserId") String receiveuserId,
            @Valid @RequestBody AddFriendRequest request) {
        try {
            Long senderId = Long.valueOf(userId);
            Long receiverId = Long.valueOf(receiveuserId);
            Long applyFriendId = applyFriendService.sendFriendRequest(senderId, receiverId, request.getMsg());
            return ResultUtils.success(applyFriendId != null);
        } catch (NumberFormatException e) {
            log.error("发送好友申请失败，用户ID格式错误，发送者：{}，接收者：{}", userId, receiveuserId);
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "用户ID格式错误");
        } catch (BusinessException e) {
            log.error("发送好友申请失败，发送者：{}，接收者：{}，原因：{}", userId, receiveuserId, e.getMessage());
            return ResultUtils.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("发送好友申请失败，发送者：{}，接收者：{}，原因：{}", userId, receiveuserId, e.getMessage(), e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public BaseResponse<?> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        log.error("请求体解析失败: {}", e.getMessage());
        return ResultUtils.error(ErrorCode.INVALID_PARAMETER_ERROR, "请求体格式错误或为空");
    }

    /**
     * 获取好友申请列表
     *
     * @param userId      用户ID
     * @param pageRequest 分页参数
     * @return 申请列表（分页）
     */
    @GetMapping("/{userId}/apply")
    public BaseResponse<?> getApplyList(
            @PathVariable Long userId,
            PageRequest pageRequest) {
        try {
            // 查询分页数据
            IPage<ApplyFriendDTO> applyFriendDTOPage = applyFriendService.getReceivedRequestsWithUserInfo(
                    userId, pageRequest);

            // 使用 PageResponse 统一返回格式
            return ResultUtils.success(PageResponse.of(applyFriendDTOPage));
        } catch (BusinessException e) {
            log.error("获取好友申请列表失败，用户ID：{}，原因：{}", userId, e.getMessage());
            return ResultUtils.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("获取好友申请列表失败，用户ID：{}，原因：{}", userId, e.getMessage(), e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }

    /**
     * 获取未读好友申请数量
     *
     * @param userId 用户ID
     * @return 未读好友申请数量
     */
    @GetMapping("/{userId}/applyCount")
    public BaseResponse<?> getUnreadApplyCount(@PathVariable Long userId) {
        try {
            int count = applyFriendService.getUnreadCount(userId);
            HashMap<String, Integer> map = new HashMap<>();
            map.put("count", count);
            return ResultUtils.success(map);
        } catch (BusinessException e) {
            log.error("获取未读好友申请数量失败，用户ID：{}，原因：{}", userId, e.getMessage());
            return ResultUtils.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("获取未读好友申请数量失败，用户ID：{}，原因：{}", userId, e.getMessage(), e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }

    /**
     * 删除好友
     *
     * @param userId        用户ID
     * @param receiveuserId 删除的好友ID
     * @return 是否成功
     */
    @DeleteMapping("/{userId}/friend/{receiveuserId}")
    public BaseResponse<?> deleteFriend(
            @PathVariable String userId,
            @PathVariable String receiveuserId) {
        try {
            Long userIdL = Long.valueOf(userId);
            Long friendId = Long.valueOf(receiveuserId);
            boolean result = friendService.deleteFriend(userIdL, friendId);
            return ResultUtils.success(result);
        } catch (NumberFormatException e) {
            log.error("删除好友失败，用户ID格式错误，用户：{}，好友：{}", userId, receiveuserId);
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "用户ID格式错误");
        } catch (BusinessException e) {
            log.error("删除好友失败，用户：{}，好友：{}，原因：{}", userId, receiveuserId, e.getMessage());
            return ResultUtils.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("删除好友失败，用户：{}，好友：{}，原因：{}", userId, receiveuserId, e.getMessage(), e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }

    /**
     * 拉黑好友
     *
     * @param userId        用户ID
     * @param receiveuserId 拉黑的好友ID
     * @return 是否成功
     */
    @PostMapping("/{userId}/block/{receiveuserId}")
    public BaseResponse<?> blockFriend(
            @PathVariable String userId,
            @PathVariable String receiveuserId) {
        try {
            Long userIdL = Long.valueOf(userId);
            Long friendId = Long.valueOf(receiveuserId);
            boolean result = friendService.blockFriend(userIdL, friendId);
            return ResultUtils.success(result);
        } catch (NumberFormatException e) {
            log.error("拉黑好友失败，用户ID格式错误，用户：{}，好友：{}", userId, receiveuserId);
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "用户ID格式错误");
        } catch (BusinessException e) {
            log.error("拉黑好友失败，用户：{}，好友：{}，原因：{}", userId, receiveuserId, e.getMessage());
            return ResultUtils.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("拉黑好友失败，用户：{}，好友：{}，原因：{}", userId, receiveuserId, e.getMessage(), e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }

    /**
     * 取消拉黑好友
     *
     * @param userId        用户ID
     * @param receiveuserId 取消拉黑的好友ID
     * @return 是否成功
     */
    @DeleteMapping("/{userId}/block/{receiveuserId}")
    public BaseResponse<?> unblockFriend(
            @PathVariable String userId,
            @PathVariable String receiveuserId) {
        try {
            Long userIdL = Long.valueOf(userId);
            Long friendId = Long.valueOf(receiveuserId);
            boolean result = friendService.unblockFriend(userIdL, friendId);
            return ResultUtils.success(result);
        } catch (NumberFormatException e) {
            log.error("取消拉黑好友失败，用户ID格式错误，用户：{}，好友：{}", userId, receiveuserId);
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "用户ID格式错误");
        } catch (BusinessException e) {
            log.error("取消拉黑好友失败，用户：{}，好友：{}，原因：{}", userId, receiveuserId, e.getMessage());
            return ResultUtils.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("取消拉黑好友失败，用户：{}，好友：{}，原因：{}", userId, receiveuserId, e.getMessage(), e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }

    /**
     * 修改好友申请状态
     *
     * @param userId  用户ID
     * @param status  状态（1:通过、2:拒绝、3:已读）
     * @param request 用户ID列表
     * @return 响应结果
     */
    @PostMapping("/{userId}/application/{status}")
    public BaseResponse<?> modifyFriendApplicationStatus(
            @PathVariable("userId") String userId,
            @PathVariable("status") Integer status,
            @Valid @RequestBody ModifyFriendApplicationRequest request) {
        try {
            Long receiverId = Long.valueOf(userId);
            List<Long> senderIds = request.getReceiveuserIds().stream()
                    .map(Long::valueOf)
                    .collect(Collectors.toList());

            ModifyFriendApplicationResponse response = applyFriendService.modifyApplicationStatus(receiverId, senderIds, status);

            // 通过申请时返回会话信息，其他情况返回true
            return ResultUtils.success(response != null ? response : true);
        } catch (NumberFormatException e) {
            log.error("修改好友申请状态失败，用户ID格式错误，用户：{}", userId);
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "用户ID格式错误");
        } catch (IllegalArgumentException e) {
            log.error("修改好友申请状态失败，状态值无效，用户：{}，状态：{}", userId, status);
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "不允许修改为该状态值");
        } catch (BusinessException e) {
            log.error("修改好友申请状态失败，用户：{}，状态：{}，原因：{}", userId, status, e.getMessage());
            return ResultUtils.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("修改好友申请状态失败，用户：{}，状态：{}，原因：{}", userId, status, e.getMessage(), e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }


    /**
     * 获取好友详情
     *
     * @param userId   用户ID
     * @param friendId 好友ID
     * @return 好友详情
     */
    @GetMapping("/{userId}/friend/{friendId}")
    public BaseResponse<?> getFriendDetail(
            @PathVariable("userId") String userId,
            @PathVariable("friendId") String friendId) {
        try {
            FriendDetailVO friendDetail = friendService.getFriendDetails(userId, friendId);
            return ResultUtils.success(friendDetail);
        } catch (BusinessException e) {
            log.error("获取好友详情失败，用户：{}，好友：{}，原因：{}", userId, friendId, e.getMessage());
            return ResultUtils.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("获取好友详情失败，用户：{}，好友：{}，原因：{}", userId, friendId, e.getMessage(), e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR);
        }
    }

}
