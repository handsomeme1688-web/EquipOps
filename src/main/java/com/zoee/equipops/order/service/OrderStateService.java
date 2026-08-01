package com.zoee.equipops.order.service;

import com.zoee.equipops.common.exception.BizException;
import com.zoee.equipops.common.result.ResultCode;
import com.zoee.equipops.order.enums.OrderStatus;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import static com.zoee.equipops.order.enums.OrderStatus.*;

/**
 * 根据状态机, 合法的流转路径：
 *
 * PENDING(待受理) ──→ ACCEPTED(已接单) ──→ IN_REPAIR(维修中) ──→ PENDING_CHECK(待验收) ──→ COMPLETED(已完成)
 *     │                    │                    │                       │
 *     └──→ CLOSED(撤单)     └──→ CLOSED(撤单)     └──→ OUTSOURCED(委外)    └──→ IN_REPAIR(退回)
 *                                                     │
 *                                                     ├──→ PENDING_CHECK(验收)
 *                                                     └──→ CLOSED(验收通过)
 */
@Service
public class OrderStateService {

    /**
     * 合法流转表：key 为当前状态，value 为该状态允许迁移到的目标状态集合。
     */
    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS;

    static {
        // 用一个字典来存储允许的流转表
        // EnumMap：它内部是数组实现，以枚举的 ordinal（序号）为下标，查找复杂度严格 O(1)，比 HashMap 更快且更省内存。
        Map<OrderStatus, Set<OrderStatus>> map = new EnumMap<>(OrderStatus.class);


        // 添加合法流转规则
        // 待受理 → 已接单（工程师接单） / 已关闭（报修人撤销、主管判定误报）
        map.put(PENDING, Set.of(ACCEPTED, CLOSED));

        // 已接单 → 维修中（开始维修） / 已关闭（报修人撤单，需工程师同意）
        map.put(ACCEPTED, Set.of(IN_REPAIR, CLOSED));

        // 维修中 → 待验收（维修完成） / 委外中（申请委外，需维保主管批准）
        map.put(IN_REPAIR, Set.of(PENDING_CHECK, OUTSOURCED));

        // 委外中 → 待验收（厂商完工） / 已关闭（厂商判定无法维修）
        map.put(OUTSOURCED, Set.of(PENDING_CHECK, CLOSED));

        // 待验收 → 已完成（验收通过） / 维修中（验收不通过，退回重修）
        map.put(PENDING_CHECK, Set.of(COMPLETED, IN_REPAIR));

        // 终态：显式映射为空集合，而非留空或 null。
        // 这样 allowedTargets() 与 canTransit() 无需在方法内判空，
        // 「终态不可流转」这条规则由数据结构本身表达，而不是靠调用方记得判断。
        map.put(COMPLETED, Collections.emptySet());
        map.put(CLOSED, Collections.emptySet());

        TRANSITIONS = Collections.unmodifiableMap(map);
    }


    // 查询，给前端用的
    public boolean canTransit(OrderStatus from, OrderStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return TRANSITIONS.get(from).contains(to);
    }

    public void validateTransition(OrderStatus from, OrderStatus to) {
        if (from == null || to == null) {
            throw new BizException(ResultCode.BAD_REQUEST,"状态流转的起点与终点均不能为空");
        }
        if (!canTransit(from, to)) {
            throw new BizException(ResultCode.ORDER_STATUS_ILLEGAL,
                    "非法的工单状态流转: " + from.getDescription() + " -> " + to.getDescription());
        }
    }


    public Set<OrderStatus> allowedTargets(OrderStatus from) {
        if (from == null) {
            return Collections.emptySet();
        }
        return TRANSITIONS.get(from);
    }


    // 判断是否为终态
    public boolean isTerminal(OrderStatus status) {
        return status != null && allowedTargets(status).isEmpty();
    }
}
