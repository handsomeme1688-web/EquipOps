package com.zoee.equipops.order.enums;


import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;


/**
 * Java 的 enum（枚举）是一种特殊的类，它的实例数量在编译时就固定死了，不能在运行时用 new 创建新实例。
 * 简单理解：它就是一份有限的、预定义的常量清单。
 *
 */
public enum OrderStatus {
    // 1. 定义 7 个枚举常量（对应数据库 0-6）
    // 这里预定义的就是实例
    PENDING(0, "待受理"),
    ACCEPTED(1, "已接单"),
    IN_REPAIR(2, "维修中"),
    OUTSOURCED(3, "委外中"),
    PENDING_CHECK(4, "待验收"),
    COMPLETED(5, "已完成"),
    CLOSED(6, "已关闭");

    // 2. 定义私有属性：数值和描述
    @EnumValue
    private final int code; // 数据库存的数值
    @JsonValue
    private final String description; // 显示用的中文描述

    // 3. 枚举构造器默认就是 private，不用也不能加 public。
    // 外界无法 new，只有 enum 内部 7 个常量能用它。
    OrderStatus(Integer code,String description){
        this.code=code;
        this.description=description;
    }

    // 4. Getter 方法
    /**
     * 用基本类型 int
     *
     * 理由是这里的值不可能为 null：
     *
     * code 是 final 字段，在构造器里必填，7 个枚举常量每个都传了值（0-6）
     * 枚举实例一旦存在，code 就一定有值
     * 既然不可能是 null，用包装类 Integer 就没有意义，反而多一层装箱、还给人"可能为 null"的错觉
     *
     * @return
     */
    public int getCode(){
        return code;
    }
    public String getDescription(){
        return description;
    }

    /**
     * 5. 根据数字代码反查对应的枚举对象
     * @param code 数据库中存的值 (0~6)
     * @return 对应的 OrderStatus 枚举，未找到则返回 null
     */
    public static OrderStatus findByCode(int code){
        // 遍历所有枚举值，找到 code 匹配的那个
        // 这是常见的数据库值 → 枚举对象反查方法。
        // 你从数据库查出 status = 2，调 OrderStatus.findByCode(2) 就得到 IN_REPAIR。
        for (OrderStatus status : OrderStatus.values()) {// OrderStatus.values() 是编译器自动生成的方法，返回所有 7 个常量的数组。
            if (status.getCode() == code) { // code相等
                return status; // 返回对象，而不是对象属性
            }
        }
        return null;
    }

    /**
     * 为什么用 enum 而不用常量类
     *
     * 用 enum 之前，许多人会这样写：
     *
     * public class OrderStatus {
     *     public static final int PENDING = 0;
     *     public static final int ACCEPTED = 1;
     *     // ...
     * }
     *
     * 这种写法有 3 个致命问题：
     *
     * 1. 类型不安全：方法签名为 void updateStatus(int status)，调用方可以传 999，编译器不报错。
     * 2. 没有命名空间：和其他常量混在一起，IDE 补全没有提示边界。
     * 3. 无法携带数据：你要中文描述，就得另写一个 Map 或 switch。
     *
     * 用 enum 之后：
     *
     * public void updateStatus(OrderStatus status)  // 类型安全，只能传 7 种值
     *
     * OrderStatus. 打点之后 IDE 只列这 7 个，传错编译不过。code 和 description 天然绑定在常量上。
     *
     */


}
