package com.shanyangcode.userservice.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.shanyangcode.common.common.ErrorCode;
import com.shanyangcode.common.constant.SessionTypeConstant;
import com.shanyangcode.common.exception.BusinessException;
import com.shanyangcode.common.exception.ThrowUtils;
import com.shanyangcode.userservice.constant.FriendStatusEnum;
import com.shanyangcode.userservice.constant.UserConstant;
import com.shanyangcode.userservice.constant.UserStateEnum;
import com.shanyangcode.userservice.mapper.FriendMapper;
import com.shanyangcode.userservice.mapper.SessionMapper;
import com.shanyangcode.userservice.mapper.UserSessionMapper;
import com.shanyangcode.userservice.model.entity.*;
import com.shanyangcode.userservice.model.vo.FriendDetailVO;
import com.shanyangcode.userservice.service.FriendService;
import com.shanyangcode.userservice.service.UserService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 好友服务实现类
 * <p>
 * 核心改动：
 * - 使用Lambda Wrapper替代string-based查询
 * - 使用Kafka异步通知替代HTTP同步调用
 * - 处理复合主键（user_id, friend_id）
 * - 状态枚举值从1/2/3调整为0/1/2
 */
@Slf4j
@Service
public class FriendServiceImpl extends ServiceImpl<FriendMapper, Friend> implements FriendService {

    private final UserService userService;
    private final SessionMapper sessionMapper;
    private final UserSessionMapper userSessionMapper;

    public FriendServiceImpl(UserService userService,
                             SessionMapper sessionMapper,
                             UserSessionMapper userSessionMapper) {
        this.userService = userService;
        this.sessionMapper = sessionMapper;
        this.userSessionMapper = userSessionMapper;
    }

    @Override
    public FriendDetailVO searchUserByKeyword(String userId, String keyword) {
        ThrowUtils.throwIf(!StringUtils.hasText(keyword), ErrorCode.PARAMS_ERROR, "搜索关键字不能为空");

        // 根据正则表达式自动判断关键字类型并构建查询条件
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        if (keyword.matches(UserConstant.PHONE_REGEX)) {
            queryWrapper.eq(com.shanyangcode.initproject.model.entity.User::getPhone, keyword);
        } else if (keyword.matches(UserConstant.EMAIL_REGEX)) {
            queryWrapper.eq(com.shanyangcode.initproject.model.entity.User::getEmail, keyword);
        } else {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请输入有效的手机号或邮箱");
        }

        User user = userService.getOne(queryWrapper);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");

        // 获取用户详情
        return getFriendDetails(userId, String.valueOf(user.getUserId()));
    }


    /**
     * 获取好友的详细信息
     *
     * @param userId   当前用户Id
     * @param friendId 好友Id
     * @return FriendDetailVO 对象
     */
    @Override
    public FriendDetailVO getFriendDetails(String userId, String friendId) {
        Long userId1 = parseUserId(userId);
        Long friendId1 = parseUserId(friendId);

        // 1. 获取好友用户信息
        User friendUser = userService.getById(friendId1);
        validateFriendUser(friendUser);

        // 2. 构建好友详情VO
        FriendDetailVO friendDetailVO = buildFriendDetailVO(friendUser);

        // 3. 填充会话ID
        populateSessionId(userId1, friendId1, friendDetailVO);

        // 4. 填充好友状态
        populateFriendStatus(userId1, friendId1, friendDetailVO);

        return friendDetailVO;
    }


    /**
     * 解析并验证用户ID
     *
     * @param userId 用户Id字符串
     * @return 解析后的用户ID
     */
    private Long parseUserId(String userId) {
        try {
            return Long.valueOf(userId);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户ID格式错误");
        }
    }

    /**
     * 验证好友用户是否存在及其状态
     *
     * @param friendUser 好友的User实体
     */
    private void validateFriendUser(User friendUser) {
        ThrowUtils.throwIf(friendUser == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");

        // 用户状态：0 正常，1 封禁，2 注销
        ThrowUtils.throwIf(friendUser.getState() == UserStateEnum.BANNED.getCode(), ErrorCode.FORBIDDEN_ERROR, "该用户已被封禁");

        ThrowUtils.throwIf(friendUser.getState() == UserStateEnum.CANCELLED.getCode(), ErrorCode.NOT_FOUND_ERROR, "该用户已注销");
    }


    /**
     * 构建好友详细信息VO
     *
     * @param friendUser 好友的User实体
     * @return FriendDetailVO 对象
     */
    private FriendDetailVO buildFriendDetailVO(User friendUser) {
        FriendDetailVO vo = new FriendDetailVO();
        vo.setUserId(String.valueOf(friendUser.getUserId()));
        vo.setNickname(friendUser.getNickname());
        vo.setAvatar(friendUser.getAvatar());
        vo.setEmail(friendUser.getEmail());
        vo.setPhone(friendUser.getPhone());
        vo.setSignature(friendUser.getDescription());
        vo.setGender(friendUser.getGender());
        return vo;
    }

    /**
     * 填充会话ID到FriendDetailVO
     *
     * @param userId         当前用户ID
     * @param friendId       好友ID
     * @param friendDetailVO FriendDetailVO 对象
     */
    private void populateSessionId(Long userId, Long friendId, FriendDetailVO friendDetailVO) {
        // 1. 查找两个用户共同的单聊会话
        LambdaQueryWrapper<UserSession> userSession1Wrapper = new LambdaQueryWrapper<>();
        userSession1Wrapper.eq(UserSession::getUserId, userId);
        List<UserSession> userSessions1 = userSessionMapper.selectList(userSession1Wrapper);

        LambdaQueryWrapper<UserSession> userSession2Wrapper = new LambdaQueryWrapper<>();
        userSession2Wrapper.eq(UserSession::getUserId, friendId);
        List<UserSession> userSessions2 = userSessionMapper.selectList(userSession2Wrapper);

        // 2. 找出共同的会话ID
        List<Long> sessionIds1 = userSessions1.stream()
                .map(UserSession::getSessionId)
                .collect(Collectors.toList());
        List<Long> sessionIds2 = userSessions2.stream()
                .map(UserSession::getSessionId)
                .collect(Collectors.toList());

        sessionIds1.retainAll(sessionIds2);

        if (!sessionIds1.isEmpty()) {
            // 3. 筛选出单聊会话
            LambdaQueryWrapper<Session> sessionWrapper = new LambdaQueryWrapper<>();
            sessionWrapper.in(Session::getSessionId, sessionIds1)
                    .eq(Session::getType, SessionTypeConstant.SIGNAL_TYPE);
            List<Session> sessions = sessionMapper.selectList(sessionWrapper);

            if (!sessions.isEmpty()) {
                friendDetailVO.setSessionId(String.valueOf(sessions.get(0).getSessionId()));
            } else {
                friendDetailVO.setSessionId(null);
            }
        } else {
            friendDetailVO.setSessionId(null);
        }
    }


    /**
     * 填充好友状态到FriendDetailVO
     *
     * @param userId         当前用户ID
     * @param friendId       好友ID
     * @param friendDetailVO FriendDetailVO 对象
     */
    private void populateFriendStatus(Long userId, Long friendId, FriendDetailVO friendDetailVO) {
        LambdaQueryWrapper<Friend> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Friend::getUserId, userId)
                .eq(Friend::getFriendId, friendId);
        Friend friend = this.getOne(wrapper);

        if (friend != null) {
            friendDetailVO.setStatus(friend.getStatus());
        } else {
            friendDetailVO.setStatus(FriendStatusEnum.NON_FRIEND.getCode());
        }
    }

    /**
     * 获取用户的好友列表
     * 支持分页和关键字搜索
     *
     * @param userId1      用户ID
     * @param pageRequest 分页参数
     * @param key         搜索关键字
     * @return 分页的好友DTO列表
     */
    @Override
    public IPage<FriendDTO> getFriends(String userId1, PageRequest pageRequest, String key) {
        Long userId = parseUserId(userId1);
        validateUserId(userId);

        int pageNum = pageRequest.getPageNum();
        int pageSize = pageRequest.getPageSize();

        // 1. 查询好友关系列表（使用Lambda Wrapper）
        LambdaQueryWrapper<Friend> friendWrapper = new LambdaQueryWrapper<>();
        friendWrapper.eq(Friend::getUserId, userId)
                .ne(Friend::getStatus, FriendStatusEnum.DELETED.getCode())
                .orderByDesc(Friend::getCreatedTime); // 按创建时间降序排列

        List<Friend> friendList = friendMapper.selectList(friendWrapper);

        // 2. 获取好友ID列表，从好友列表中提取所有好友的ID，组成一个新的ID列表。
        List<Long> friendIds = friendList.stream() // 将好友列表转换成流
                .map(Friend::getFriendId) // 对每个好友对象，提取其 friendId（映射转换）
                .collect(Collectors.toList()); // 将所有ID收集成一个 List<Long> 类型的列表

        if (friendIds.isEmpty()) {
            // 返回空分页结果
            Page<FriendDTO> emptyPage = new Page<>(pageNum, pageSize);
            emptyPage.setTotal(0);
            emptyPage.setRecords(List.of());
            return emptyPage;
        }

        // 3. 查询好友用户信息
        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.in(com.shanyangcode.initproject.model.entity.User::getUserId, friendIds);

        // 如果有搜索关键字，添加搜索条件
        if (StringUtils.hasText(key)) {
            userWrapper.and(wrapper -> wrapper
                    .like(com.shanyangcode.initproject.model.entity.User::getNickname, key)
                    .or()
                    .like(com.shanyangcode.initproject.model.entity.User::getPhone, key)
                    .or()
                    .like(com.shanyangcode.initproject.model.entity.User::getUserId, key));
        }

        List<User> users = userService.list(userWrapper); // 好友信息列表

        // 4. 查询好友与当前用户之间的会话ID映射（批量查询优化）
        Map<Long, String> friendSessionMap = buildFriendSessionMap(userId, friendIds);

        // 5. 构建好友ID到好友关系的映射（避免嵌套stream，O(n+m)），n = friendList 的大小（好友关系数量），m = users 的大小（查询到的好友用户信息数量）
        Map<Long, Friend> friendRelationMap = friendList.stream()
                .collect(Collectors.toMap(Friend::getFriendId, f -> f)); // 创建一个 Map，将好友关系列表转换为 Map，好友ID为key、好友对象为value。

        // 6. 构建 FriendDTO 列表
        List<FriendDTO> friendDTOList = users.stream()
                .map(user -> {
                    FriendDTO dto = new FriendDTO();
                    dto.setUserId(String.valueOf(user.getUserId())); // 设置dto的ID（好友用户ID）
                    dto.setNickname(user.getNickname());
                    dto.setAvatar(user.getAvatar());
                    dto.setSignature(user.getDescription());
                    Friend friendRelation = friendRelationMap.get(user.getUserId()); // 通过用户ID从映射表中获取对应的好友关系
                    dto.setStatus(friendRelation != null ? friendRelation.getStatus() : FriendStatusEnum.NON_FRIEND.getCode()); // 如果存在好友关系则取其状态，否则标记为非好友
                    dto.setSessionId(friendSessionMap.get(user.getUserId())); // 从会话映射中获取该用户与当前用户的聊天会话ID
                    return dto;
                })
                .collect(Collectors.toList());

        // 7. 手动分页
        // 创建分页对象：初始化页码和每页大小
        Page<FriendDTO> page = new Page<>(pageNum, pageSize);
        page.setTotal(friendDTOList.size());

        // 计算索引范围：根据页码计算起始和结束位置，防止越界
        int fromIndex = (pageNum - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, friendDTOList.size());

        // 截取数据：使用 subList 提取当前页数据，若超出范围则返回空列表
        if (fromIndex < friendDTOList.size()) {
            page.setRecords(friendDTOList.subList(fromIndex, toIndex));
        } else {
            page.setRecords(List.of());
        }

        return page;
    }


    /**
     * 批量构建好友与会话ID的映射关系
     * <p>
     * 优化查询性能，避免N+1问题
     *
     * @param userId    当前用户ID
     * @param friendIds 好友ID列表
     * @return 好友ID到会话ID的映射
     */
    private Map<Long, String> buildFriendSessionMap(Long userId, List<Long> friendIds) {
        Map<Long, String> friendSessionMap = new HashMap<>();

        if (friendIds.isEmpty()) {
            return friendSessionMap;
        }

        // 1. 查询当前用户的所有 UserSession
        LambdaQueryWrapper<UserSession> currentUserSessionWrapper = new LambdaQueryWrapper<>();
        currentUserSessionWrapper.eq(UserSession::getUserId, userId);
        List<UserSession> currentUserSessions = userSessionMapper.selectList(currentUserSessionWrapper);

        if (currentUserSessions.isEmpty()) {
            return friendSessionMap;
        }

        // 2. 获取当前用户的所有会话ID
        // 把上面的 currentUserSessions 对象转换成会话ID列表
        List<Long> currentUserSessionIds = currentUserSessions.stream()
                .map(UserSession::getSessionId)
                .collect(Collectors.toList());

        // 3. 查询这些会话的详细信息，筛选出单聊会话（SIGNAL_TYPE）
        LambdaQueryWrapper<Session> sessionWrapper = new LambdaQueryWrapper<>();
        sessionWrapper.in(Session::getSessionId, currentUserSessionIds)
                .eq(Session::getType, SessionTypeConstant.SIGNAL_TYPE);
        List<Session> singleChatSessions = sessionMapper.selectList(sessionWrapper);

        if (singleChatSessions.isEmpty()) {
            return friendSessionMap;
        }

        // 4. 获取单聊会话ID列表
        List<Long> singleChatSessionIds = singleChatSessions.stream()
                .map(Session::getSessionId)
                .collect(Collectors.toList());

        // 5. 查询所有好友在这些会话中的 UserSession 记录
        LambdaQueryWrapper<UserSession> friendSessionWrapper = new LambdaQueryWrapper<>();
        friendSessionWrapper.in(UserSession::getUserId, friendIds)
                .in(UserSession::getSessionId, singleChatSessionIds);
        List<UserSession> friendUserSessions = userSessionMapper.selectList(friendSessionWrapper);

        // 6. 构建 friendId -> sessionId 映射
        for (UserSession friendUserSession : friendUserSessions) {
            Long friendId = friendUserSession.getUserId();
            Long sessionId = friendUserSession.getSessionId();

            // 验证这个会话是否确实是当前用户和该好友的共同会话 double check
            if (currentUserSessionIds.contains(sessionId)) {
                friendSessionMap.put(friendId, String.valueOf(sessionId));
            }
        }

        return friendSessionMap;
    }


    /**
     * 校验用户ID的有效性
     *
     * @param userId 用户ID
     */
    private void validateUserId(Long userId) {
        ThrowUtils.throwIf(userId == null || userId < 0, ErrorCode.PARAMS_ERROR, "用户ID无效");
    }


    /**
     * 批量构建好友与会话ID的映射关系
     * <p>
     * 优化查询性能，避免N+1问题
     *
     * @param userId    当前用户ID
     * @param friendIds 好友ID列表
     * @return 好友ID到会话ID的映射
     */
    private java.util.Map<Long, String> buildFriendSessionMap(Long userId, List<Long> friendIds) {
        java.util.Map<Long, String> friendSessionMap = new java.util.HashMap<>();

        if (friendIds.isEmpty()) {
            return friendSessionMap;
        }

        // 1. 查询当前用户的所有 UserSession
        LambdaQueryWrapper<UserSession> currentUserSessionWrapper = new LambdaQueryWrapper<>();
        currentUserSessionWrapper.eq(UserSession::getUserId, userId);
        List<UserSession> currentUserSessions = userSessionMapper.selectList(currentUserSessionWrapper);

        if (currentUserSessions.isEmpty()) {
            return friendSessionMap;
        }

        // 2. 获取当前用户的所有会话ID
        List<Long> currentUserSessionIds = currentUserSessions.stream()
                .map(UserSession::getSessionId)
                .collect(Collectors.toList());

        // 3. 查询这些会话的详细信息，筛选出单聊会话（SIGNAL_TYPE）
        LambdaQueryWrapper<Session> sessionWrapper = new LambdaQueryWrapper<>();
        sessionWrapper.in(Session::getSessionId, currentUserSessionIds)
                .eq(Session::getType, SessionTypeConstant.SIGNAL_TYPE);
        List<Session> singleChatSessions = sessionMapper.selectList(sessionWrapper);

        if (singleChatSessions.isEmpty()) {
            return friendSessionMap;
        }

        // 4. 获取单聊会话ID列表
        List<Long> singleChatSessionIds = singleChatSessions.stream()
                .map(Session::getSessionId)
                .collect(Collectors.toList());

        // 5. 查询所有好友在这些会话中的 UserSession 记录
        LambdaQueryWrapper<UserSession> friendSessionWrapper = new LambdaQueryWrapper<>();
        friendSessionWrapper.in(UserSession::getUserId, friendIds)
                .in(UserSession::getSessionId, singleChatSessionIds);
        List<UserSession> friendUserSessions = userSessionMapper.selectList(friendSessionWrapper);

        // 6. 构建 friendId -> sessionId 映射
        for (UserSession friendUserSession : friendUserSessions) {
            Long friendId = friendUserSession.getUserId();
            Long sessionId = friendUserSession.getSessionId();

            // 验证这个会话是否确实是当前用户和该好友的共同会话
            if (currentUserSessionIds.contains(sessionId)) {
                friendSessionMap.put(friendId, String.valueOf(sessionId));
            }
        }

        return friendSessionMap;
    }

}