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
import com.zoee.equipops.order.service.OrderIdempotencyService;
import com.zoee.equipops.order.service.OrderRequestFingerprint;
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
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class RepairOrderServiceImpl extends ServiceImpl<RepairOrderMapper, RepairOrder> implements RepairOrderService {
    private final DeviceService deviceService;
    private final PermissionCheckService permissionCheckService;
    private final UserService userService; //不能绕过 Service 直接调 Mapper
    private final OrderStateService orderStateService;
    private final OrderIdempotencyService orderIdempotencyService;
    private final OrderRequestFingerprint orderRequestFingerprint;

    /**
     * 创建设备维修工单
     * Redis 只做快速返回，数据库的 (request_user_id, idempotency_key)
     * 唯一索引才是并发和缓存故障下的最终防线。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RepairOrderVO create(RepairOrderDTO repairOrderDTO, String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new BizException(ResultCode.BAD_REQUEST, "幂等键不能为空");
        }
        String normalizedKey = idempotencyKey.trim();

        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        String requestHash = orderRequestFingerprint.calculate(repairOrderDTO);
        OrderIdempotencyService.Claim claim = orderIdempotencyService.begin(
                userId,
                normalizedKey,
                requestHash
        );

        try {
            if (claim.completedOrderId() != null) {
                RepairOrder cachedOrder = getById(claim.completedOrderId());
                if (cachedOrder != null
                        && userId.equals(cachedOrder.getRequestUserId())
                        && normalizedKey.equals(cachedOrder.getIdempotencyKey())) {
                    ensureSameRequest(cachedOrder, requestHash);
                    return toVO(cachedOrder);
                }
            }

            RepairOrder existingOrder = findByIdempotencyKey(userId, normalizedKey);
            if (existingOrder != null) {
                ensureSameRequest(existingOrder, requestHash);
                orderIdempotencyService.completeAfterCommit(claim, existingOrder.getId());
                return toVO(existingOrder);
            }

            //校验设备是否存在
            Device existsDevice = deviceService.getById(repairOrderDTO.getDeviceId());
            if (existsDevice == null)throw new BizException(ResultCode.DEVICE_NOT_FOUND); // 设备不存在

            //校验报修人是否为设备所在部门
            if(!UserContext.getDeptId().equals(existsDevice.getDeptId()) && !permissionCheckService.isAdmin(UserContext.getUserId())) throw new BizException(ResultCode.NOT_FOUND);

            // 创建新工单
            LocalDateTime now = LocalDateTime.now();
            RepairOrder repairOrder = new RepairOrder();
            repairOrder.setDeviceId(repairOrderDTO.getDeviceId());
            repairOrder.setRequestUserId(userId);
            repairOrder.setRequestTime(now);
            repairOrder.setVersion(0); // 乐观锁是每次更新 +1，创建时从 0 开始。

            repairOrder.setIdempotencyKey(normalizedKey);
            repairOrder.setRequestHash(requestHash);

            repairOrder.setDeptId(UserContext.getDeptId());
            repairOrder.setStatus(OrderStatus.PENDING);
            repairOrder.setDescription(repairOrderDTO.getDescription().trim());
            repairOrder.setPriority(repairOrderDTO.getPriority());
            repairOrder.setCreateBy(userId);
            repairOrder.setCreateTime(now);
            repairOrder.setTimedOut(false);

            try {
                save(repairOrder);
            } catch (DuplicateKeyException e) {
                RepairOrder concurrentOrder = baseMapper.selectByIdempotencyKeyForUpdate(
                        userId,
                        normalizedKey
                );
                if (concurrentOrder != null) {
                    ensureSameRequest(concurrentOrder, requestHash);
                    orderIdempotencyService.completeAfterCommit(claim, concurrentOrder.getId());
                    return toVO(concurrentOrder);
                }
                throw new BizException(ResultCode.ORDER_IDEMPOTENCY_CONFLICT);
            }

            orderIdempotencyService.completeAfterCommit(claim, repairOrder.getId());
            return toVO(repairOrder, existsDevice);
        } catch (RuntimeException exception) {
            orderIdempotencyService.release(claim);
            throw exception;
        }
    }

    private RepairOrder findByIdempotencyKey(Long userId, String idempotencyKey) {
        return lambdaQuery()
                .eq(RepairOrder::getRequestUserId, userId)
                .eq(RepairOrder::getIdempotencyKey, idempotencyKey)
                .one();
    }

    private void ensureSameRequest(RepairOrder order, String requestHash) {
        if (!Objects.equals(order.getRequestHash(), requestHash)) {
            throw new BizException(
                    ResultCode.ORDER_IDEMPOTENCY_CONFLICT,
                    "同一 Idempotency-Key 不能用于不同请求内容"
            );
        }
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
        vo.setFinishTime(order.getFinishTime());
        vo.setCheckTime(order.getCheckTime());
        vo.setCloseTime(order.getCloseTime());
        vo.setTimedOut(order.getTimedOut());
        vo.setTimeoutTime(order.getTimeoutTime());
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
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<RepairOrder> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(RepairOrder::getId,orderId)
                .eq(RepairOrder::getStatus,OrderStatus.PENDING)
                .set(RepairOrder::getStatus,OrderStatus.ACCEPTED)
                .set(RepairOrder::getAssignId,UserContext.getUserId())
                .set(RepairOrder::getAcceptTime,now)
                .set(RepairOrder::getUpdateBy, UserContext.getUserId())
                .set(RepairOrder::getUpdateTime, now)
                .setSql("version = version + 1");
        boolean success = update(wrapper);
        repairOrder = getById(orderId);
        if (success){
            return toVO(repairOrder);
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

        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<RepairOrder> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(RepairOrder::getId,orderId)
                .eq(RepairOrder::getStatus,repairOrder.getStatus())
                .set(RepairOrder::getStatus,orderStatus)
                .set(RepairOrder::getUpdateBy, UserContext.getUserId())
                .set(RepairOrder::getUpdateTime, now)
                .setSql("version = version + 1");

        switch (orderStatus) {
            case ACCEPTED -> wrapper
                    .set(RepairOrder::getAssignId, UserContext.getUserId())
                    .set(RepairOrder::getAcceptTime, now);
            case PENDING_CHECK -> wrapper.set(RepairOrder::getFinishTime, now);
            case COMPLETED -> wrapper.set(RepairOrder::getCheckTime, now);
            case CLOSED -> wrapper.set(RepairOrder::getCloseTime, now);
            default -> {
                // 进入维修或委外只改变状态与通用审计字段。
            }
        }
        boolean success = update(wrapper);

        repairOrder = getById(orderId);

        if (success){
            return toVO(repairOrder);
        }

        throw new BizException(ResultCode.ORDER_STATUS_ILLEGAL);
    }

}
