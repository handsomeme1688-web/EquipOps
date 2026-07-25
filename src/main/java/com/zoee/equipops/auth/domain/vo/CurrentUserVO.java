package com.zoee.equipops.auth.domain.vo;

import lombok.Data;

import java.util.Set;

@Data
public class CurrentUserVO {
    private Long userId;              // 主键，后续操作（创建工单等）需要
    private String username;          // 登录名
    private String realName;          // 真实姓名，右上角显示"张三"
    private Long deptId;             // 所属部门 ID
    private String deptName;         // 所属部门名，页面显示用
    private Set<String> permissions; // 权限码集合，前端据此控制按钮显隐
}
