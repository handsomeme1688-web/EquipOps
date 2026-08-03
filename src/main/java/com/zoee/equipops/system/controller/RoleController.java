package com.zoee.equipops.system.controller;

import com.zoee.equipops.common.annotation.OpLog;
import com.zoee.equipops.common.result.Result;
import com.zoee.equipops.system.domain.dto.AssignPermissionsDTO;
import com.zoee.equipops.system.service.RolePermissionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
@Validated
public class RoleController {
    private final RolePermissionService rolePermissionService;

    @OpLog(resourceType = "role", action = "assignPermissions")
    @PreAuthorize("hasAuthority('system:role:manage')")
    @PostMapping("/{roleId}/permissions")
    public Result<Void> assignPermissions(
            @PathVariable @Positive(message = "角色 ID 必须为正数") Long roleId,
            @Valid @RequestBody AssignPermissionsDTO request) {
        rolePermissionService.assignPermission(roleId, request.getPermissionIds());
        return Result.success();
    }

}
