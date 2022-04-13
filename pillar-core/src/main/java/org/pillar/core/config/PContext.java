package org.pillar.core.config;

import java.util.List;
import java.util.Optional;

import org.bigdata.redisson.common.utils.RedissonUtils;
import org.pillar.core.enums.QueueType;

/**
 * Author: GL
 * Date: 2022-04-13
 */
public interface PContext {
    String prefix();

    RedissonUtils getRedissonUtils();

    long heartbeatInterval();

    int expirationCount();

    long timestamp();

    long expirationTime();

    String leaderLock();

    String leaderName();

    String hignQueue();

    String mediumQueue();

    String lowQueue();

    String getQueue(QueueType queueType);

    List<String> getAllQueue();

    List<QueueType> getAllQueueType();

    String resultQueue();

    String masterConsumerLock();

    String slaveConsumerLock();

    String executeHash();

    String heartHash();

    String masterHashKey();

    String slaveHashKey();

    List<String> getAllKey();

    String addPillarSplit(Optional<String> source, String value);

    List<String> deletePillarSplit(String value);
}
