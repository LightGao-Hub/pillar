package org.pillar.core.enums;

import lombok.Getter;

/**
 * Author: GL
 * Date: 2022-04-01
 */
@Getter
public enum OverTime {
    // 单位秒
    OHOURS(CommonConstants.OHOURS),
    THOURS(CommonConstants.THOURS),
    FHOURS(CommonConstants.FHOURS),
    SHOURS(CommonConstants.SHOURS),
    EHOURS(CommonConstants.EHOURS),
    HALFDAY(CommonConstants.HALFDAY),
    DAYDAY(CommonConstants.DAYDAY);

    private final long overTime;

    OverTime(long overTime) {
        this.overTime = overTime;
    }
}
