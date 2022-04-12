package org.pillar.core.config;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static org.pillar.core.enums.CommonConstants.HASH_VALUE_SPLIT_ESCAPE;
import static org.pillar.core.enums.CommonConstants.REDIS_SPLIT;

/**
 * Author: GL
 * Date: 2022-03-27
 */
@Slf4j
public class SplitTest {
    @Test
    public void split() {
        String hashValue = "task_second^pillar^task_first";
        log.info(String.join(REDIS_SPLIT, deletePillarSplit(hashValue)));
    }

    public List<String> deletePillarSplit(String value) {
        Objects.requireNonNull(value);
        return new ArrayList<>(Arrays.asList(value.split(HASH_VALUE_SPLIT_ESCAPE)));
    }
}
