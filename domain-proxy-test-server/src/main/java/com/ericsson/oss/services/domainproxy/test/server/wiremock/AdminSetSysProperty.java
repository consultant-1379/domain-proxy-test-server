package com.ericsson.oss.services.domainproxy.test.server.wiremock;

import java.net.HttpURLConnection;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.tomakehurst.wiremock.admin.Router;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.common.Errors;
import com.github.tomakehurst.wiremock.extension.AdminApiExtension;
import com.github.tomakehurst.wiremock.http.QueryParameter;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AdminSetSysProperty implements AdminApiExtension {
    public static final String SYS_PROPERTY_BASE = "com.ericsson.oss.services.domainproxy.test.wiremock.";
    private static Logger logger = LoggerFactory.getLogger(AdminSetSysProperty.class);

    @Override
    public void contributeAdminApiRoutes(final Router router) {
        router.add(RequestMethod.GET, "/cbrs/sys-property/{name}", (admin, request, pathParams) -> {
            final String propertyName = pathParams.get("name");
            final QueryParameter valueParameter = request.queryParameter("value");
            if (propertyName == null || propertyName.isEmpty()) {
                return ResponseDefinition.notFound();
            }
            if (!valueParameter.isPresent()){
                return ResponseDefinition.badRequest(Errors.single(1, "Missing 'value' parameter"));
            }

            final String sysProperty = SYS_PROPERTY_BASE + propertyName;
            final String value = valueParameter.firstValue();
            System.setProperty(sysProperty, value);

            logger.info("Update System Property: {}={}", sysProperty, value);

            return ResponseDefinitionBuilder.responseDefinition()
                    .withStatus(HttpURLConnection.HTTP_OK)
                    .withHeader("Content-Type", "text/plain")
                    .withBody(sysProperty + '=' + value)
                    .build();
        });

        router.add(RequestMethod.GET, "/cbrs/sys-property", (admin, request, pathParams) -> {
            logger.info("Fetching System properties");
            final String propertiesSet = System.getProperties().stringPropertyNames().stream()
                    .filter(name -> name.startsWith(SYS_PROPERTY_BASE))
                    .map(name -> name + '=' + System.getProperty(name))
                    .collect(Collectors.joining(";\n"));

            logger.info("Listing System properties: {}", propertiesSet);
            return ResponseDefinitionBuilder.responseDefinition()
                    .withStatus(HttpURLConnection.HTTP_OK)
                    .withHeader("Content-Type", "text/plain")
                    .withBody(propertiesSet)
                    .build();
        });
    }

    @Override
    public String getName() {
        return "cbrs-set-sys-property";
    }
}
