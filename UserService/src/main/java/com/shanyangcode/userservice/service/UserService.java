package com.shanyangcode.initproject.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.shanyangcode.initproject.model.dto.UserLoginCodeRequest;
import com.shanyangcode.initproject.model.dto.UserLoginPasswordRequest;
import com.shanyangcode.initproject.model.dto.UserRegisterRequest;
import com.shanyangcode.initproject.model.entity.User;
import com.shanyangcode.initproject.model.vo.LoginAndRegisterResponse;
import com.shanyangcode.initproject.model.vo.TokenResponse;
import com.shanyangcode.userservice.model.dto.UpdateAvatarRequest;
import com.shanyangcode.userservice.model.vo.UploadUrlResponse;

import java.util.Map;


public interface UserService extends IService<User> {

    void sendCaptcha(String targetEmail);


    LoginAndRegisterResponse register(UserRegisterRequest userRegisterRequest);


    LoginAndRegisterResponse loginPassword(UserLoginPasswordRequest userLoginPasswordRequest);



    LoginAndRegisterResponse loginCode(UserLoginCodeRequest userLoginCodeRequest);

    boolean logout(String userId);

    TokenResponse refreshToken(String refreshToken);

    String refreshUri(Long userId);

    UploadUrlResponse uploadUrl(String fileName) ;

    Boolean updateAvatar(UpdateAvatarRequest updateAvatarRequest);

    Map<Long, String> getUserNickName(Long sessionId);
}
