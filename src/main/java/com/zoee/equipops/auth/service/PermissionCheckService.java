package com.zoee.equipops.auth.service;

public interface PermissionCheckService  {
    boolean hasPerm(Long userId,String permCode);
}
