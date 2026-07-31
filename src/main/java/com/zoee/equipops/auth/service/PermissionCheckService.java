package com.zoee.equipops.auth.service;

public interface PermissionCheckService  {
    boolean hasPerm(Long userId,String permCode);
    default boolean isAdmin(Long userId){
        return hasPerm(userId,"system:role:manage");
    }
}
