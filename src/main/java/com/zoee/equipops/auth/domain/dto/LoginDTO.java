package com.zoee.equipops.auth.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginDTO {
    @NotBlank(message = "用户名不能为空")
    @Size(max = 50, message = "用户名长度不超过 50 位")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(max = 255, message = "密码长度不超过 255 位")
    private String password;
}
