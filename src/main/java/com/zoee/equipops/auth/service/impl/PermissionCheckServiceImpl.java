package com.zoee.equipops.auth.service.impl;

import com.zoee.equipops.auth.service.PermissionCheckService;
import com.zoee.equipops.system.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class PermissionCheckServiceImpl implements PermissionCheckService {
    private final PermissionService permissionService;

    @Override
    public boolean hasPerm(Long userId, String permCode) {
        Set<String> permissions = permissionService.listPermissionCodesByUser(userId);
        return permissions.contains(permCode);
    }
}
