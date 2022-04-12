package org.pillar.core.config;

import org.redisson.api.RedissonClient;

/**
 * Author: GL
 * Date: 2022-03-26
 */
public interface PConfig {

    /**
     *  redis前缀, master 和 slave 的前缀要保持一致, 否则无法消费任务
     */
    String getPrefix();

    /**
     *  根据用户实际环境创建的redissonClient
     */
    RedissonClient getRedissonClient();

    /**
     * 心跳间隔ms, 此参数也将作为leader检查节点是否宕机的时间间隔
     */
    long getHeartbeatInterval();

    /**
     * 心跳缺失次数, 例如HeartbeatInterval=30000ms,  ExpirationCount=5
     * 代表节点如果超过150s没有更新, 代表此节点宕机
     */
    int getExpirationCount();
}
