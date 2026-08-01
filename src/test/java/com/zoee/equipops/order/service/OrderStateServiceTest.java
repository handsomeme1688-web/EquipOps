package com.zoee.equipops.order.service;

import com.zoee.equipops.common.exception.BizException;
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
                .isInstanceOf(BizException.class)
                .hasMessageContaining("非法的工单状态流转");
    }


    @Test
    void validateTransition_非法流转_已接单到待验收_抛异常(){
        OrderStateService orderStateService=new OrderStateService();
        assertThatThrownBy(()->{orderStateService.validateTransition(ACCEPTED,PENDING_CHECK);})
                .isInstanceOf(BizException.class)
                .hasMessageContaining("非法的工单状态流转");
    }


    @Test
    void allowedTargets_待受理_返回已接单和已关闭() {
        OrderStateService service = new OrderStateService();
        // 测值 → assertThat(value)
        assertThat(service.allowedTargets(PENDING))
                .containsExactlyInAnyOrder(ACCEPTED, CLOSED);
    }


    @Test
    void allowedTargets_维修中_返回委外中和待验收() {
        OrderStateService orderStateService=new OrderStateService();
        assertThat(orderStateService.allowedTargets(IN_REPAIR))
                .containsExactlyInAnyOrder(OUTSOURCED,PENDING_CHECK);
    }
}
