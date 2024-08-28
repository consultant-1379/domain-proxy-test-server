package com.ericsson.oss.services.domainproxy.test.server.wiremock;

import com.ericsson.oss.services.domainproxy.test.server.testevent.reports.InMemoryReport;
import com.github.tomakehurst.wiremock.admin.Router;
import com.github.tomakehurst.wiremock.extension.AdminApiExtension;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AdminReport implements AdminApiExtension {
    private final InMemoryReport report;

    @Override
    public void contributeAdminApiRoutes(final Router router) {
        router.add(RequestMethod.GET, "/cbrs/report", (admin, request, pathParams) -> ResponseDefinition.okForJson(report));
    }

    @Override
    public String getName() {
        return "cbrs-report";
    }
}
