package com.zoee.equipops.system.controller;

import com.zoee.equipops.common.annotation.OpLog;
import com.zoee.equipops.common.result.Result;
import com.zoee.equipops.system.service.PermissionService;
import com.zoee.equipops.system.service.UserRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;


@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController {
    private final UserRoleService userRoleService;
    private final PermissionService permissionService;

    @OpLog(resourceType = "user", action = "assignRoles")
    @PreAuthorize("hasAuthority('system:role:manage')")
    @PostMapping("/{userId}/roles")
    public Result<Void> assignRoles(@PathVariable Long userId, @RequestBody List<Long> roleIds) {
        userRoleService.assignRole(userId, roleIds);
        return Result.success();
    }

    @GetMapping("/{userId}/permissions")
    @PreAuthorize("hasAuthority('system:role:view')")
    public Result<Set<String>> getPermissions(@PathVariable Long userId){
        return Result.success(permissionService.listPermissionCodesByUser(userId));
    }

}
