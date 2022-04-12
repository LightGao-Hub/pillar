package org.pillar.core.enums;

import lombok.Getter;

/**
 * Author: GL
 * Date: 2022-04-01
 */
@Getter
public enum QueueType {
    HIGN(CommonConstants.HIGN_QUEUE),
    MEDIUM(CommonConstants.MEDIUM_QUEUE),
    LOW(CommonConstants.LOW_QUEUE);

    private final String queue;

    QueueType(String queue) {
        this.queue = queue;
    }
}
