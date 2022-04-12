package org.pillar.master.context;

import static org.pillar.core.enums.CommonConstants.FIRST;
import static org.pillar.core.enums.CommonConstants.REDIS_SPLIT;
import static org.pillar.core.enums.CommonConstants.SECOND;
import static org.pillar.core.enums.CommonConstants.THIRD;
import static org.pillar.core.enums.CommonConstants.ZERO;

import java.net.URL;
import java.util.stream.IntStream;

import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.pillar.core.config.PillarConfig;
import org.pillar.core.config.PillarContext;
import org.pillar.core.enums.QueueType;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

@Slf4j
public class PillarMasterTest {
    private final String prefix = "test:master";
    private final long heartbeatInterval = 10 * 1000;
    private final int expirationCount = 3;
    private Master<String> pillarMaster;
    private PillarContext context;

    @Before
    public void setUp() throws Exception {
        final URL resource = PillarMasterTest.class.getClassLoader().getResource("redisson.yml");
        final RedissonClient redissonClient = Redisson.create(Config.fromYAML(resource));
        final PillarConfig config = PillarConfig.builder().prefix(prefix).redissonClient(redissonClient)
                .heartbeatInterval(heartbeatInterval).expirationCount(expirationCount).build();
        log.info(String.format("init config: %s", config));
        context = new PillarContext(config);
        pillarMaster = new PillarMaster(config);
    }

    @After
    public void tearDown() {
        close();
        clean();
    }

    @Test
    public void all() {
        getNodeInfo();
        getActiveNodeInfo();
        getLeaderInfo();
        send();
        getQueue();
        getAllQueue();
        delete();
        getAllQueue();
        allDelete();
        getAllQueue();
        getQueueMax();
        getQueueSize();
        getAllQueueSize();
        setResultQueue(); // 结果队列各插入两条数据
        getResultQueueSum();
        consume();
        getExecuteQueue();
        commit();
    }

    @Test
    public void send() {
        IntStream.range(ZERO, SECOND).forEach(weight -> context.getAllQueueType().forEach(queue -> // 三种优先级队列各插入两条数据
                        pillarMaster.send(queue, weight, String.valueOf(queue).concat(String.valueOf(weight)))));
        log.info("send finished");
    }

    @Test
    public void consume() {
        IntStream.range(ZERO, THIRD).forEach((v) -> log.info("consume: {}", pillarMaster.consume())); // THIRD多消费一次
        log.info("consume finished, executeHash: {}", context.getRedissonUtils().hgetall(context.executeHash()));
    }

    @Test
    public void delete() {
        final String value = String.valueOf(QueueType.HIGN).concat(String.valueOf(FIRST));
        log.info("delete value:{} finished, boolean: {}", value, pillarMaster.delete(QueueType.HIGN, value));
    }

    @Test
    public void allDelete() {
        final String value = String.valueOf(QueueType.LOW).concat(String.valueOf(FIRST));
        log.info("testDelete value: {} finished, boolean: {}", value, pillarMaster.delete(value));
    }

    @Test
    public void getQueue() {
        context.getAllQueueType().forEach(queue -> log.info("getQueue finished, {}:{}", queue, pillarMaster.getQueue(queue)));
    }

    @Test
    public void getAllQueue() {
        log.info("getAllQueue finished, {}", pillarMaster.getAllQueue());
    }

    @Test
    public void getExecuteQueue() {
        log.info("getExecuteQueue finished, {}", pillarMaster.getExecuteQueue());
    }

    @Test
    public void getExecuteQueueSum() {
        log.info("getExecuteQueueSum finished, {}", pillarMaster.getExecuteQueueSum());
    }

    @Test
    public void getResultQueueSum() {
        log.info("getResultQueueSum finished, {}", pillarMaster.getResultQueueSum());
    }

    @Test
    public void getQueueMax() {
        context.getAllQueueType().forEach(queue -> log.info("getQueueMax finished, queue: {}, max: {}", queue, pillarMaster.getQueueMax(queue)));
    }

    @Test
    public void getQueueSize() {
        context.getAllQueueType().forEach(queue -> log.info("getQueueSum finished, queue: {}, sum: {}", queue, pillarMaster.getQueueSize(queue)));
    }

    @Test
    public void getAllQueueSize() {
        log.info("getAllQueueSize finished, map: {}", pillarMaster.getQueueSize());
    }

    @Test
    public void commit() {
        IntStream.range(ZERO, THIRD).forEach((number) ->
                pillarMaster.commit(context.resultQueue().concat(REDIS_SPLIT).concat(String.valueOf(number)))); // THIRD多提交一次
        log.info("commit finished, ExecuteQueue: {}", pillarMaster.getExecuteQueue());
    }

    @Test
    public void getNodeInfo() {
        log.info("getNodeInfo finished, info: {}", pillarMaster.getNodeInfo());
    }

    @Test
    public void getActiveNodeInfo() {
        log.info("getActiveNodeInfo finished, activeInfo: {}", pillarMaster.getActiveNodeInfo());
    }

    @Test
    public void getLeaderInfo() {
        log.info("getLeaderInfo finished, leaderInfo: {}", pillarMaster.getLeaderInfo());
    }

    @Test
    public void close() {
        pillarMaster.close();
        log.info("pillarMaster closed");
    }

    private void setResultQueue() {
        IntStream.range(ZERO, SECOND).forEach(number ->
                context.getRedissonUtils().rpush(context.resultQueue(), context.resultQueue().concat(REDIS_SPLIT).concat(String.valueOf(number))));
    }

    private void clean() {
        context.getAllKey().forEach((v) -> context.getRedissonUtils().del(v));
    }
}