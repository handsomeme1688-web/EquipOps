package com.zoee.equipops.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zoee.equipops.common.exception.BizException;
import com.zoee.equipops.system.entity.RolePermission;
import com.zoee.equipops.system.mapper.RolePermissionMapper;
import com.zoee.equipops.system.service.RolePermissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class RolePermissionServiceImpl extends ServiceImpl<RolePermissionMapper,RolePermission> implements RolePermissionService {
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void assignPermission(Long roleId, List<Long> permissionIds) {
        remove(new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId,roleId));
        if (true) throw new RuntimeException("模拟异常，测试回滚");
//        写法一
        if (permissionIds != null && !permissionIds.isEmpty()) {
            List<RolePermission> newList=new ArrayList<>();
            for (Long pid : permissionIds) {
                RolePermission rolePermission = new RolePermission();
                rolePermission.setRoleId(roleId);
                rolePermission.setPermissionId(pid);
                newList.add(rolePermission);
            }
            saveBatch(newList);
        }


//        写法二
//        saveBatch(permissionIds.stream()
//                .map(pid->{// map() 要的是一个"变换配方":给我一个 pid,你还我一个变换后的东西
//                    RolePermission rolePermission = new RolePermission();
//                    rolePermission.setRoleId(roleId);
//                    rolePermission.setPermissionId(pid);
//                    return rolePermission;
//                })
//                .toList()
//        );
                /**
                 * toList 先建一个空 List
                 * ├─ 拉第1个 pid1 → 进 map 的 lambda → 造出 rp1 → 立刻塞进那个 List
                 * ├─ 拉第2个 pid2 → 进 map 的 lambda → 造出 rp2 → 立刻塞进那个 List
                 * └─ 拉第3个 pid3 → 进 map 的 lambda → 造出 rp3 → 立刻塞进那个 List
                 * List 建满，返回
                 */


    }
}
