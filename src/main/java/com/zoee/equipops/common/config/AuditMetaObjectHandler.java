package com.zoee.equipops.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {
    /**
     * this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
     * //                    ~~~~~~~~~~~  ~~~~~~~~~~~~  ~~~~~~~~~~~~~~~~~  ~~~
     * //                        ①            ②                ③            ④
     *
     * ┌──────────────┬───────────────────────────────────────────────────┬────────────────────────┐
     * │     参数     │                       含义                        │       这段代码里       │
     * ├──────────────┼───────────────────────────────────────────────────┼────────────────────────┤
     * │ ① metaObject │ 当前正在插入的实体对象的元信息包装器              │ 框架传过来的，你不用管 │
     * ├──────────────┼───────────────────────────────────────────────────┼────────────────────────┤
     * │ ② fieldName  │ 要自动填充的字段名（Java 属性名，不是数据库列名） │ "updateTime"           │
     * ├──────────────┼───────────────────────────────────────────────────┼────────────────────────┤
     * │ ③ fieldType  │ 该字段的 Java 类型                                │ LocalDateTime.class    │
     * ├──────────────┼───────────────────────────────────────────────────┼────────────────────────┤
     * │ ④ fieldVal   │ 要填入的值                                        │ now（当前时间）        │
     * └──────────────┴───────────────────────────────────────────────────┴────────────────────────┘
     *
     * strict 是什么意思？
     *
     * MyBatis-Plus 有两个方法：
     *
     * // strict：只在字段值为 null 时才填充
     * strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
     *
     * // 非 strict：不管原有值是什么，直接覆盖
     * fillStrategy(metaObject, "updateTime", LocalDateTime.class, now);
     *
     * strict 版本更安全——如果你在代码里手动 set 了值，它不会覆盖你的值。
     * @param metaObject 元对象
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "createBy", Long.class, getCurrentUserId());
        this.strictInsertFill(metaObject, "updateBy", Long.class, getCurrentUserId());


    }

    @Override
    public void updateFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, now);
        this.strictUpdateFill(metaObject, "updateBy", Long.class, getCurrentUserId());

    }



    /**
     * 获取当前登录用户 id。
     * TODO Day 11: 从 UserContext(ThreadLocal) 取
     * TODO Day 16: 升级为从 SecurityContextHolder 取
     * 现在没有登录体系，先返回固定值。
     */
    private Long getCurrentUserId() {
        return 1L;//暂时写死
    }
}
