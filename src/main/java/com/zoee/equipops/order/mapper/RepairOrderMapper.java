package com.zoee.equipops.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zoee.equipops.order.domain.entity.RepairOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RepairOrderMapper extends BaseMapper<RepairOrder> {

    /** 悲观锁：FOR UPDATE 锁住行，其他事务读同一行会阻塞等待 */
    @Select("SELECT * FROM repair_order WHERE id = #{id} FOR UPDATE")
    RepairOrder selectForUpdate(@Param("id") Long id);
}
