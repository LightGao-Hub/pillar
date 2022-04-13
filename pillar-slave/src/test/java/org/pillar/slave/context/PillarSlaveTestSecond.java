package org.pillar.slave.context;

import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.pillar.core.config.PContext;
import org.pillar.core.config.PillarConfig;
import org.pillar.core.config.PillarContext;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import java.net.URL;
import java.util.stream.IntStream;

import static org.pillar.core.enums.CommonConstants.REDIS_SPLIT;
import static org.pillar.core.enums.CommonConstants.SECOND;
import static org.pillar.core.enums.CommonConstants.SIXTH;
import static org.pillar.core.enums.CommonConstants.THIRD;
import static org.pillar.core.enums.CommonConstants.ZERO;

@Slf4j
public class PillarSlaveTestSecond {
    private final String prefix = "test:slave";
    private final long heartbeatInterval = 10 * 1000;
    private final int expirationCount = 3;
    private PSlave<String> pillarSlave;
    private PSlave<String> pillarSlave2;
    private PContext context;

    @Before
    public void setUp() throws Exception {
        final URL resource = PillarSlaveTestSecond.class.getClassLoader().getResource("redisson.yml");
        final RedissonClient redissonClient = Redisson.create(Config.fromYAML(resource));
        final PillarConfig config = PillarConfig.builder().prefix(prefix).redissonClient(redissonClient).heartbeatInterval(heartbeatInterval).expirationCount(expirationCount).build();
        log.info(String.format("init config: %s", config));
        context = new PillarContext(config);
        pillarSlave = new PillarSlave(config);
        pillarSlave2 = new PillarSlave(config);
    }

    @After
    public void tearDown() {
        clean();
        close();
    }

    // 用于验证多个pillarSlave消费队列, 此测试并未考虑心跳, 支持单纯测试分布式锁抢占消费过程
    @Test
    public void all() throws InterruptedException {
        setQueueTask();
        new Thread(this::consume).start();
        new Thread(this::consume2).start();
        Thread.sleep(10000);
    }

    public void consume() {
        IntStream.range(ZERO, SIXTH).forEach((v) -> log.info("consume first {}: {}", v, pillarSlave.consume()));
        log.info("consume first finished, executeHash: {}", context.getRedissonUtils().hgetall(context.executeHash()));
    }

    public void consume2() {
        IntStream.range(ZERO, SIXTH).forEach((v) -> log.info("consume second {}: {}", v, pillarSlave2.consume()));
        log.info("consume second finished, executeHash: {}", context.getRedissonUtils().hgetall(context.executeHash()));
    }

    @Test
    public void close() {
        pillarSlave.close();
    }

    // 三种优先级队列各插入两条数据
    private void setQueueTask() {
        IntStream.range(ZERO, THIRD).forEach(number ->
                context.getAllQueue().forEach((queue) -> context.getRedissonUtils().zadd(queue, number, queue.concat(REDIS_SPLIT).concat(String.valueOf(number)))));
    }

    @Test
    public void clean() {
        context.getAllKey().forEach((v) -> context.getRedissonUtils().del(v));
    }
}