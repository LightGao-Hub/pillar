package org.pillar.clients.context;

import static org.pillar.core.enums.CommonConstants.EXPIRATION_COUNT;
import static org.pillar.core.enums.CommonConstants.FIFTH;
import static org.pillar.core.enums.CommonConstants.FIRST;
import static org.pillar.core.enums.CommonConstants.FOURTH;
import static org.pillar.core.enums.CommonConstants.MASTER_PREFIX;
import static org.pillar.core.enums.CommonConstants.MILLISECOND;
import static org.pillar.core.enums.CommonConstants.REDIS_SPLIT;
import static org.pillar.core.enums.CommonConstants.SECOND;
import static org.pillar.core.enums.CommonConstants.SEVENTH;
import static org.pillar.core.enums.CommonConstants.SLAVE_PREFIX;
import static org.pillar.core.enums.CommonConstants.TENTH;
import static org.pillar.core.enums.CommonConstants.THIRD;
import static org.pillar.core.enums.CommonConstants.TRUE;
import static org.pillar.core.enums.CommonConstants.ZERO;

import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.pillar.core.config.PConfig;
import org.pillar.core.config.PContext;
import org.pillar.core.config.PillarConfig;
import org.pillar.core.config.PillarContext;
import org.pillar.core.enums.QueueType;
import org.pillar.core.lambdas.ProcessLambda;
import org.pillar.master.context.Master;
import org.pillar.master.context.PillarMaster;
import org.pillar.slave.context.PillarSlave;
import org.pillar.slave.context.Slave;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

/**
 *  模拟生产环境：双master、双slave 共四个节点:
 *      先运行pillarMasterFirst函数, 此时此节点为leader节点, 并循环发送数据到随机队列, 并消费result队列
 *      再运行pillarMasterSecond函数, 非leader节点, 正常发送数据到随机队列, 并消费result队列
 *      再运行pillarSlaveFirst函数, 非leader节点, 正常从高到底消费数据, 根据数据进行睡眠后提交任务到result队列
 *      再运行pillarSlaveSecond函数, 非leader节点, 正常从高到底消费数据, 根据数据进行睡眠后提交任务到result队列
 *
 *  测试流程:
 *      程序运行100s时pillarMasterFirst节点最先宕机, 新的leader节点诞生, 130s时, leader断定pillarMasterFirst节点宕机, 执行宕机逻辑
 *      程序运行200s时pillarSlaveFirst节点宕机, 若此节点是leader节点则逻辑同上, 若非leader节点, 230s时, leader断定pillarSlaveFirst节点宕机, 执行宕机逻辑
 *      程序运行300s时, 剩余节点分别关闭, 测试结束.
 *
 *      注意: 测试结束后记得单独执行clean函数, 将测试相关队列清除.
 *
 * Author: GL
 * Date: 2022-04-07
 */
@Slf4j
public class PillarTest {
    private final AtomicBoolean stopper = new AtomicBoolean();
    private final Random rand = new Random();
    private final String prefix = "test:pillar";
    private final long heartbeatInterval = 10 * 1000;
    private final int expirationCount = 3;
    private PContext context;
    private PConfig config;
    private Master<String> pillarMaster;
    private Slave<String> pillarSlave;

    @Before
    public void setUp() throws Exception {
        final URL resource = PillarTest.class.getClassLoader().getResource("redisson.yml");
        final RedissonClient redissonClient = Redisson.create(Config.fromYAML(resource));
        config = PillarConfig.builder().prefix(prefix).redissonClient(redissonClient)
                .heartbeatInterval(heartbeatInterval).expirationCount(expirationCount).build();
        log.info(String.format("init config: %s", config));
        context = new PillarContext(config);
    }

    @After
    public void tearDown() {
        close();
    }

    @Test
    public void pillarMasterFirst() {
        pillarMaster = new PillarMaster(config);
        startMaster(MASTER_PREFIX.concat(REDIS_SPLIT).concat(String.valueOf(FIRST)));
        waiting(heartbeatInterval * TENTH); // 100s后停止
        log.info("pillarMasterFirst:getExecuteQueue: {}", pillarMaster.getExecuteQueue());
    }

    @Test
    public void pillarMasterSecond() {
        pillarMaster = new PillarMaster(config);
        startMaster(MASTER_PREFIX.concat(REDIS_SPLIT).concat(String.valueOf(SECOND)));
        waiting(heartbeatInterval * EXPIRATION_COUNT * SECOND); // 200s后停止
        log.info("pillarMasterFirst:getExecuteQueue: {}", pillarMaster.getExecuteQueue());
    }

    @Test
    public void pillarSlaveFirst() {
        pillarSlave = new PillarSlave(config);
        startSlave(SLAVE_PREFIX.concat(REDIS_SPLIT).concat(String.valueOf(FIRST)));
        waiting(heartbeatInterval * EXPIRATION_COUNT * THIRD); // 300s后停止
    }

    @Test
    public void pillarSlaveSecond() {
        pillarSlave = new PillarSlave(config);
        startSlave(SLAVE_PREFIX.concat(REDIS_SPLIT).concat(String.valueOf(SECOND)));
        waiting(heartbeatInterval * EXPIRATION_COUNT * FOURTH); // 400s后停止, 消费结束
    }

    private void startMaster(String name) {
        new Thread(this::masterSend, name).start();
        new Thread(this::masterConsume, name).start();
    }

    private void startSlave(String name) {
        new Thread(this::slaveConsume, name).start();
    }

    // master循环生产
    private void masterSend() {
        List<QueueType> allQueueType = context.getAllQueueType();
        process(() -> {
            QueueType queueType = allQueueType.get(rand.nextInt(allQueueType.size()));
            int score = rand.nextInt(FIFTH);
            String value = Thread.currentThread().getName().concat(REDIS_SPLIT).concat(uuid()).concat(String.valueOf(score * MILLISECOND));
            pillarMaster.send(queueType, score, value);
        });
    }

    // master循环消费
    private void masterConsume() {
        process(() -> {
            Optional<String> consume = pillarMaster.consume();
            waiting(heartbeatInterval); // 故意延迟master消费结果队列中的数据，为了当此节点宕机时执行队列有数据，可以完整触发leader宕机处理
            consume.ifPresent((v) -> pillarMaster.commit(v));
        });
    }

    // slave循环消费
    private void slaveConsume() {
        process(() -> {
            Optional<String> consume = pillarSlave.consume();
            waiting(heartbeatInterval); // 故意延迟slave消费队列中的数据，为了当此节点宕机时执行队列有数据，可以完整触发leader宕机处理
            consume.ifPresent((v) -> pillarSlave.commit(v.concat(REDIS_SPLIT).concat(Thread.currentThread().getName()), v));
        });
    }

    public void process(ProcessLambda lambda) {
        while (!stopper.get()) {
            lambda.process();
            waiting(heartbeatInterval);
        }
    }

    private void waiting(long interval) {
        try {
            Thread.sleep(interval);
        } catch (InterruptedException e) {
            log.error("thread sleep error!", e);
        }
    }

    private void close() {
        this.stopper.set(TRUE);
        if (Objects.nonNull(pillarMaster)) {
            pillarMaster.close();
        }
        if (Objects.nonNull(pillarSlave)) {
            pillarSlave.close();
        }
        log.info("service closed");
    }

    private String uuid() {
        return UUID.randomUUID().toString().substring(ZERO, SEVENTH);
    }

    @Test
    public void clean() {
        context.getAllKey().forEach((v) -> context.getRedissonUtils().del(v));
    }
}