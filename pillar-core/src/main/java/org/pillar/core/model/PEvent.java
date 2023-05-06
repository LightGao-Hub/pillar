package org.pillar.core.model;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import org.pillar.core.enums.OverTime;
import org.pillar.core.enums.QueueType;

import static org.pillar.core.enums.CommonConstants.*;

/**
 * Author: GL
 * Date: 2022-10-31
 */
@Data
@Builder
public class PEvent<E> implements Event<E> {
    @NonNull
    private E value;
    @Builder.Default
    private double score = DOUBLE_FIRST;
    @Builder.Default
    private QueueType queue = QueueType.MEDIUM;
    @Builder.Default
    private long creatTime = EXPIRATION_COUNT;
    // 单位秒
    // 放入待执行队列时间
    private long queueTime;
    // 放入执行队列时间
    private long execTime;
    // 放入结果队列时间
    private long resQueueTime;
    // 放入结果队列执行时间
    private long resExecTime;
    // 执行队列超时时间
    @Builder.Default
    private long execOverTime = OverTime.SHOURS.getOverTime();
    // 结果执行队列超时时间
    @Builder.Default
    private long resExecOverTime = OverTime.OHOURS.getOverTime();
}
