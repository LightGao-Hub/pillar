package org.pillar.slave.context;

import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.pillar.core.config.PConfig;
import org.pillar.core.config.PContext;
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

    private final PContext context;
    private final PillarService<String, String> pillarService;
    private final LeaderService leaderService;

    public PillarSlave(PConfig pConfig) {
        this.context = new PillarContext(pConfig);
        this.pillarService = new PillarServiceImpl(context);
        this.leaderService = new LeaderServiceImpl(context, context.slaveHashKey());
    }

    @Override
    public Optional<String> consume() {
        return context.getRedissonUtils().lock(context.slaveConsumerLock(), Optional.empty(), (t) -> {
            for (String queueName : context.getAllQueue()) {
                Optional<String> tValue = context.getRedissonUtils().zrpop(queueName);
                if (tValue.isPresent()) {
                    pillarService.sendExecuteQueue(context.slaveHashKey(), tValue.get());
                    context.getRedissonUtils().zrem(queueName, tValue.get());
                    log.info("slave[{}] consume finished, queue: {}, value: {}", context.slaveHashKey(), queueName, tValue);
                    return tValue;
                }
            }
            return Optional.empty();
        });
    }

    @Override
    public int getExecuteQueueSum() {
        return pillarService.getExecuteQueueSum(context.slaveHashKey());
    }

    @Override
    public void commit(String value, String executeValue) {
        context.getRedissonUtils().rpush(context.resultQueue(), value);
        pillarService.commitExecuteTask(context.slaveHashKey(), executeValue);
        log.info("slave[{}] commit finished, resultQueue: {}, value: {}", context.slaveHashKey(), context.resultQueue(), value);
    }

    @Override
    public String getNodeInfo() {
        return context.slaveHashKey();
    }

    @Override
    public void close() {
        this.leaderService.close();
    }
}
