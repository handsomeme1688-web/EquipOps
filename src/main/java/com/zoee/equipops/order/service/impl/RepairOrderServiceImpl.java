package com.zoee.equipops.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zoee.equipops.auth.service.PermissionCheckService;
import com.zoee.equipops.common.context.UserContext;
import com.zoee.equipops.common.exception.BizException;
import com.zoee.equipops.common.result.ResultCode;
import com.zoee.equipops.device.domain.entity.Device;
import com.zoee.equipops.device.service.DeviceService;
import com.zoee.equipops.order.domain.dto.RepairOrderDTO;
import com.zoee.equipops.order.domain.entity.RepairOrder;
import com.zoee.equipops.order.domain.vo.RepairOrderVO;
import com.zoee.equipops.order.enums.OrderStatus;
import com.zoee.equipops.order.mapper.RepairOrderMapper;
import com.zoee.equipops.order.service.OrderStateService;
import com.zoee.equipops.order.service.RepairOrderService;
import com.zoee.equipops.system.entity.User;
import com.zoee.equipops.system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class RepairOrderServiceImpl extends ServiceImpl<RepairOrderMapper, RepairOrder> implements RepairOrderService {
    private final DeviceService deviceService;
    private final PermissionCheckService permissionCheckService;
    private final UserService userService; //不能绕过 Service 直接调 Mapper
    private final OrderStateService orderStateService;

    /**
     * 创建设备维修工单
     * @param repairOrderDTO
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RepairOrderVO create(RepairOrderDTO repairOrderDTO) {
        if (!StringUtils.hasText(repairOrderDTO.getIdempotencyKey())) {
            throw new BizException(ResultCode.BAD_REQUEST, "幂等键不能为空");
        }

        Long userId = UserContext.getUserId();
        RepairOrder existingOrder = lambdaQuery()
                .eq(RepairOrder::getRequestUserId, userId)
                .eq(RepairOrder::getIdempotencyKey, repairOrderDTO.getIdempotencyKey())
                .one();
        if (existingOrder != null) {
            return toVO(existingOrder);
        }

        //校验设备是否存在
        Device existsDevice = deviceService.getById(repairOrderDTO.getDeviceId());
        if (existsDevice == null)throw new BizException(ResultCode.DEVICE_NOT_FOUND); // 设备不存在

        //校验报修人是否为设备所在部门
        if(!UserContext.getDeptId().equals(existsDevice.getDeptId()) && !permissionCheckService.isAdmin(UserContext.getUserId())) throw new BizException(ResultCode.NOT_FOUND);

        // 创建新工单
        RepairOrder repairOrder = new RepairOrder();
        repairOrder.setDeviceId(repairOrderDTO.getDeviceId());
        repairOrder.setRequestUserId(userId);
        repairOrder.setRequestTime(LocalDateTime.now());
        repairOrder.setVersion(0); // 乐观锁是每次更新 +1，创建时从 0 开始。

        repairOrder.setIdempotencyKey(repairOrderDTO.getIdempotencyKey());

        repairOrder.setDeptId(UserContext.getDeptId());
        repairOrder.setStatus(OrderStatus.PENDING);
        repairOrder.setDescription(repairOrderDTO.getDescription());
        repairOrder.setPriority(repairOrderDTO.getPriority());
        repairOrder.setCreateBy(userId);
        repairOrder.setCreateTime(LocalDateTime.now());

        try {
            save(repairOrder);
        } catch (DuplicateKeyException e) {
            RepairOrder concurrentOrder = lambdaQuery()
                    .eq(RepairOrder::getRequestUserId, userId)
                    .eq(RepairOrder::getIdempotencyKey, repairOrderDTO.getIdempotencyKey())
                    .one();
            if (concurrentOrder != null) {
                return toVO(concurrentOrder);
            }
            throw new BizException(ResultCode.ORDER_IDEMPOTENCY_CONFLICT);
        }

        return toVO(repairOrder, existsDevice);
    }

    private RepairOrderVO toVO(RepairOrder order) {
        Device device = deviceService.getById(order.getDeviceId());
        if (device == null) {
            throw new BizException(ResultCode.DEVICE_NOT_FOUND);
        }
        return toVO(order, device);
    }

    private RepairOrderVO toVO(RepairOrder order, Device device) {
        RepairOrderVO vo = new RepairOrderVO();
        vo.setId(order.getId());
        vo.setDeviceId(device.getId());
        User requester = userService.getById(order.getRequestUserId());
        vo.setRequesterName(requester == null ? "未知" : requester.getRealName());
        vo.setRequestTime(order.getRequestTime());
        vo.setStatus(order.getStatus());
        vo.setDescription(order.getDescription());
        vo.setCreateTime(order.getCreateTime());
        vo.setUpdateTime(order.getUpdateTime());
        vo.setAcceptTime(order.getAcceptTime());
        if (order.getAssignId() != null) {
            User assignee = userService.getById(order.getAssignId());
            vo.setAssignName(assignee == null ? "未知" : assignee.getRealName());
        }
        return vo;
    }

    /**
     * 工程师接单
     * @param orderId
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RepairOrderVO accept(Long orderId) {
        // 校验工单是否存在
        RepairOrder repairOrder = getById(orderId);
        if (repairOrder==null) throw new BizException(ResultCode.ORDER_NOT_FOUND);

        //校验工程师权限码
        if (!permissionCheckService.hasPerm(UserContext.getUserId(),"order:accept")) throw  new BizException((ResultCode.FORBIDDEN));

        //条件更新 DB
        LambdaUpdateWrapper<RepairOrder> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(RepairOrder::getId,orderId)
                .eq(RepairOrder::getStatus,OrderStatus.PENDING)
                .set(RepairOrder::getStatus,OrderStatus.ACCEPTED)
                .set(RepairOrder::getAssignId,UserContext.getUserId())
                .set(RepairOrder::getAcceptTime,LocalDateTime.now())
                .setSql("version = version + 1");
        boolean success = update(wrapper);
        repairOrder = getById(orderId);
        if (success){
            RepairOrderVO vo = new RepairOrderVO();
            vo.setId(repairOrder.getId());
            vo.setDeviceId(repairOrder.getDeviceId());
            vo.setRequesterName(userService.getById(repairOrder.getRequestUserId()).getRealName());
            vo.setRequestTime(repairOrder.getRequestTime());
            vo.setAssignName(userService.getById(UserContext.getUserId()).getRealName());
            vo.setStatus(repairOrder.getStatus());
            vo.setDescription(repairOrder.getDescription());
            vo.setCreateTime(repairOrder.getCreateTime());
            vo.setUpdateTime(repairOrder.getUpdateTime());
            vo.setAcceptTime(repairOrder.getAcceptTime());
            return vo;
        }

        throw new BizException(ResultCode.ORDER_ALREADY_ACCEPTED);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RepairOrderVO updateStatus(Long orderId, OrderStatus orderStatus) {
        RepairOrder repairOrder = getById(orderId);
        if (repairOrder==null) throw new BizException(ResultCode.ORDER_NOT_FOUND);
        orderStateService.validateTransition(repairOrder.getStatus(),orderStatus);

        String requiredPerm = switch (orderStatus) {
            case ACCEPTED      -> "order:accept";
            case IN_REPAIR     -> "order:repair";
            case OUTSOURCED    -> "order:outsource";
            case PENDING_CHECK -> "order:submit";
            case COMPLETED     -> "order:audit";
            case CLOSED        -> "order:cancel";
            default            -> null;
        };
        if (requiredPerm != null
                && !permissionCheckService.hasPerm(UserContext.getUserId(), requiredPerm)) {
            throw new BizException(ResultCode.FORBIDDEN);
        }

        LambdaUpdateWrapper<RepairOrder> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(RepairOrder::getId,orderId)
                .eq(RepairOrder::getStatus,repairOrder.getStatus())
                .set(RepairOrder::getStatus,orderStatus)
                .set(RepairOrder::getAssignId,UserContext.getUserId());
        boolean success = update(wrapper);

        repairOrder = getById(orderId);

        if (success){
            RepairOrderVO vo = new RepairOrderVO();
            vo.setId(repairOrder.getId());
            vo.setDeviceId(repairOrder.getDeviceId());
            vo.setRequesterName(userService.getById(repairOrder.getRequestUserId()).getRealName());
            vo.setRequestTime(repairOrder.getRequestTime());
            vo.setAssignName(userService.getById(UserContext.getUserId()).getRealName());
            vo.setStatus(repairOrder.getStatus());
            vo.setDescription(repairOrder.getDescription());
            vo.setCreateTime(repairOrder.getCreateTime());
            vo.setUpdateTime(repairOrder.getUpdateTime());
            vo.setAcceptTime(repairOrder.getAcceptTime());
            return vo;
        }

        throw new BizException(ResultCode.ORDER_STATUS_ILLEGAL);
    }

}
