package com.zoee.equipops.order.service;

import com.zoee.equipops.order.enums.OrderStatus;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

// 静态导入，让代码里写 PENDING 而不是 OrderStatus.PENDING，流转表更干净。
import static com.zoee.equipops.order.enums.OrderStatus.*;

/**
 * 三、为什么这个 Service 没拆接口和实现类
 * 原因：
 *
 * 1. 没有多态需求。状态流转规则只有一份，不会有"另一种规则实现"。接口的核心价值是"同一契约，多种实现"，这里用不上。
 * 2. 测试不需要 mock 它。这个类不访问数据库、不依赖外部服务，是纯逻辑。单元测试直接 new 就行，不需要 mock 框架。
 * 看看你的测试类 OrderStateServiceTest——它是空的还没写，但将来写测试时应该是：
 * OrderStateService service = new OrderStateService();
 * service.validateTransition(PENDING, ACCEPTED);  // 不抛异常 = 通过
 * 如果拆成接口+实现，单元测试也可以直接 new 实现类，接口并没有增加测试便利性。
 * 3. 流转规则不应被代理。Spring 的 @Transactional、AOP 切面等基于 JDK 动态代理（需要接口）或 CGLib（不需要接口）。这个类不需要事务也不需要切面，所以不需要 JDK 动态代理，也就不需要接口。
 *
 * 什么时候才该拆？ 如果你将来有 OrderStateService 接口，然后有两个实现——比如 StandardOrderStateService 标准规则和 ExpressOrderStateService 加急工单规则
 * 那拆接口就很有价值。但目前没有这个需求，拆了只是多一个文件多一层跳转，没有实际收益。这符合"不要为假设的未来需求设计"的原则。
 *
 */


/**
 * 这是一个纯逻辑的状态流转校验器——不访问数据库、不调外部服务、没有 Spring Bean 依赖。它做的事只有一件：判断一次状态变更是否合法。
 *
 * 为什么需要它：
 * 假设有一个工单当前状态是"待受理"，有人直接把它改成"已完成"。这是跳步骤的错误操作。OrderStateService 用一张流转表来拦截这种非法跳转。
 *
 * 用图来表示合法的流转路径：
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

    /**
     * 查询，给前端用的
     * 判断一次状态迁移是否合法。
     *
     * <p>用于「查询」场景——例如前端需要知道当前工单能展示哪些操作按钮。
     * 真正执行流转时请使用 {@link #validateTransition}，
     * 因为返回 boolean 的方法，调用方可能忘记检查返回值。
     */
    public boolean canTransit(OrderStatus from, OrderStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return TRANSITIONS.get(from).contains(to);
    }

    /**
     * 校验一次状态迁移，非法则抛出异常。
     * 给后端业务代码用的。这是"先校验再执行"模式——业务代码在写库之前调这个方法，如果流转非法就直接抛异常阻断。
     * 选择抛异常而不是返回 boolean 的原因是：返回值可以被忽略，异常不能。调了但忘了 if 判空，系统不会悄悄通过。
     *
     * <p>TODO Day 8：common 模块建立 BizException 后，
     * 将此处替换为携带业务错误码的 BizException，以便全局异常处理器返回 409。
     *
     * @param from 起点状态
     * @param to   终点状态
     * @throws IllegalArgumentException 入参为 null
     * @throws IllegalStateException    该迁移不在合法流转表中
     */
    public void validateTransition(OrderStatus from, OrderStatus to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("状态流转的起点与终点均不能为空");
        }
        if (!canTransit(from, to)) {
            throw new IllegalStateException(
                    "非法的工单状态流转: " + from.getDescription() + " -> " + to.getDescription());
        }
    }

    /**
     * 返回指定状态下所有合法的目标状态。
     * 给前端用的。与上面给前端用的方法的共同特点：只读、不抛异常、用于查询而非执行
     *
     * 项目还没有前端，为什么能写出这些方法？这不是"提前给前端写好了接口"，而是状态机天然就需要两种调用方式：
     *
     * - 执行时：直接干，非法就炸 → validateTransition
     * - 查询时：先看看能不能干，别炸 → canTransit
     *
     * <p>终态返回空集合，不返回 null——调用方可以直接遍历，无需判空。
     */
    public Set<OrderStatus> allowedTargets(OrderStatus from) {
        if (from == null) {
            return Collections.emptySet();
        }
        return TRANSITIONS.get(from);
    }

    /**
     * 判断是否为终态（没有任何出边的状态）。
     *
     * <p>注意实现方式：终态不是靠硬编码「COMPLETED 和 CLOSED 是终态」来判断的，
     * 而是由流转表推导得出。将来若新增终态，只需在流转表中映射为空集合，
     * 本方法无需修改——避免了同一事实存在两个来源。
     */
    public boolean isTerminal(OrderStatus status) {
        return status != null && allowedTargets(status).isEmpty();
    }
    /**
     * 以一个典型调用为例，报修人撤销工单：
     *      1. Controller 收到撤销请求
     *      2. Service 层取出工单当前状态: OrderStatus current = order.getStatus();  // 比如是 PENDING
     *      3. 调用: orderStateService.validateTransition(current, OrderStatus.CLOSED);
     *      4. validateTransition 查 TRANSITIONS 表:
     *         TRANSITIONS.get(PENDING) → {ACCEPTED, CLOSED}
     *         {ACCEPTED, CLOSED}.contains(CLOSED) → true → 通过
     *      5. 校验通过, 执行业务逻辑: order.setStatus(CLOSED); orderRepository.save(order);
     * 如果是非法操作，比如工单是 COMPLETED 还要改，第 4 步 TRANSITIONS.get(COMPLETED) 返回空集合，
     * contains 返回 false，直接抛异常，后续的 save 根本不会执行。
     */
}
