package org.pillar.core.enums;

/**
 * 常用变量
 * <p>
 * Author: GL
 * Date: 2022-03-26
 */
public enum CommonConstants {
    ;

    public static final String PREFIX = "pillar:default";
    public static final String MASTER_PREFIX = "master";
    public static final String SLAVE_PREFIX = "slave";

    public static final String MASTER_LOCK = "lock:consume:master";
    public static final String SLAVE_LOCK = "lock:consume:slave";

    public static final String HIGN_QUEUE = "queue:high";
    public static final String MEDIUM_QUEUE = "queue:medium";
    public static final String LOW_QUEUE = "queue:low";
    public static final String RESULT_QUEUE = "queue:result";

    public static final String EXECUTE_HASH = "hash:execute";
    public static final String HEART_HASH = "hash:heart";
    public static final String HASH_VALUE_SPLIT = "^pillar^";
    public static final String HASH_VALUE_SPLIT_ESCAPE = "\\^pillar\\^";

    public static final String LEADER_LOCK = "leader:lock";
    public static final String LEADER_NAME = "leader:name";

    public static final String REDIS_FORMAT = "%s%s";
    public static final String EMPTY_STRING = "";
    public static final String REDIS_SPLIT = ":";

    public static final long MILLISECOND = 1000;
    public static final long HEARTBEAT_INTERVAL = 30 * 1000;
    public static final long MIN_HEARTBEAT_INTERVAL = 10 * 1000;
    public static final int EXPIRATION_COUNT = 6;
    public static final int MIN_EXPIRATION_COUNT = 3;
    public static final int FIRST = 1;
    public static final int SECOND = 2;
    public static final int THIRD = 3;
    public static final int FOURTH = 4;
    public static final int FIFTH = 5;
    public static final int SIXTH = 6;
    public static final int SEVENTH = 7;
    public static final int TENTH = 10;
    public static final int ZERO = 0;
    public static final int END_INDEX = -1;

    public static final boolean FALSE = false;
    public static final boolean TRUE = true;
}
