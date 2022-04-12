package org.pillar.core.config;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

/**
 * Author: GL
 * Date: 2022-03-27
 */
@Slf4j
public class HostnamePidTest {
    @Test
    public void hostnamePid() {
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        log.info(runtimeMXBean.getName());
    }
}
