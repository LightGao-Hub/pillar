package org.pillar.service.leader;

import java.io.Closeable;
import java.util.Optional;
import java.util.Set;

import org.pillar.core.inspect.Initialize;

/**
 * Author: GL
 * Date: 2022-03-26
 */
public interface LeaderService extends Initialize, Closeable {
    /**
     * 心跳接口
     */
    void heart();

    /**
     * leader抢占接口
     */
    void seize();

    /**
     * 节点监听接口
     */
    void listen();

    /**
     * 获取所有活跃节点
     */
    Set<String> getActiveNodeInfo();

    /**
     * 获取Leader信息
     */
    Optional<String> getLeaderInfo();

    /**
     * 关闭接口
     */
    void close();
}
