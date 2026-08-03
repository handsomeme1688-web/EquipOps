package com.zoee.equipops.system.domain.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

@Data
public class AssignRolesDTO {

    @NotEmpty(message = "角色 ID 列表不能为空")
    private List<@Positive(message = "角色 ID 必须为正数") Long> roleIds;
}
