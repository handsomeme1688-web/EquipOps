package com.zoee.equipops.auth.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zoee.equipops.auth.domain.dto.LoginDTO;
import com.zoee.equipops.auth.domain.dto.RegisterDTO;
import com.zoee.equipops.auth.domain.vo.CurrentUserVO;
import com.zoee.equipops.auth.domain.vo.TokenVO;
import com.zoee.equipops.auth.service.AuthService;
import com.zoee.equipops.auth.util.JwtUtil;
import com.zoee.equipops.common.exception.BizException;
import com.zoee.equipops.system.entity.Dept;
import com.zoee.equipops.system.entity.User;
import com.zoee.equipops.system.mapper.DeptMapper;
import com.zoee.equipops.system.mapper.PermissionMapper;
import com.zoee.equipops.system.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static com.zoee.equipops.common.result.ResultCode.*;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl extends ServiceImpl<UserMapper, User> implements AuthService {
    private final JwtUtil jwtUtil;
    private final DeptMapper deptMapper;
    private final PermissionMapper permissionMapper;
    @Override
    public TokenVO login(LoginDTO loginDTO) {
        User existUser = baseMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername,loginDTO.getUsername()));

        if (existUser==null) throw new BizException(BAD_CREDENTIALS);
        if (!BCrypt.checkpw(loginDTO.getPassword(),existUser.getPassword())) throw new BizException(BAD_CREDENTIALS);

        Map<String,Object> claim=new HashMap<>();
        claim.put("userId",existUser.getId());
        claim.put("deptId",existUser.getDeptId());
        String token = jwtUtil.generateJwt(claim);

        TokenVO tokenVO =new TokenVO();
        tokenVO.setAccessToken(token);
        tokenVO.setTokenType("Bearer"); // 固定 "Bearer"
        tokenVO.setExpiresIn(jwtUtil.getExpireSeconds());

        return tokenVO;
    }

    @Override
    public TokenVO register(RegisterDTO registerDTO) {
        boolean exists=lambdaQuery().eq(User::getUsername,registerDTO.getUsername()).exists();
        if(exists) throw new BizException(USERNAME_EXISTS);

        User user = new User();
        user.setUsername(registerDTO.getUsername());
        user.setPassword(BCrypt.hashpw(registerDTO.getPassword()));
        user.setRealName(registerDTO.getRealName());
        user.setDeptId(registerDTO.getDeptId());
        user.setPhone(registerDTO.getPhone());
        user.setEmail(registerDTO.getEmail());
        user.setStatus((byte) 1);
        user.setPwdUpdateTime(java.time.LocalDateTime.now());
        save(user);

        Long userId = user.getId();
        Map<String,Object> claim = new HashMap<>();
        claim.put("userId",userId);
        claim.put("deptId",user.getDeptId());
        String token = jwtUtil.generateJwt(claim);

        TokenVO tokenVO = new TokenVO();
        tokenVO.setAccessToken(token);
        tokenVO.setTokenType("Bearer"); // 固定 "Bearer"
        tokenVO.setExpiresIn(jwtUtil.getExpireSeconds());

        return tokenVO;
    }

    @Override
    public CurrentUserVO me(Long userId) {
        User existsUser = baseMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getId, userId));

        if(existsUser==null) throw new BizException(BAD_CREDENTIALS);
        CurrentUserVO currentUserVO = new CurrentUserVO();
        currentUserVO.setUserId(existsUser.getId());
        currentUserVO.setUsername(existsUser.getUsername());
        currentUserVO.setRealName(existsUser.getRealName());
        currentUserVO.setDeptId(existsUser.getDeptId());
        Dept dept = deptMapper.selectById(existsUser.getDeptId());
        currentUserVO.setDeptName(dept!=null?dept.getName():"未知部门");
        currentUserVO.setPermissions(new HashSet<>(permissionMapper.selectPermissionCodesByUser(existsUser.getId())));
        return currentUserVO;
    }


}
