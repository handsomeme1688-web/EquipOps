package com.zoee.equipops.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;


/**
 * ---
 * Redis Key 命名惯例
 *
 * 业界通用格式：业务对象:操作:标识，用冒号分隔。三个原因：
 *
 * 1. 可读性：redis-cli 里 KEYS * 一看就知道存的什么
 * 2. 分组：Redis 客户端工具（如 Another Redis Desktop Manager）按冒号分组显示为树形目录
 * 3. 批量操作：可以 DEL device:detail:* 一把清掉所有设备缓存
 *
 * ---
 * 设计推演
 *
 * ┌──────────────────┬────────────────────┬────────────────────────────────────────────────┐
 * │     缓存内容      │    key 怎么来的      │                    命名逻辑                     │
 * ├──────────────────┼────────────────────┼────────────────────────────────────────────────┤
 * │ 设备详情          │ device:detail:3    │ 业务对象=device + 操作=detail + 标识=设备ID        │
 * ├──────────────────┼────────────────────┼────────────────────────────────────────────────┤
 * │ 用户权限          │ user:permissions:5 │ 业务对象=user + 操作=permissions + 标识=用户ID     │
 * ├──────────────────┼────────────────────┼────────────────────────────────────────────────┤
 */

/**
 * 击穿 vs 穿透 vs 雪崩
 *
 *   穿透 ─── 查的数据根本不存在 → 每次穿透到 DB → 空值短 TTL
 *   雪崩 ─── 大量 key 同时过期 → DB 瞬间被打爆 → TTL 加随机值
 *   击穿 ─── 一个热点 key 刚好过期，瞬间 50 个请求同时打到 DB
 *            ↓
 *           DB 压力瞬间暴涨（虽然只查一条数据，但 50 次并发查同一条）
 * ---
 */
// Spring 启动时会扫描它，执行里面所有 @Bean 方法，把返回值放进 IoC 容器。
@Configuration
public class RedisConfig {
    // @Bean 方法的参数 Spring 会自动注入，不需要单独声明 @RequiredArgsConstructor 字段

    /**
     * Spring 发现你有个参数类型是 RedisConnectionFactory，会自动从容器里找一个 Bean 注入进来。
     * 这比 @RequiredArgsConstructor + private final 更简洁
     * @param redisConnectionFactory spring自带的
     * @return
     */
    @Bean
    public RedisTemplate<String,Object> redisTemplate(RedisConnectionFactory redisConnectionFactory){
        RedisTemplate<String,Object> redisTemplate=new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);

        // Key 序列化
        redisTemplate.setKeySerializer(RedisSerializer.string());
        redisTemplate.setHashKeySerializer(RedisSerializer.string());

        // Value 序列化：存类型信息 + jsr310 支持 LocalDateTime
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder().allowIfBaseType(Object.class).build(),
                ObjectMapper.DefaultTyping.NON_FINAL);
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        redisTemplate.setValueSerializer(jsonSerializer);
        redisTemplate.setHashValueSerializer(jsonSerializer);

        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }
}
