package com.zoee.equipops.device.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zoee.equipops.common.exception.BizException;
import com.zoee.equipops.common.result.ResultCode;
import com.zoee.equipops.device.domain.dto.DeviceCreateDTO;
import com.zoee.equipops.device.domain.dto.DeviceUpdateDTO;
import com.zoee.equipops.device.domain.entity.Device;
import com.zoee.equipops.device.domain.query.DeviceQuery;
import com.zoee.equipops.device.domain.vo.DeviceVO;
import com.zoee.equipops.device.enums.DeviceStatus;
import com.zoee.equipops.device.mapper.DeviceMapper;
import com.zoee.equipops.device.service.DeviceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;



@Service
public class DeviceServiceImpl extends ServiceImpl<DeviceMapper, Device> implements DeviceService {


    private DeviceVO toVO(Device device) {
        if (device == null) return null;
        DeviceVO vo = new DeviceVO();
        vo.setId(device.getId());
        vo.setCode(device.getCode());
        vo.setName(device.getName());
        vo.setModel(device.getModel());
        vo.setLocation(device.getLocation());
        vo.setStatus(device.getStatus());
        vo.setDescription(device.getDescription());
        vo.setDeptId(device.getDeptId());
        vo.setOwnerId(device.getOwnerId());
        vo.setCreateTime(device.getCreateTime());
        vo.setUpdateTime(device.getUpdateTime());
        // TODO Day 9: deptName/ownerName 需 JOIN，暂留 null
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class) // 用于事务，纯读操作不加
    public DeviceVO create(DeviceCreateDTO deviceCreateDTO) {
        // 查重,只看是否存在，不获取整个数据内容，速度更快
        boolean existDevice= lambdaQuery().eq(Device::getCode,deviceCreateDTO.getCode()).exists();
        if(existDevice) throw new BizException(ResultCode.DEVICE_CODE_EXISTS);

        // 保存
        Device device=new Device();
        device.setOwnerId(deviceCreateDTO.getOwnerId());
        device.setCode(deviceCreateDTO.getCode());
        device.setName(deviceCreateDTO.getName());
        device.setModel(deviceCreateDTO.getModel());
        device.setLocation(deviceCreateDTO.getLocation());
        device.setDescription(deviceCreateDTO.getDescription());

        // TODO Day 11: 从 UserContext 取当前登录用户
//        Long currentUserId = 1L;      // 暂时写死
        Long currentDeptId = 2L;      // 暂时写死
        device.setDeptId(currentDeptId);
//        device.setCreateBy(currentUserId);

        device.setStatus(DeviceStatus.NORMAL);
        save(device);

        return toVO(device);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceVO update(Long id, DeviceUpdateDTO deviceUpdateDTO) {
        Device existDevice=getById(id);
        if(existDevice == null) throw new BizException(ResultCode.DEVICE_NOT_FOUND);
        existDevice.setOwnerId(deviceUpdateDTO.getOwnerId());
        existDevice.setLocation(deviceUpdateDTO.getLocation());
        existDevice.setName(deviceUpdateDTO.getName());
        existDevice.setModel(deviceUpdateDTO.getModel());
        existDevice.setDescription(deviceUpdateDTO.getDescription());
        updateById(existDevice);
        return toVO(existDevice);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if(getById(id) == null) throw new BizException(ResultCode.DEVICE_NOT_FOUND);
        removeById(id);
    }

    @Override
    public DeviceVO detail(Long id) {
        Device existDevice = getById(id);
        if (existDevice == null) throw new BizException(ResultCode.DEVICE_NOT_FOUND);
        return toVO(existDevice);
    }

    @Override
    public Page<DeviceVO> page(DeviceQuery deviceQuery) {
        // 第 1 步：构造分页对象（告诉 MP 查第几页、每页几条）
        Page<Device> pageParam =new Page<>(deviceQuery.getPageNum(),deviceQuery.getPageSize());

        // 第 2 步：构造查询条件（动态拼 where）
        LambdaQueryWrapper<Device> wrapper =new LambdaQueryWrapper<>();
        // wrapper 的三个参数：要不要拼这个条件，拼哪个数据库列，拼什么值
        wrapper.like(StringUtils.hasText(deviceQuery.getName()),Device::getName, deviceQuery.getName());
        wrapper.eq(StringUtils.hasText(deviceQuery.getCode()),Device::getCode,deviceQuery.getCode());
        wrapper.eq(deviceQuery.getStatus()!=null,Device::getStatus,deviceQuery.getStatus());
        wrapper.eq(deviceQuery.getDeptId()!=null,Device::getDeptId,deviceQuery.getDeptId());
        wrapper.eq(deviceQuery.getOwnerId()!=null,Device::getOwnerId,deviceQuery.getOwnerId());

        Page<Device> devicePage = page(pageParam,wrapper);
        return (Page<DeviceVO>) devicePage.convert(this::toVO);
    }
}
