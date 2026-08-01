package com.zoee.equipops.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// 注解定义
/** 贴在 Controller 方法上，AOP 自动记录操作日志 */
@Target(ElementType.METHOD) // 这个注解只能贴在方法上
@Retention(RetentionPolicy.RUNTIME) //注解信息保留到运行时
public @interface OpLog { // @interface 不是 class，它定义一个注解类型
    /** 资源类型，如 "order"、"device" */
    String resourceType();
    /** 动作，如 "create"、"update"、"delete" */
    String action();
}
