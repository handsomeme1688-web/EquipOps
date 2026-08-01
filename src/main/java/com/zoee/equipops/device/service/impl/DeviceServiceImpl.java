package com.zoee.equipops.device.service.impl;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zoee.equipops.auth.service.PermissionCheckService;
import com.zoee.equipops.common.context.UserContext;
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
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class DeviceServiceImpl extends ServiceImpl<DeviceMapper, Device> implements DeviceService {

    private static final String DEVICE_CACHE_PREFIX = "device:detail:";
    private static final String DEVICE_NULL_CACHE_PREFIX = "device:detail:null:";

    private final RedisTemplate<String,Object> redisTemplate;
    private final PermissionCheckService permissionCheckService;

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
        device.setDeptId(UserContext.getDeptId()); // 服务端强制加 deptId，不信任前端
        device.setStatus(DeviceStatus.NORMAL);
        save(device);

        return toVO(device);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceVO update(Long id, DeviceUpdateDTO deviceUpdateDTO) {
        Device existDevice=getById(id);
        if(existDevice == null) throw new BizException(ResultCode.DEVICE_NOT_FOUND);
        if(!permissionCheckService.isAdmin(UserContext.getUserId()) && !existDevice.getDeptId().equals(UserContext.getDeptId())){
            throw new BizException(ResultCode.NOT_FOUND);// 故意返回 404 而非 403，不给攻击者确认"这个 ID 存在"
        }
        existDevice.setOwnerId(deviceUpdateDTO.getOwnerId());
        existDevice.setLocation(deviceUpdateDTO.getLocation());
        existDevice.setName(deviceUpdateDTO.getName());
        existDevice.setModel(deviceUpdateDTO.getModel());
        existDevice.setDescription(deviceUpdateDTO.getDescription());
        updateById(existDevice);
        evictDetailCacheAfterCommit(id);
        return toVO(existDevice);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Device existDevice = getById(id);
        if(existDevice == null) throw new BizException(ResultCode.DEVICE_NOT_FOUND);
        if(!permissionCheckService.isAdmin(UserContext.getUserId()) && !existDevice.getDeptId().equals(UserContext.getDeptId())){
            throw new BizException(ResultCode.NOT_FOUND);// 故意返回 404 而非 403，不给攻击者确认"这个 ID 存在"
        }
        removeById(id);
        evictDetailCacheAfterCommit(id);
    }

    @Override
    public DeviceVO detail(Long id) {
        String key = detailCacheKey(id);
        String nullKey = nullDetailCacheKey(id);

        if (Boolean.TRUE.equals(redisTemplate.opsForValue().get(nullKey))) {
            throw new BizException(ResultCode.DEVICE_NOT_FOUND);
        }

        Object cached = redisTemplate.opsForValue().get(key);
        if (cached==null) {
            Device existDevice = getById(id);
            if (existDevice==null) {
                redisTemplate.opsForValue().set(nullKey, Boolean.TRUE, Duration.ofSeconds(2 * 60));
                throw new BizException(ResultCode.DEVICE_NOT_FOUND);
            }
            if(!permissionCheckService.isAdmin(UserContext.getUserId()) && !existDevice.getDeptId().equals(UserContext.getDeptId())){
                throw new BizException(ResultCode.NOT_FOUND);// 故意返回 404 而非 403，不给攻击者确认"这个 ID 存在"
            }
            int ttl = 30 * 60 + (int) (Math.random() * 300);
            DeviceVO vo = toVO(existDevice);
            redisTemplate.opsForValue().set(key,vo, Duration.ofSeconds(ttl));
            return vo;
        }

        DeviceVO existVO=(DeviceVO) cached;
        if(!permissionCheckService.isAdmin(UserContext.getUserId()) && !existVO.getDeptId().equals(UserContext.getDeptId())){
            throw new BizException(ResultCode.NOT_FOUND);// 故意返回 404 而非 403，不给攻击者确认"这个 ID 存在"
        }
        return existVO;
    }

    @Override
    public Page<DeviceVO> page(DeviceQuery deviceQuery) {
        // 构造分页对象（告诉 MP 查第几页、每页几条）。泛型是 DeviceVO（不再是 Device）
        Page<DeviceVO> pageParam =new Page<>(deviceQuery.getPageNum(),deviceQuery.getPageSize());
        if(!permissionCheckService.isAdmin(UserContext.getUserId())){
            deviceQuery.setDeptId(UserContext.getDeptId());
        }

        // 调 JOIN 查询：MP 拦截器会把查询结果 + 总数，填进 pageParam 这个对象
        baseMapper.selectDeviceVoPage(pageParam,deviceQuery);

        // pageParam 已经被填满了，直接返回
        return pageParam;

    }

    private void evictDetailCacheAfterCommit(Long id) {
        Runnable evict = () -> {
            redisTemplate.delete(detailCacheKey(id));
            redisTemplate.delete(nullDetailCacheKey(id));
        };
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict.run();
                }
            });
            return;
        }
        evict.run();
    }

    private String detailCacheKey(Long id) {
        return DEVICE_CACHE_PREFIX + id;
    }

    private String nullDetailCacheKey(Long id) {
        return DEVICE_NULL_CACHE_PREFIX + id;
    }
}
