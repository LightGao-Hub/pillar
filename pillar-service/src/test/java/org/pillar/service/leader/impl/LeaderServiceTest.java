package org.pillar.service.leader.impl;

import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.pillar.core.config.PContext;
import org.pillar.core.config.PillarConfig;
import org.pillar.core.config.PillarContext;
import org.pillar.service.leader.LeaderService;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import java.io.IOException;
import java.net.URL;
import java.util.Optional;

import static org.pillar.core.enums.CommonConstants.END_INDEX;
import static org.pillar.core.enums.CommonConstants.REDIS_SPLIT;
import static org.pillar.core.enums.CommonConstants.SECOND;
import static org.pillar.core.enums.CommonConstants.ZERO;

/**
 * Author: GL
 * Date: 2022-03-28
 */
@Slf4j
public class LeaderServiceTest {
    private final String prefix = "test:leader";
    private final long heartbeatInterval = 10 * 1000;
    private final int expirationCount = 3;
    private final long leaderSleep = 1000;
    private final long sleep = 1000 * 60 * 60;
    private PContext context;

    @Before
    public void setUp() throws IOException {
        final URL resource = LeaderServiceTest.class.getClassLoader().getResource("redisson.yml");
        final RedissonClient redissonClient = Redisson.create(Config.fromYAML(resource));
        final PillarConfig config =
                PillarConfig.builder().prefix(prefix).redissonClient(redissonClient).heartbeatInterval(heartbeatInterval).expirationCount(expirationCount).build();
        log.info(String.format("init config: %s", config));
        context = new PillarContext(config);
    }

    @After
    public void tearDown() {
        clean();
    }

    @Test
    public void testSlaveShutdown() throws InterruptedException {
        setSlaveExecute();
        LeaderService leaderServiceM = new LeaderServiceImpl(context, context.masterHashKey());
        Thread.sleep(leaderSleep);
        LeaderService leaderServiceS = new LeaderServiceImpl(context, context.slaveHashKey());
        Thread.sleep(heartbeatInterval * SECOND);
        // slave.shutdown, 等待Master逻辑处理, 30s后高优队列hignQueue 会有数据
        leaderServiceS.close();
        Thread.sleep(heartbeatInterval * expirationCount * SECOND);
        log.info("hignQueue: {}", String.join(REDIS_SPLIT, context.getRedissonUtils().zrange(context.hignQueue(), ZERO, END_INDEX)));
        Thread.sleep(sleep);
    }

    @Test
    public void testMasterShutdown() throws InterruptedException {
        setMasterExecute();
        LeaderService leaderServiceM = new LeaderServiceImpl(context, context.masterHashKey());
        Thread.sleep(leaderSleep);
        LeaderService leaderServiceS = new LeaderServiceImpl(context, context.slaveHashKey());
        Thread.sleep(heartbeatInterval * SECOND);
        // master.shutdown, slave晋升leader, 等待slave逻辑处理, 30s后高优队列resultQueue 会有数据
        leaderServiceM.close();
        Thread.sleep(heartbeatInterval * expirationCount * SECOND);
        log.info("hignQueue: {}", String.join(REDIS_SPLIT, context.getRedissonUtils().zrange(context.hignQueue(), ZERO, END_INDEX)));
        Thread.sleep(sleep);
    }

    private void setSlaveExecute() {
        String value = context.addPillarSplit(Optional.of("task_second"), "task_first");
        context.getRedissonUtils().hset(context.executeHash(), context.slaveHashKey(), value);
        log.info(String.format("setExecute finished, hashExecute value: %s", value));
    }

    private void setMasterExecute() {
        String value = context.addPillarSplit(Optional.of("task_second"), "task_first");
        context.getRedissonUtils().hset(context.executeHash(), context.masterHashKey(), value);
        log.info(String.format("setExecute finished, hashExecute value: %s", value));
    }

    @Test
    public void clean() {
        context.getAllKey().forEach((v) -> context.getRedissonUtils().del(v));
    }
}
