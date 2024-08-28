package com.ericsson.oss.services.domainproxy.test.server.wiremock;

import com.ericsson.oss.services.domainproxy.test.server.cbrs.Topology;
import com.ericsson.oss.services.domainproxy.test.server.testevent.Reporter;
import com.ericsson.oss.services.domainproxy.test.wiremock.response.consistentransformer.CbsdAndGrantRegistry;
import com.github.tomakehurst.wiremock.admin.Router;
import com.github.tomakehurst.wiremock.extension.AdminApiExtension;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AdminReset implements AdminApiExtension {
    private final Topology topology;
    private final Reporter reporter;

    @Override
    public void contributeAdminApiRoutes(final Router router) {
        router.add(RequestMethod.GET, "/cbrs/reset", (admin, request, pathParams) -> {
            topology.reset();
            reporter.reset();
            CbsdAndGrantRegistry.getInstance().clear();
            return ResponseDefinition.ok();
        });
    }

    @Override
    public String getName() {
        return "cbrs-reset";
    }
}
