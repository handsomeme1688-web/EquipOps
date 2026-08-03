package com.zoee.equipops.system.domain.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

@Data
public class AssignPermissionsDTO {

    @NotEmpty(message = "权限 ID 列表不能为空")
    private List<@Positive(message = "权限 ID 必须为正数") Long> permissionIds;
}
