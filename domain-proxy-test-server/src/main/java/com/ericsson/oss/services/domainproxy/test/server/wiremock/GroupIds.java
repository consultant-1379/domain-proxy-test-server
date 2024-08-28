package com.ericsson.oss.services.domainproxy.test.server.wiremock;

import java.net.HttpURLConnection;
import java.util.stream.Collectors;

import com.ericsson.oss.services.domainproxy.test.server.cbrs.Topology;
import com.github.tomakehurst.wiremock.admin.Router;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.extension.AdminApiExtension;
import com.github.tomakehurst.wiremock.http.RequestMethod;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class GroupIds implements AdminApiExtension {
    private final Topology topology;

    @Override
    public void contributeAdminApiRoutes(final Router router) {
        router.add(RequestMethod.GET, "/cbrs/group/ids", (admin, request, pathParams) -> {
            final String idsText = topology.getGroupIds().stream().map(id -> "\"" + id + "\"").collect(Collectors.joining("\n"));

            return ResponseDefinitionBuilder.responseDefinition()
                    .withStatus(HttpURLConnection.HTTP_OK)
                    .withHeader("Content-Type", "text/plain")
                    .withBody(idsText)
                    .build();
        });
    }

    @Override
    public String getName() {
        return "cbrs-groupids";
    }
}
