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
public class PillarMaster implements Master<String> {

    private final PillarContext pillarContext;
    private final LeaderService leaderService;
    private final PillarService<String, String> pillarService;

    public PillarMaster(PConfig pConfig) {
        this.pillarContext = new PillarContext(pConfig);
        this.pillarService = new PillarServiceImpl(pillarContext);
        this.leaderService = new LeaderServiceImpl(pillarContext, pillarContext.masterHashKey());
    }

    @Override
    public void send(QueueType queue, double score, String value) {
        pillarContext.getRedissonUtils().zadd(pillarContext.getQueue(queue), score, value);
        log.info("master[{}] send finished, queue: {}, score: {}, value: {}", pillarContext.masterHashKey(), queue, score, value);
    }

    @Override
    public Optional<String> consume() {
        return pillarContext.getRedissonUtils().lock(pillarContext.masterConsumerLock(), Optional.empty(), (t) -> {
            Optional<String> rValue = pillarContext.getRedissonUtils().rlpop(pillarContext.resultQueue());
            rValue.ifPresent(v -> {
                pillarService.sendExecuteQueue(pillarContext.masterHashKey(), rValue.get());
                pillarContext.getRedissonUtils().lpop(pillarContext.resultQueue());
            });
            log.info("master[{}] consume finished, queue: {}, value: {}", pillarContext.masterHashKey(), pillarContext.resultQueue(), rValue);
            return rValue;
        });
    }

    @Override
    public PillarStatus delete(QueueType queue, String value) {
        return pillarContext.getRedissonUtils().zrem(pillarContext.getQueue(queue), value) == TRUE
                ? PillarStatus.SUCCESS : PillarStatus.NON_EXISTENT;
    }

    @Override
    public PillarStatus delete(String value) {
        for (QueueType queueType : pillarContext.getAllQueueType()) {
            if (delete(queueType, value) == PillarStatus.SUCCESS) {
                return PillarStatus.SUCCESS;
            }
        }
        return PillarStatus.NON_EXISTENT;
    }

    @Override
    public Collection<RedissonUtils.ScoredEntryEx<String>> getQueue(QueueType queue) {
        return pillarContext.getRedissonUtils().zrangebyscore(pillarContext.getQueue(queue), ZERO, END_INDEX);
    }

    @Override
    public Map<QueueType, Collection<RedissonUtils.ScoredEntryEx<String>>> getAllQueue() {
        return new HashMap<QueueType, Collection<RedissonUtils.ScoredEntryEx<String>>>() {{
            pillarContext.getAllQueueType().forEach(queue -> put(queue,
                    pillarContext.getRedissonUtils().zrangebyscore(pillarContext.getQueue(queue), ZERO, END_INDEX)));
        }};
    }

    @Override
    public Optional<List<String>> getExecuteQueue() {
        Optional<String> value = pillarContext.getRedissonUtils().hget(pillarContext.executeHash(), pillarContext.masterHashKey());
        return value.map(pillarContext::deletePillarSplit);
    }

    @Override
    public int getExecuteQueueSum() {
        return pillarService.getExecuteQueueSum(pillarContext.masterHashKey());
    }

    @Override
    public int getResultQueueSum() {
        return pillarContext.getRedissonUtils().llen(pillarContext.resultQueue());
    }

    @Override
    public double getQueueMax(QueueType queue) {
        return pillarContext.getRedissonUtils().zmax(pillarContext.getQueue(queue));
    }

    @Override
    public int getQueueSize(QueueType queue) {
        return pillarContext.getRedissonUtils().zcard(pillarContext.getQueue(queue));
    }

    @Override
    public Map<QueueType, Integer> getQueueSize() {
        return new HashMap<QueueType, Integer>() {{
            pillarContext.getAllQueueType().forEach(queue -> put(queue, getQueueSize(queue)));
        }};
    }

    @Override
    public void commit(String resultValue) {
        pillarService.commitExecuteTask(pillarContext.masterHashKey(), resultValue);
        log.info("master[{}] commit finished, executeHash: {}, resultValue: {}", pillarContext.masterHashKey(),
                pillarContext.executeHash(), resultValue);
    }

    @Override
    public String getNodeInfo() {
        return pillarContext.masterHashKey();
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