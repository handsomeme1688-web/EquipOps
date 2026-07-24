package com.zoee.equipops.device.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.zoee.equipops.common.result.Result;
import com.zoee.equipops.device.domain.dto.DeviceCreateDTO;
import com.zoee.equipops.device.domain.dto.DeviceUpdateDTO;
import com.zoee.equipops.device.domain.entity.Device;
import com.zoee.equipops.device.domain.query.DeviceQuery;
import com.zoee.equipops.device.domain.vo.DeviceVO;

public interface DeviceService extends IService<Device> {
    DeviceVO create(DeviceCreateDTO deviceCreateDTO);
    DeviceVO update(Long id,DeviceUpdateDTO deviceUpdateDTO);
    void delete(Long id);
    DeviceVO detail(Long id);
    Page<DeviceVO> page(DeviceQuery deviceQuery);
}
