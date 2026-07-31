package com.zoee.equipops.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
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
        // new 一个空的 RedisTemplate 对象。此时它还不知道怎么连 Redis，也不知道怎么把 Java 对象变成二进制存进去。
        // 下面几行就是在配置这两件事。
        RedisTemplate<String,Object> redisTemplate=new RedisTemplate<>();

        // 把连接工厂塞进去。
        // Spring Boot 自动配置根据你 yaml 里的 spring.data.redis.host/port/password 帮我们造好了这个 RedisConnectionFactory。
        // 这行之后，template 就知道连哪台 Redis 了。
        redisTemplate.setConnectionFactory(redisConnectionFactory);

        // Key 的序列化器 → 用 string()。
        // 效果：key "device:detail:3" 存到 Redis 里是一段 UTF-8 字节
        redisTemplate.setKeySerializer(RedisSerializer.string());

        // Value 的序列化器 → 用 json()。
        // 效果：你把一个 DeviceVO 对象 set 进去，它自动转成 {"id":3,"name":"设备A",...} 的 JSON 字节存起来；
        // get 出来的时候自动把 JSON 字节转回 Java 对象。
        // 底层用的是 Jackson（Spring Boot 自带）。
        redisTemplate.setValueSerializer(RedisSerializer.json());

        // String   │ 一个 key 对应一个 value（最常见）
        // Hash     │ 一个 key 下有很多 field-value 对（像一个微型 HashMap）
        // 两种数据类型都涵盖
        redisTemplate.setHashKeySerializer(RedisSerializer.string());
        redisTemplate.setHashValueSerializer(RedisSerializer.json());

        // 初始化。前面设了一堆序列化器，这行让 RedisTemplate 做最后的内部检查、确认配置完整。
        redisTemplate.afterPropertiesSet();

        // 把这个配置好的 template 交给 Spring 容器。之后在 Service 里注入就能用了。
        return redisTemplate;
    }
}
