package org.pillar.service.pillar.impl;

import static org.pillar.core.enums.CommonConstants.HASH_VALUE_SPLIT;
import static org.pillar.core.enums.CommonConstants.ZERO;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.pillar.core.config.PContext;
import org.pillar.service.pillar.PillarService;

/**
 * Author: GL
 * Date: 2022-03-26
 */
@Slf4j
public class PillarServiceImpl implements PillarService<String, String> {

    private final PContext context;

    public PillarServiceImpl(PContext context) {
        this.context = context;
    }

    @Override
    public void sendExecuteQueue(String hashKey, String value) {
        Optional<String> oldHashValue = context.getRedissonUtils().hget(context.executeHash(), hashKey);
        String hashValue = context.addPillarSplit(oldHashValue, value);
        context.getRedissonUtils().hset(context.executeHash(), hashKey, hashValue);
        log.debug("PillarService:sendExecuteQueue, executeHash: {}, hashKey: {}, value: {}, oldHashValue: {}, newHashValue: {}",
                context.executeHash(), hashKey, value, oldHashValue, hashValue);
    }

    @Override
    public void commitExecuteTask(String hashKey, String value) {
        Optional<String> oldHashValue = context.getRedissonUtils().hget(context.executeHash(), hashKey);
        oldHashValue.ifPresent((v) -> {
            List<String> collect = context.deletePillarSplit(v).stream().filter((s) -> !s.equals(value)).collect(Collectors.toList());
            String hashValue = null;
            if (collect.isEmpty()) {
                context.getRedissonUtils().hdel(context.executeHash(), hashKey);
            } else {
                hashValue = String.join(HASH_VALUE_SPLIT, collect);
                context.getRedissonUtils().hset(context.executeHash(), hashKey, hashValue);
            }
            log.debug("[{}] commitExecuteTask, executeHash: {}, hashKey: {}, value: {}, oldHashValue: {}, newHashValue: {}",
                    hashKey, context.executeHash(), hashKey, value, oldHashValue, hashValue);
        });
    }

    @Override
    public int getExecuteQueueSum(String hashKey) {
        Optional<String> execute = context.getRedissonUtils().hget(context.executeHash(), hashKey);
        return execute.map(s -> context.deletePillarSplit(s).size()).orElse(ZERO);
    }
}
