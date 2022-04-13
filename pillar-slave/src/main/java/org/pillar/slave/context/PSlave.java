package org.pillar.slave.context;

import java.io.Closeable;
import java.util.Optional;

/**
 * Author: GL
 * Date: 2022-03-26
 */
public interface PSlave<T> extends Closeable {
    /**
     * 消费Queue中数据, 此函数将从高到低消费待执行队列, 若列表无数据则返回空Optional
     * 注意: 分布式服务需使用分布式锁
     */
    Optional<T> consume();

    /**
     * 获取正在执行的队列任务数
     *
     * @return
     */
    int getExecuteQueueSum();

    /**
     * 结束执行任务, 将任务结果value存放至result队列中, 并删除执行队列中的任务executeValue
     * executeValue的值是consume函数获取的数据
     *
     * @param value 任务结果
     * @param executeValue  删除执行中的任务
     */
    void commit(T value, T executeValue);

    /**
     * 获取当前节点信息
     */
    String getNodeInfo();

    /**
     * 关闭接口
     */
    void close();
}
