package org.pillar.service.pillar.impl;

import static org.pillar.core.enums.CommonConstants.SECOND;
import static org.pillar.core.enums.CommonConstants.ZERO;

import java.net.URL;
import java.util.stream.IntStream;

import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.pillar.core.config.PContext;
import org.pillar.core.config.PillarConfig;
import org.pillar.core.config.PillarContext;
import org.pillar.service.leader.impl.LeaderServiceTest;
import org.pillar.service.pillar.PillarService;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

@Slf4j
public class PillarServiceImplTest {
    private final String prefix = "test:pillar:service";
    private final long heartbeatInterval = 10 * 1000;
    private final int expirationCount = 3;
    private PContext context;
    private PillarService<String, String> pillarService;

    @Before
    public void setUp() throws Exception {
        final URL resource = LeaderServiceTest.class.getClassLoader().getResource("redisson.yml");
        final RedissonClient redissonClient = Redisson.create(Config.fromYAML(resource));
        final PillarConfig config =
                PillarConfig.builder().prefix(prefix).redissonClient(redissonClient).heartbeatInterval(heartbeatInterval).expirationCount(expirationCount).build();
        log.info(String.format("init config: %s", config));
        context = new PillarContext(config);
        pillarService = new PillarServiceImpl(context);
    }

    @After
    public void tearDown() {
        clean();
    }

    @Test
    public void sendExecuteQueue() {
        IntStream.range(ZERO, SECOND).forEach((v) -> pillarService.sendExecuteQueue(context.masterHashKey(), String.valueOf(v)));
        log.info("sendExecuteQueue finished, executeHash: {}, hashKey: {}, value: {}", context.executeHash(),
                context.masterHashKey(), context.getRedissonUtils().hget(context.executeHash(), context.masterHashKey()));
    }

    @Test
    public void commitExecuteTask() {
        sendExecuteQueue();
        IntStream.range(ZERO, SECOND).forEach((v) -> pillarService.commitExecuteTask(context.masterHashKey(), String.valueOf(v)));
        log.info("commitExecuteTask finished, executeHash: {}, hashKey: {}, value: {}", context.executeHash(),
                context.masterHashKey(), context.getRedissonUtils().hget(context.executeHash(), context.masterHashKey()));
    }

    @Test
    public void clean() {
        context.getAllKey().forEach((v) -> context.getRedissonUtils().del(v));
    }
}