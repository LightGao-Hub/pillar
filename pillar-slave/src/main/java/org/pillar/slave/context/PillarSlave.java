package org.pillar.slave.context;

import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.pillar.core.config.PConfig;
import org.pillar.core.config.PillarContext;
import org.pillar.service.leader.LeaderService;
import org.pillar.service.leader.impl.LeaderServiceImpl;
import org.pillar.service.pillar.PillarService;
import org.pillar.service.pillar.impl.PillarServiceImpl;

/**
 * Author: GL
 * Date: 2022-03-26
 */
@Slf4j
public class PillarSlave implements Slave<String> {

    private final PillarContext pillarContext;
    private final PillarService<String, String> pillarService;
    private final LeaderService leaderService;

    public PillarSlave(PConfig pConfig) {
        this.pillarContext = new PillarContext(pConfig);
        this.pillarService = new PillarServiceImpl(pillarContext);
        this.leaderService = new LeaderServiceImpl(pillarContext, pillarContext.slaveHashKey());
    }

    @Override
    public Optional<String> consume() {
        return pillarContext.getRedissonUtils().lock(pillarContext.slaveConsumerLock(), Optional.empty(), (t) -> {
            for (String queueName : pillarContext.getAllQueue()) {
                Optional<String> tValue = pillarContext.getRedissonUtils().zrpop(queueName);
                if (tValue.isPresent()) {
                    pillarService.sendExecuteQueue(pillarContext.slaveHashKey(), tValue.get());
                    pillarContext.getRedissonUtils().zrem(queueName, tValue.get());
                    log.info("slave[{}] consume finished, queue: {}, value: {}", pillarContext.slaveHashKey(), queueName, tValue);
                    return tValue;
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public int getExecuteQueueSum() {
        return pillarService.getExecuteQueueSum(pillarContext.slaveHashKey());
    }

    @Override
    public void commit(String value, String executeValue) {
        pillarContext.getRedissonUtils().rpush(pillarContext.resultQueue(), value);
        pillarService.commitExecuteTask(pillarContext.slaveHashKey(), executeValue);
        log.info("slave[{}] commit finished, resultQueue: {}, value: {}", pillarContext.slaveHashKey(), pillarContext.resultQueue(), value);
    }

    @Override
    public String getNodeInfo() {
        return pillarContext.slaveHashKey();
    }

    @Override
    public void close() {
        this.leaderService.close();
    }
}
