package com.ericsson.oss.services.domainproxy.test.server.cbrs;

import com.fasterxml.jackson.annotation.JsonView;
import com.github.tomakehurst.wiremock.common.Json;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@JsonView(Json.PublicView.class)
public class FrequencyRangeHz {
    private long frequencyStart;
    private long frequencyEnd;
}
