package com.zoee.equipops.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.zoee.equipops.common.context.UserContext;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

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
     * TODO  升级为从 SecurityContextHolder 取
     * 现在没有登录体系，先返回固定值。
     */
    private Long getCurrentUserId() {

        return UserContext.getUserId();//暂时写死
    }
}
