package com.zoee.equipops.auth.domain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterDTO {
    @NotBlank(message = "用户名不能为空")
    @Size(max = 50, message = "用户名长度不超过 50 位")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(max = 255, message = "密码长度不超过 255 位")
    private String password;

    @NotBlank(message = "真实姓名不能为空")
    @Size(max = 50, message = "真实姓名长度不超过 50 位")
    private String realName;

    @NotNull(message = "所属部门不能为空")
    @Positive(message = "所属部门 ID 必须为正数")
    private Long deptId;

    @Size(max = 20, message = "手机号长度不超过 20 位")
    private String phone;

    @Email(message = "邮箱格式不正确")
    @Size(max = 50)
    private String email;
}
