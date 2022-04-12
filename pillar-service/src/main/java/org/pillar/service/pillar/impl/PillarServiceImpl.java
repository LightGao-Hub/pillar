package org.pillar.service.pillar.impl;

import static org.pillar.core.enums.CommonConstants.HASH_VALUE_SPLIT;
import static org.pillar.core.enums.CommonConstants.ZERO;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.pillar.core.config.PillarContext;
import org.pillar.service.pillar.PillarService;

/**
 * Author: GL
 * Date: 2022-03-26
 */
@Slf4j
public class PillarServiceImpl implements PillarService<String, String> {

    private final PillarContext pillarContext;

    public PillarServiceImpl(PillarContext pillarContext) {
        this.pillarContext = pillarContext;
    }

    @Override
    public void sendExecuteQueue(String hashKey, String value) {
        Optional<String> oldHashValue = pillarContext.getRedissonUtils().hget(pillarContext.executeHash(), hashKey);
        String hashValue = pillarContext.addPillarSplit(oldHashValue, value);
        pillarContext.getRedissonUtils().hset(pillarContext.executeHash(), hashKey, hashValue);
        log.info("PillarService:sendExecuteQueue, executeHash: {}, hashKey: {}, value: {}, oldHashValue: {}, newHashValue: {}",
                pillarContext.executeHash(), hashKey, value, oldHashValue, hashValue);
    }

    @Override
    public void commitExecuteTask(String hashKey, String value) {
        Optional<String> oldHashValue = pillarContext.getRedissonUtils().hget(pillarContext.executeHash(), hashKey);
        oldHashValue.ifPresent((v) -> {
            List<String> collect = pillarContext.deletePillarSplit(v).stream().filter((s) -> !s.equals(value)).collect(Collectors.toList());
            String hashValue = null;
            if (collect.isEmpty()) {
                pillarContext.getRedissonUtils().hdel(pillarContext.executeHash(), hashKey);
            } else {
                hashValue = String.join(HASH_VALUE_SPLIT, collect);
                pillarContext.getRedissonUtils().hset(pillarContext.executeHash(), hashKey, hashValue);
            }
            log.info("[{}] commitExecuteTask, executeHash: {}, hashKey: {}, value: {}, oldHashValue: {}, newHashValue: {}",
                    hashKey, pillarContext.executeHash(), hashKey, value, oldHashValue, hashValue);
        });
    }

    @Override
    public int getExecuteQueueSum(String hashKey) {
        Optional<String> execute = pillarContext.getRedissonUtils().hget(pillarContext.executeHash(), hashKey);
        return execute.map(s -> pillarContext.deletePillarSplit(s).size()).orElse(ZERO);
    }
}
