package org.pillar.core.config;

import static org.pillar.core.enums.CommonConstants.EXPIRATION_COUNT;
import static org.pillar.core.enums.CommonConstants.HEARTBEAT_INTERVAL;
import static org.pillar.core.enums.CommonConstants.MIN_EXPIRATION_COUNT;
import static org.pillar.core.enums.CommonConstants.MIN_HEARTBEAT_INTERVAL;
import static org.pillar.core.enums.CommonConstants.PREFIX;

import lombok.Builder;
import lombok.Data;;
import lombok.NonNull;
import org.redisson.api.RedissonClient;

/**
 * Author: GL
 * Date: 2022-03-26
 */
@Data
@Builder
public class PillarConfig implements PConfig {
    @Builder.Default
    private String prefix = PREFIX;
    @NonNull
    private RedissonClient redissonClient;
    @Builder.Default
    private long heartbeatInterval = HEARTBEAT_INTERVAL;
    @Builder.Default
    private int expirationCount = EXPIRATION_COUNT;

    public int getExpirationCount() {
        return Math.max(expirationCount, MIN_EXPIRATION_COUNT);
    }

    public long getHeartbeatInterval() {
        return Math.max(heartbeatInterval, MIN_HEARTBEAT_INTERVAL);
    }
}

