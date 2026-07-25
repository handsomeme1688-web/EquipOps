package com.zoee.equipops.auth.controller;

import com.zoee.equipops.auth.domain.dto.LoginDTO;
import com.zoee.equipops.auth.domain.dto.RegisterDTO;
import com.zoee.equipops.auth.domain.vo.CurrentUserVO;
import com.zoee.equipops.auth.domain.vo.TokenVO;
import com.zoee.equipops.auth.service.AuthService;
import com.zoee.equipops.common.context.UserContext;
import com.zoee.equipops.common.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    @PostMapping("/login")
    public Result<TokenVO> login(@Valid @RequestBody LoginDTO loginDTO){
        return Result.success(authService.login(loginDTO));
    }

    @PostMapping("/register")
    public Result<TokenVO> register(@Valid @RequestBody RegisterDTO registerDTO){
        return Result.success(authService.register(registerDTO));
    }

    @GetMapping("/me")
    public Result<CurrentUserVO> me(){
        Long userId = UserContext.getUserId();
        return Result.success(authService.me(userId));
    }
}
