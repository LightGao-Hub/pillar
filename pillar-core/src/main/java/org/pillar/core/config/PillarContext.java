package org.pillar.core.config;

import static org.pillar.core.enums.CommonConstants.EXECUTE_HASH;
import static org.pillar.core.enums.CommonConstants.FIRST;
import static org.pillar.core.enums.CommonConstants.HASH_VALUE_SPLIT;
import static org.pillar.core.enums.CommonConstants.HASH_VALUE_SPLIT_ESCAPE;
import static org.pillar.core.enums.CommonConstants.HEART_HASH;
import static org.pillar.core.enums.CommonConstants.HIGN_QUEUE;
import static org.pillar.core.enums.CommonConstants.LEADER_LOCK;
import static org.pillar.core.enums.CommonConstants.LEADER_NAME;
import static org.pillar.core.enums.CommonConstants.LOW_QUEUE;
import static org.pillar.core.enums.CommonConstants.MASTER_LOCK;
import static org.pillar.core.enums.CommonConstants.MASTER_PREFIX;
import static org.pillar.core.enums.CommonConstants.MEDIUM_QUEUE;
import static org.pillar.core.enums.CommonConstants.REDIS_FORMAT;
import static org.pillar.core.enums.CommonConstants.REDIS_SPLIT;
import static org.pillar.core.enums.CommonConstants.RESULT_QUEUE;
import static org.pillar.core.enums.CommonConstants.SLAVE_LOCK;
import static org.pillar.core.enums.CommonConstants.SLAVE_PREFIX;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.Getter;
import org.bigdata.redisson.common.utils.RedissonUtils;
import org.pillar.core.enums.QueueType;

/**
 * 由于用户可能在一个服务中启动多个master/slave, pConfig参数不同, 故此处context非静态使用
 *
 * Author: GL
 * Date: 2022-03-26
 */
@Getter
public final class PillarContext implements PContext {

    private final PConfig config;
    private final String pidHostname;
    private final RedissonUtils redissonUtils;

    public PillarContext(PConfig config) {
        this.config = config;
        this.pidHostname = ManagementFactory.getRuntimeMXBean().getName();
        this.redissonUtils = RedissonUtils.getInstance(Optional.ofNullable(config.getRedissonClient()));
    }

    @Override
    public String prefix() {
        return this.config.getPrefix();
    }

    @Override
    public long heartbeatInterval() {
        return this.config.getHeartbeatInterval();
    }

    @Override
    public int expirationCount() {
        return this.config.getExpirationCount();
    }

    @Override
    public long timestamp() {
        return System.currentTimeMillis();
    }

    /**
     * 获取节点过期时间阈值
     */
    @Override
    public long expirationTime() {
        return expirationCount() * heartbeatInterval();
    }

    @Override
    public String leaderLock() {
        return redisFormat(prefix(), LEADER_LOCK);
    }

    @Override
    public String leaderName() {
        return redisFormat(prefix(), LEADER_NAME);
    }

    @Override
    public String hignQueue() {
        return redisFormat(prefix(), HIGN_QUEUE);
    }

    @Override
    public String mediumQueue() {
        return redisFormat(prefix(), MEDIUM_QUEUE);
    }

    @Override
    public String lowQueue() {
        return redisFormat(prefix(), LOW_QUEUE);
    }

    @Override
    public String getQueue(QueueType queueType) {
        return redisFormat(prefix(), queueType.getQueue());
    }

    @Override
    public List<String> getAllQueue() {
        return Arrays.asList(hignQueue(), mediumQueue(), lowQueue());
    }

    @Override
    public List<QueueType> getAllQueueType() {
        return Arrays.asList(QueueType.HIGN, QueueType.MEDIUM, QueueType.LOW);
    }

    @Override
    public String resultQueue() {
        return redisFormat(prefix(), RESULT_QUEUE);
    }

    @Override
    public String masterConsumerLock() {
        return redisFormat(prefix(), MASTER_LOCK);
    }

    @Override
    public String slaveConsumerLock() {
        return redisFormat(prefix(), SLAVE_LOCK);
    }

    @Override
    public String executeHash() {
        return redisFormat(prefix(), EXECUTE_HASH);
    }

    @Override
    public String heartHash() {
        return redisFormat(prefix(), HEART_HASH);
    }

    @Override
    public String masterHashKey() {
        return redisFormat(MASTER_PREFIX, this.pidHostname);
    }

    @Override
    public String slaveHashKey() {
        return redisFormat(SLAVE_PREFIX, this.pidHostname);
    }

    @Override
    public List<String> getAllKey() {
        return Stream.of(hignQueue(), mediumQueue(), lowQueue(), resultQueue(), leaderLock(), leaderName(), masterConsumerLock(),
                slaveConsumerLock(), executeHash(), heartHash()).collect(Collectors.toList());
    }

    @Override
    public String addPillarSplit(Optional<String> source, String value) {
        Objects.requireNonNull(value);
        return source.map(s -> s.concat(HASH_VALUE_SPLIT).concat(value)).orElse(value);
    }

    @Override
    public List<String> deletePillarSplit(String value) {
        Objects.requireNonNull(value);
        return new ArrayList<>(Arrays.asList(value.split(HASH_VALUE_SPLIT_ESCAPE)));
    }

    private String redisFormat(String prefix, String suffix) {
        return String.format(REDIS_FORMAT, redisSplitEnd(prefix), redisSplitStart(suffix));
    }

    private String redisSplitEnd(String key) {
        return key.endsWith(REDIS_SPLIT) ? key : key.concat(REDIS_SPLIT);
    }

    private String redisSplitStart(String key) {
        return key.startsWith(REDIS_SPLIT) ? key.substring(FIRST) : key;
    }

}
