package com.zoee.equipops.order.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static com.zoee.equipops.order.enums.OrderStatus.*;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

/**
 * 记住一个决策树就够了
 *
 * 被测方法是 void 吗？
 * ├── 是 → 你关心它会炸还是不会炸？
 * │        ├── 应该不炸   → assertThatCode(lambda)
 * │        └── 应该炸     → assertThatThrownBy(lambda)
 * └── 不是（有返回值）     → assertThat(value)
 *
 * 补充：assertThatThrownBy 和 assertThatCode 的关系
 *
 * assertThatCode 其实是父集，它也能测异常：
 *
 * assertThatCode(() -> service.validateTransition(PENDING, COMPLETED))
 *     .isInstanceOf(IllegalStateException.class)
 *     .hasMessageContaining("非法的工单状态流转");
 *
 * 但 assertThatThrownBy 更语义化——方法名就表达了"我预期它会炸"，读起来更清晰。如果你的测试就是测异常场景，用它更直接。
 *
 */
public class OrderStateServiceTest {
    @Test
    void validateTransition_合法流转_待受理到已接单_不抛异常() {
        // 不需要注入
        OrderStateService service = new OrderStateService();
        // 测代码不抛异常 → assertThatCode(lambda)
        assertThatCode(() -> service.validateTransition(PENDING, ACCEPTED))
                .doesNotThrowAnyException();
    }


    @Test
    void validateTransition_合法流转_待受理到关闭_不抛异常() {
        OrderStateService service =new OrderStateService();
        assertThatCode(()->{service.validateTransition(PENDING,CLOSED);})
                .doesNotThrowAnyException();
    }


    @Test
    void validateTransition_合法流转_已接单到关闭_不抛异常(){
        OrderStateService orderStateService = new OrderStateService();
        assertThatCode(()->{orderStateService.validateTransition(ACCEPTED,CLOSED);})
                .doesNotThrowAnyException();
    }


    @Test
    void validateTransition_合法流转_已接单到维修中_不抛异常(){
        OrderStateService orderStateService=new OrderStateService();
        assertThatCode(()->{orderStateService.validateTransition(ACCEPTED,IN_REPAIR);})
                .doesNotThrowAnyException();
    }


    @Test
    void validateTransition_非法流转_待受理到已完成() {
        OrderStateService service = new OrderStateService();
        // 测代码抛了指定异常 → assertThatThrownBy(lambda)
        assertThatThrownBy(() -> service.validateTransition(PENDING, COMPLETED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("非法的工单状态流转");
    }


    @Test
    void validateTransition_非法流转_已接单到待验收_抛异常(){
        OrderStateService orderStateService=new OrderStateService();
        assertThatThrownBy(()->{orderStateService.validateTransition(ACCEPTED,PENDING_CHECK);})
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("非法的工单状态流转");
        /**
         * isInstanceOf(IllegalStateException.class)
         * 作用：断言抛出的异常类型。
         *
         * 解析：验证捕获到的异常是不是 IllegalStateException（非法状态异常）类或其子类。\
         * 如果代码没有抛异常，或者抛出的异常是 NullPointerException 等其他类型，测试就会宣告失败。
         *
         */
    }


    @Test
    void allowedTargets_待受理_返回已接单和已关闭() {
        OrderStateService service = new OrderStateService();
        // 测值 → assertThat(value)
        assertThat(service.allowedTargets(PENDING))
                .containsExactlyInAnyOrder(ACCEPTED, CLOSED);
        /**
         * assertThat.contains()和assertThat.containsExactlyInAnyOrder()的区别
         * contains — "这些都在就行，多的不管"，只检查指定的元素是否都在集合里，不关心集合里有没有额外的元素。
         * containsExactlyInAnyOrder — "就这些，一个不多一个不少"，检查集合里必须包含指定的元素，并且数量必须一致。
         *
         * 测状态机的 allowedTargets 时，集合的精确性就是业务逻辑本身。
         * "PENDING 只能流转到 2 个目标"——这个"只能"是核心约束。
         * 用 contains 等于只验证了下限，没验证上限，万一有人加了第三条边，测试也绿，就失去了测试的意义。
         */
    }


    @Test
    void allowedTargets_维修中_返回委外中和待验收() {
        OrderStateService orderStateService=new OrderStateService();
        assertThat(orderStateService.allowedTargets(IN_REPAIR))
                .containsExactlyInAnyOrder(OUTSOURCED,PENDING_CHECK);
        /**
         * 不需要 Lambda 的原因：这段代码的目标是验证 allowedTargets(...) 方法返回的具体数据。
         *
         * 必须使用 Lambda 的原因：如果不用 Lambda，直接写成 assertThatThrownBy(orderStateService.validateTransition(...))，
         * Java 在传入参数之前就会立刻执行这个方法。
         * 一旦该方法抛出了 IllegalStateException 异常，整个测试程序会在这一行直接崩溃终止，后面的 assertThatThrownBy 甚至都还没有机会运行！
         *
         * Lambda (() -> { ... }) 的作用：它把这行代码包裹（延迟）起来了，变成了一个“待执行的操作指令”。
         * assertThatThrownBy 拿到这个指令后，内部会用 try-catch 去主动触发它并捕获异常。
         *
         */
    }
}
