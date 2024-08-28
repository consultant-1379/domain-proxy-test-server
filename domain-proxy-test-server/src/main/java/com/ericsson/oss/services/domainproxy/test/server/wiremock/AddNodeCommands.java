package com.ericsson.oss.services.domainproxy.test.server.wiremock;

import com.ericsson.oss.services.domainproxy.test.server.cbrs.Node;
import com.ericsson.oss.services.domainproxy.test.server.cbrs.Topology;
import com.github.tomakehurst.wiremock.admin.Router;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.extension.AdminApiExtension;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import lombok.RequiredArgsConstructor;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class AddNodeCommands implements AdminApiExtension {
    private final Topology topology;

    @Override
    public void contributeAdminApiRoutes(final Router router) {
        router.add(RequestMethod.GET, "/cbrs/add-node-commands", (admin, request, pathParams) -> {
            final List<Node> nodes = topology.getNodes();
            final String commands = nodes.stream()
                    .map(this::generateAddNodeCommands)
                    .collect(Collectors.joining("\n"));

            return ResponseDefinitionBuilder.responseDefinition()
                    .withStatus(HttpURLConnection.HTTP_OK)
                    .withHeader("Content-Type", "text/plain")
                    .withBody(commands)
                    .build();
        });
    }

    private String generateAddNodeCommands(final Node node) {
        return String.format("cmedit create NetworkElement=%1$s networkElementId=\"%1$s\",neType=\"RadioNode\",ossPrefix=\"\" " +
                "-ns=OSS_NE_DEF -v=2.0.0" +
                "\ncmedit create NetworkElement=%1$s,ComConnectivityInformation=1 ComConnectivityInformationId=\"1\"," +
                "port=\"%3$d\",transportProtocol=\"TLS\",ipAddress=\"%2$s\" -ns=COM_MED -v=1.1.0" +
                "\nsecadm credentials create --secureusername netsim --secureuserpassword netsim -n %1$s" +
                "\ncmedit set NetworkElement=%1$s,CmNodeHeartbeatSupervision=1 active=true", node.getName(), node.getAddress(), node.getPort());
    }

    @Override
    public String getName() {
        return "cbrs-add-nodes-command";
    }
}
