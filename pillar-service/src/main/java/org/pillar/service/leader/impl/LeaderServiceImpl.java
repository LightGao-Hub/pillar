package org.pillar.service.leader.impl;

import static org.pillar.core.enums.CommonConstants.MASTER_PREFIX;
import static org.pillar.core.enums.CommonConstants.SLAVE_PREFIX;
import static org.pillar.core.enums.CommonConstants.TRUE;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;
import org.bigdata.redisson.common.utils.RedissonUtils;
import org.pillar.core.config.PillarContext;
import org.pillar.core.execption.PillarHashPrefixException;
import org.pillar.core.lambdas.ProcessLambda;
import org.pillar.service.leader.LeaderService;

/**
 * Author: GL
 * Date: 2022-03-26
 */
@Slf4j
public class LeaderServiceImpl implements LeaderService {
    private final AtomicBoolean stopper = new AtomicBoolean();
    private final PillarContext pillarContext;
    private final RedissonUtils redissonUtils;
    private final Thread heartThread;
    private final Thread leaderThread;
    private final String nodeInfo;

    public LeaderServiceImpl(PillarContext pillarContext, String nodeInfo) {
        this.nodeInfo = nodeInfo;
        this.pillarContext = pillarContext;
        this.heartThread = new Thread(() -> process(this::heart));
        this.leaderThread = new Thread(this::seize);
        this.redissonUtils = RedissonUtils.getInstance(Optional.empty());
        init();
    }

    @Override
    public void init() {
        this.heartThread.start();
        this.leaderThread.start();
    }

    @Override
    public void heart() {
        final long timestamp = pillarContext.timestamp();
        redissonUtils.hset(pillarContext.heartHash(), nodeInfo, timestamp);
        log.info("send heart, hashkey: {}, nodeInfo: {}, timestamp: {}", pillarContext.heartHash(), nodeInfo, timestamp);
    }

    @Override
    public void seize() {
        redissonUtils.lock(pillarContext.leaderLock(), Optional.empty(), (t) -> {
            listen();
            return t;
        });
    }

    @Override
    public void listen() {
        redissonUtils.set(pillarContext.leaderName(), nodeInfo);
        log.info("leader login was successful, key: {}, value: {}", pillarContext.leaderName(), nodeInfo);
        process(this::listener);
    }

    @Override
    public Set<String> getActiveNodeInfo() {
        Map<String, Long> heartMap = redissonUtils.hgetall(pillarContext.heartHash());
        return heartMap.keySet();
    }

    @Override
    public Optional<String> getLeaderInfo() {
        return redissonUtils.get(pillarContext.leaderName());
    }

    private void listener() {
        Map<String, Long> heartMap = redissonUtils.hgetall(pillarContext.heartHash());
        long timestamp = pillarContext.timestamp();
        log.info("listen heart, leader: {}, redisKey:{}, heartMap: {}, timestamp: {}", redissonUtils.get(pillarContext.leaderName()),
                pillarContext.heartHash(), heartMap, timestamp);
        heartMap.forEach((k, v) -> {
            if (timestamp - v > pillarContext.expirationTime()) {
                Optional<String> value = redissonUtils.hget(pillarContext.executeHash(), k);
                log.warn("Node downtime processing start, k: {}, v: {}, timestamp: {}, executeHash: {}, executeValue: {}", k, v,
                        timestamp, pillarContext.executeHash(), value);
                if (k.startsWith(MASTER_PREFIX)) {
                    value.ifPresent(this::acceptMaster);
                } else if (k.startsWith(SLAVE_PREFIX)) {
                    value.ifPresent(this::acceptSlave);
                } else {
                    throw new PillarHashPrefixException(String.format("executeHash key prefix is not present, key: %s", k));
                }
                redissonUtils.hdel(pillarContext.executeHash(), k);
                redissonUtils.hdel(pillarContext.heartHash(), k);
                log.warn("node downtime processing Successful, delete old executeHash: {}, delete old heartKey: {}, ", k, k);
            }
        });
    }

    /**
     * 若master节点宕机, 则将执行中数据存储至结果队列头部
     */
    public void acceptMaster(String value) {
        pillarContext.deletePillarSplit(value).forEach((v) -> redissonUtils.lpush(pillarContext.resultQueue(), v));
        log.warn("master node downtime processing end, value: {}, to resultQueue: {}", value, pillarContext.resultQueue());
    }

    /**
     *  若slave节点宕机, 则将执行中数据存储至高优队列最高优先级
     */
    public void acceptSlave(String value) {
        double zmax = redissonUtils.zmax(pillarContext.hignQueue());
        pillarContext.deletePillarSplit(value)
                .forEach((v) -> redissonUtils.zadd(pillarContext.hignQueue(), zmax, v));
        log.warn("slave node downtime processing end, value: {}, score: {} to hignQueue: {}", value, zmax, pillarContext.hignQueue());
    }

    public void process(ProcessLambda lambda) {
        while (!stopper.get()) {
            lambda.process();
            waiting(pillarContext.heartbeatInterval());
        }
    }

    private void waiting(long interval) {
        try {
            Thread.sleep(interval);
        } catch (InterruptedException e) {
            log.error("thread sleep error!", e);
        }
    }

    @Override
    public void close() {
        this.stopper.set(TRUE);
        log.warn("shutdown nodeInfo: {} ", nodeInfo);
    }
}