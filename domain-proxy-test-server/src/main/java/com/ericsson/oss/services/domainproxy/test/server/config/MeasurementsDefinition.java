package com.ericsson.oss.services.domainproxy.test.server.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeasurementsDefinition {
    private int durationSeconds = 10;
    private int bandwidthMhz = 10;
    private int minPowerDbm = -100;
    private int maxPowerDbm = -25;
}
