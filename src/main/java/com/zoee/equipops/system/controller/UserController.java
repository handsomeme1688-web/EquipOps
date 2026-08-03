package com.zoee.equipops.system.controller;

import com.zoee.equipops.common.annotation.OpLog;
import com.zoee.equipops.common.result.Result;
import com.zoee.equipops.system.domain.dto.AssignRolesDTO;
import com.zoee.equipops.system.service.PermissionService;
import com.zoee.equipops.system.service.UserRoleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Set;


@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
@Validated
public class UserController {
    private final UserRoleService userRoleService;
    private final PermissionService permissionService;

    @OpLog(resourceType = "user", action = "assignRoles")
    @PreAuthorize("hasAuthority('system:role:manage')")
    @PostMapping("/{userId}/roles")
    public Result<Void> assignRoles(
            @PathVariable @Positive(message = "用户 ID 必须为正数") Long userId,
            @Valid @RequestBody AssignRolesDTO request) {
        userRoleService.assignRole(userId, request.getRoleIds());
        return Result.success();
    }

    @GetMapping("/{userId}/permissions")
    @PreAuthorize("hasAuthority('system:role:view')")
    public Result<Set<String>> getPermissions(@PathVariable @Positive(message = "用户 ID 必须为正数") Long userId){
        return Result.success(permissionService.listPermissionCodesByUser(userId));
    }

}
