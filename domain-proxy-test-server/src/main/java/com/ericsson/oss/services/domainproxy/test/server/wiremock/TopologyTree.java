package com.ericsson.oss.services.domainproxy.test.server.wiremock;

import com.ericsson.oss.services.domainproxy.test.server.cbrs.Node;
import com.ericsson.oss.services.domainproxy.test.server.cbrs.Topology;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.tomakehurst.wiremock.admin.Router;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.extension.AdminApiExtension;
import com.github.tomakehurst.wiremock.http.ContentTypeHeader;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.List;

@Slf4j
public class TopologyTree implements AdminApiExtension {
    private final Topology topology;
    private final Handlebars handlebars;
    private final Template template;

    public TopologyTree(final Topology topology, final Handlebars handlebars) throws IOException {
        this.topology = topology;
        this.handlebars = handlebars;

        template = handlebars.compile("/wiremock/topologyTree");
    }

    @Override
    public void contributeAdminApiRoutes(final Router router) {
        router.add(RequestMethod.GET, "/cbrs/topology-tree", (admin, request, pathParams) -> generateTopologyTree(request, topology.getNodes()));
        router.add(RequestMethod.GET, "/cbrs/topology-tree/node/{nodeName}", (admin, request, pathParams) -> {
            final String nodeName = pathParams.get("nodeName");
            return topology.getNode(nodeName)
                    .map(node -> generateTopologyTree(request, Collections.singletonList(node)))
                    .orElse(ResponseDefinition.notFound());
        });
        router.add(RequestMethod.POST, "/cbrs/topology-tree/node/{nodeName}", (admin, request, pathParams) -> {
            final String body = request.getBodyAsString();
            final String nodeName = pathParams.get("nodeName");
            if (body.contains("action=stop") || request.queryParameter("action").containsValue("stop")) {
                topology.nodeStop(nodeName);
            } else if (body.contains("action=start") || request.queryParameter("action").containsValue("start")) {
                topology.nodeStart(nodeName);
            } else if (body.contains("action=disable-scan-capability") || request.queryParameter("action").containsValue("disable-scan-capability")) {
                topology.setRcvdPowerScanCapability(nodeName, false);
            } else if (body.contains("action=enable-scan-capability") || request.queryParameter("action").containsValue("enable-scan-capability")) {
                topology.setRcvdPowerScanCapability(nodeName, true);
            } else if (body.contains("latency=")) {
                final String latencyText = body.substring(body.indexOf("latency=") + "latency=".length());
                final long newLatency = Long.parseLong(latencyText);
                topology.setNodeLatencyMillis(nodeName, newLatency);
            } else if (request.queryParameter("latency").isPresent()) {
                final String latencyText = request.queryParameter("latency").firstValue();
                final long newLatency = Long.parseLong(latencyText);
                topology.setNodeLatencyMillis(nodeName, newLatency);
            }
            return redirectToTopologyTree();
        });
    }

    private ResponseDefinition redirectToTopologyTree() {
        return ResponseDefinitionBuilder.responseDefinition()
                .withStatus(HttpURLConnection.HTTP_MOVED_TEMP)
                .withHeader("Location", "/__admin/cbrs/topology-tree")
                .build();
    }

    private ResponseDefinition generateTopologyTree(final Request request,
                                                    final List<Node> nodes) {
        try {
            final ContentTypeHeader contentTypeHeader = request.contentTypeHeader();
            if (contentTypeHeader.isPresent() && contentTypeHeader.containsValue("application/json")) {
                return ResponseDefinition.okForJson(nodes);
            } else {
                final String content = template.apply(Collections.singletonMap("nodes", nodes));
                return ResponseDefinitionBuilder.responseDefinition()
                        .withStatus(HttpURLConnection.HTTP_OK)
                        .withHeader("Content-Type", "text/html")
                        .withBody(content)
                        .build();
            }
        } catch (Exception e) {
            log.error("Failed to generate topology tree.", e);
            return ResponseDefinitionBuilder.responseDefinition().withStatus(HttpURLConnection.HTTP_INTERNAL_ERROR)
                    .withStatusMessage(e.getMessage()).build();
        }
    }

    @Override
    public String getName() {
        return "cbrs-topology-tree";
    }
}
