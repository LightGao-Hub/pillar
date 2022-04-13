package org.pillar.master.context;

import static org.pillar.core.enums.CommonConstants.END_INDEX;
import static org.pillar.core.enums.CommonConstants.TRUE;
import static org.pillar.core.enums.CommonConstants.ZERO;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.bigdata.redisson.common.utils.RedissonUtils;
import org.pillar.core.config.PConfig;
import org.pillar.core.config.PContext;
import org.pillar.core.config.PillarContext;
import org.pillar.core.enums.PillarStatus;
import org.pillar.core.enums.QueueType;
import org.pillar.service.leader.LeaderService;
import org.pillar.service.leader.impl.LeaderServiceImpl;
import org.pillar.service.pillar.PillarService;
import org.pillar.service.pillar.impl.PillarServiceImpl;

/**
 * Author: GL
 * Date: 2022-03-26
 */
@Slf4j
public class PillarMaster implements PMaster<String> {

    private final PContext context;
    private final LeaderService leaderService;
    private final PillarService<String, String> pillarService;

    public PillarMaster(PConfig pConfig) {
        this.context = new PillarContext(pConfig);
        this.pillarService = new PillarServiceImpl(context);
        this.leaderService = new LeaderServiceImpl(context, context.masterHashKey());
    }

    @Override
    public void send(QueueType queue, double score, String value) {
        context.getRedissonUtils().zadd(context.getQueue(queue), score, value);
        log.info("master[{}] send finished, queue: {}, score: {}, value: {}", context.masterHashKey(), queue, score, value);
    }

    @Override
    public Optional<String> consume() {
        return context.getRedissonUtils().lock(context.masterConsumerLock(), Optional.empty(), (t) -> {
            Optional<String> rValue = context.getRedissonUtils().rlpop(context.resultQueue());
            rValue.ifPresent(v -> {
                pillarService.sendExecuteQueue(context.masterHashKey(), rValue.get());
                context.getRedissonUtils().lpop(context.resultQueue());
            });
            log.info("master[{}] consume finished, queue: {}, value: {}", context.masterHashKey(), context.resultQueue(), rValue);
            return rValue;
        });
    }

    @Override
    public PillarStatus delete(QueueType queue, String value) {
        return context.getRedissonUtils().zrem(context.getQueue(queue), value) == TRUE
                ? PillarStatus.SUCCESS : PillarStatus.NON_EXISTENT;
    }

    @Override
    public PillarStatus delete(String value) {
        for (QueueType queueType : context.getAllQueueType()) {
            if (delete(queueType, value) == PillarStatus.SUCCESS) {
                return PillarStatus.SUCCESS;
            }
        }
        return PillarStatus.NON_EXISTENT;
    }

    @Override
    public Collection<RedissonUtils.ScoredEntryEx<String>> getQueue(QueueType queue) {
        return context.getRedissonUtils().zrangebyscore(context.getQueue(queue), ZERO, END_INDEX);
    }

    @Override
    public Map<QueueType, Collection<RedissonUtils.ScoredEntryEx<String>>> getAllQueue() {
        return new HashMap<QueueType, Collection<RedissonUtils.ScoredEntryEx<String>>>() {{
            context.getAllQueueType().forEach(queue -> put(queue,
                    context.getRedissonUtils().zrangebyscore(context.getQueue(queue), ZERO, END_INDEX)));
        }};
    }

    @Override
    public Optional<List<String>> getExecuteQueue() {
        Optional<String> value = context.getRedissonUtils().hget(context.executeHash(), context.masterHashKey());
        return value.map(context::deletePillarSplit);
    }

    @Override
    public int getExecuteQueueSum() {
        return pillarService.getExecuteQueueSum(context.masterHashKey());
    }

    @Override
    public int getResultQueueSum() {
        return context.getRedissonUtils().llen(context.resultQueue());
    }

    @Override
    public double getQueueMax(QueueType queue) {
        return context.getRedissonUtils().zmax(context.getQueue(queue));
    }

    @Override
    public int getQueueSize(QueueType queue) {
        return context.getRedissonUtils().zcard(context.getQueue(queue));
    }

    @Override
    public Map<QueueType, Integer> getQueueSize() {
        return new HashMap<QueueType, Integer>() {{
            context.getAllQueueType().forEach(queue -> put(queue, getQueueSize(queue)));
        }};
    }

    @Override
    public void commit(String resultValue) {
        pillarService.commitExecuteTask(context.masterHashKey(), resultValue);
        log.info("master[{}] commit finished, executeHash: {}, resultValue: {}", context.masterHashKey(),
                context.executeHash(), resultValue);
    }

    @Override
    public String getNodeInfo() {
        return context.masterHashKey();
    }

    @Override
    public Set<String> getActiveNodeInfo() {
        return leaderService.getActiveNodeInfo();
    }

    @Override
    public Optional<String> getLeaderInfo() {
        return leaderService.getLeaderInfo();
    }

    @Override
    public void close() {
        this.leaderService.close();
    }
}