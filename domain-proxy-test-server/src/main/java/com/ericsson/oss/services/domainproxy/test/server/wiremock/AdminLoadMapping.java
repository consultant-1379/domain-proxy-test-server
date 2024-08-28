package com.ericsson.oss.services.domainproxy.test.server.wiremock;

import com.github.tomakehurst.wiremock.admin.Router;
import com.github.tomakehurst.wiremock.common.Errors;
import com.github.tomakehurst.wiremock.extension.AdminApiExtension;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RequiredArgsConstructor
public class AdminLoadMapping implements AdminApiExtension {
    private static Logger logger = LoggerFactory.getLogger(AdminLoadMapping.class);

    @Override
    public void contributeAdminApiRoutes(final Router router) {
        router.add(RequestMethod.GET, "/cbrs/load/{name}", (admin, request, pathParams) -> {
            final String mappingName = pathParams.get("name");
            if (mappingName == null || mappingName.isEmpty()) {
                return ResponseDefinition.notFound();
            }

            String mappingFileName = null;
            if (mappingName.endsWith(".json")) {
                mappingFileName = mappingName.toLowerCase();
            } else {
                mappingFileName = mappingName.toLowerCase() + ".json";
            }

            final InputStream resourceAsStream = getClass().getResourceAsStream("/auxmappings/" + mappingFileName);
            if (resourceAsStream == null) {
                logger.warn("Could not find mapping: {}", mappingFileName);
                return ResponseDefinition.notFound();
            }

            try {
                final StubMapping stubMapping = StubMapping.buildFrom(readFile(resourceAsStream));
                final UUID id = stubMapping.getId();
                if (id != null) {
                    admin.removeStubMapping(stubMapping);
                }
                admin.addStubMapping(stubMapping);

                logger.info("Loaded new mapping: {}", mappingFileName);
                return ResponseDefinition.ok();
            } catch (IOException e) {
                logger.error("Error reading mapping file: {}", mappingFileName, e);
                return ResponseDefinition.badRequest(Errors.validation("LoadMapping", "Failed to read file: " + e.getMessage()));
            }
        });
    }

    @Override
    public String getName() {
        return "cbrs-load";
    }

    private String readFile(final InputStream inputStream) throws IOException {
        final ByteArrayOutputStream result = new ByteArrayOutputStream();
        final byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        return result.toString(StandardCharsets.UTF_8.name());
    }

}
