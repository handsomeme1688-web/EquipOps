package com.zoee.equipops.auth.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zoee.equipops.auth.domain.dto.LoginDTO;
import com.zoee.equipops.auth.domain.dto.RegisterDTO;
import com.zoee.equipops.auth.domain.vo.CurrentUserVO;
import com.zoee.equipops.auth.domain.vo.TokenVO;
import com.zoee.equipops.system.entity.User;


public interface AuthService extends IService<User> {
    TokenVO login(LoginDTO loginDTO);
    TokenVO register(RegisterDTO registerDTO);
    CurrentUserVO me(Long userId);
}
