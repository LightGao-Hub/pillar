package org.pillar.master.context;

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
import static org.pillar.core.enums.CommonConstants.SEVENTH;
import static org.pillar.core.enums.CommonConstants.THIRD;
import static org.pillar.core.enums.CommonConstants.ZERO;

@Slf4j
public class PillarMasterTestSecond {
    private final String prefix = "test:master";
    private final long heartbeatInterval = 10 * 1000;
    private final int expirationCount = 3;
    private PMaster<String> pillarMaster;
    private PMaster<String> pillarMaster2;
    private PContext context;

    @Before
    public void setUp() throws Exception {
        final URL resource = PillarMasterTestSecond.class.getClassLoader().getResource("redisson.yml");
        final RedissonClient redissonClient = Redisson.create(Config.fromYAML(resource));
        final PillarConfig config = PillarConfig.builder().prefix(prefix).redissonClient(redissonClient)
                .heartbeatInterval(heartbeatInterval).expirationCount(expirationCount).build();
        log.info(String.format("init config: %s", config));
        context = new PillarContext(config);
        pillarMaster = new PillarMaster(config);
        pillarMaster2 = new PillarMaster(config);
    }

    @After
    public void tearDown() {
        close();
        clean();
    }

    // 用于验证多个pillarMaster消费队列, 此测试并未考虑心跳, 支持单纯测试分布式锁抢占消费过程
    @Test
    public void all() throws InterruptedException {
        setResultQueue();
        new Thread(this::consume).start();
        new Thread(this::consume2).start();
        Thread.sleep(10000);
    }

    public void consume() {
        IntStream.range(ZERO, THIRD).forEach((v) -> log.info("consume first:{} {}", v, pillarMaster.consume())); // THIRD多消费一次
        log.info("consume first finished, executeHash: {}", context.getRedissonUtils().hgetall(context.executeHash()));
    }

    public void consume2() {
        IntStream.range(ZERO, THIRD).forEach((v) -> log.info("consume second:{} {}", v, pillarMaster2.consume())); // THIRD多消费一次
        log.info("consume second finished, executeHash: {}", context.getRedissonUtils().hgetall(context.executeHash()));
    }

    @Test
    public void close() {
        pillarMaster.close();
        log.info("pillarMaster closed");
    }

    private void setResultQueue() {
        IntStream.range(ZERO, SEVENTH).forEach(number ->
                context.getRedissonUtils().rpush(context.resultQueue(), context.resultQueue().concat(REDIS_SPLIT).concat(String.valueOf(number))));
    }

    @Test
    public void clean() {
        context.getAllKey().forEach((v) -> context.getRedissonUtils().del(v));
    }
}