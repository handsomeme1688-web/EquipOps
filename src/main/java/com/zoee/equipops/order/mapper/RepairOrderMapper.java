package com.zoee.equipops.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zoee.equipops.order.domain.entity.RepairOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface RepairOrderMapper extends BaseMapper<RepairOrder> {

    /** 悲观锁：FOR UPDATE 锁住行，其他事务读同一行会阻塞等待 */
    @Select("SELECT * FROM repair_order WHERE id = #{id} FOR UPDATE")
    RepairOrder selectForUpdate(@Param("id") Long id);

    /**
     * 唯一键冲突后的当前读。FOR UPDATE 不使用旧的一致性快照，能够看到并发事务刚提交的原工单。
     */
    @Select("""
            SELECT *
            FROM repair_order
            WHERE request_user_id = #{userId}
              AND idempotency_key = #{idempotencyKey}
            FOR UPDATE
            """)
    RepairOrder selectByIdempotencyKeyForUpdate(
            @Param("userId") Long userId,
            @Param("idempotencyKey") String idempotencyKey
    );

    @Select("""
            SELECT id
            FROM repair_order
            WHERE status = 0
              AND timed_out = 0
              AND request_time < #{cutoff}
            ORDER BY request_time ASC
            LIMIT #{batchSize}
            """)
    List<Long> selectPendingTimeoutCandidateIds(
            @Param("cutoff") LocalDateTime cutoff,
            @Param("batchSize") int batchSize
    );

    @Update("""
            UPDATE repair_order
            SET timed_out = 1,
                timeout_time = #{markedAt},
                update_time = #{markedAt}
            WHERE id = #{orderId}
              AND status = 0
              AND timed_out = 0
              AND request_time < #{cutoff}
            """)
    int markPendingOrderTimedOut(
            @Param("orderId") Long orderId,
            @Param("cutoff") LocalDateTime cutoff,
            @Param("markedAt") LocalDateTime markedAt
    );
}
