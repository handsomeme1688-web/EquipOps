package com.zoee.equipops.system.controller;

import com.zoee.equipops.common.annotation.OpLog;
import com.zoee.equipops.common.result.Result;
import com.zoee.equipops.system.service.RolePermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
public class RoleController {
    private final RolePermissionService rolePermissionService;

    @OpLog(resourceType = "role", action = "assignPermissions")
    @PreAuthorize("hasAuthority('system:role:manage')")
    @PostMapping("/{roleId}/permissions")
    public Result<Void> assignPermissions(@PathVariable Long roleId,@RequestBody List<Long> permissions) {
        rolePermissionService.assignPermission(roleId, permissions);
        return Result.success();
    }

}
