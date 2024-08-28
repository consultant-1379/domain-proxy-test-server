package com.ericsson.oss.services.domainproxy.test.server.wiremock;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericsson.oss.services.domainproxy.test.server.cbrs.Cbsd;
import com.ericsson.oss.services.domainproxy.test.server.cbrs.CbsdCpiData;
import com.ericsson.oss.services.domainproxy.test.server.cbrs.Topology;
import com.github.tomakehurst.wiremock.admin.Router;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.extension.AdminApiExtension;
import com.github.tomakehurst.wiremock.http.RequestMethod;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class Cpi implements AdminApiExtension {
    private static final String CPI_HEADER = "protectedHeader,encodedCpiSignedData,digitalSignature,cpiId,cpiName,installCertificationTime,fccId,cbsdSerialNumber,latitude,longitude,height,heightType,indoorDeployment,horizontalAccuracy,verticalAccuracy,antennaAzimuth,antennaDowntilt,antennaGain,eirpCapability,antennaBeamwidth,antennaModel\n";
    private static Logger logger = LoggerFactory.getLogger(Cpi.class);
    private final Topology topology;

    @Override
    public void contributeAdminApiRoutes(final Router router) {
        router.add(RequestMethod.GET, "/cbrs/cpi", (admin, request, pathParams) -> {
            final List<Cbsd> cbsds = topology.getCbsds();
            final String cpiData = cbsds.stream()
                    .filter(cbsd -> cbsd.getCpiData() != null)
                    .map(this::generateCbsdCpiData)
                    .collect(Collectors.joining("\n"));

            final List<Cbsd> missingCpi = cbsds.stream().filter(cbsd -> cbsd.getCpiData() == null).collect(Collectors.toList());
            if (missingCpi.size() > 0){
                logger.warn("There are {} cbsds missing cpi data: {}", missingCpi.size(), missingCpi);
            }


            return ResponseDefinitionBuilder.responseDefinition()
                    .withStatus(HttpURLConnection.HTTP_OK)
                    .withHeader("Content-Type", "text/plain")
                    .withBody(CPI_HEADER + cpiData)
                    .build();
        });
    }

    private String generateCbsdCpiData(final Cbsd cbsd) {
        final CbsdCpiData cpiData = cbsd.getCpiData();
        return String.format(
                "protectedHeader1,encodedCpiSignedData1,digitalSignature1,cpiId1,cpiName1,2017-12-08T14:08:54Z,%s,%s,10,20,6,AMSL,%s,0,0,0,0,%s,%s,0,",
                cpiData.getFccid(),cbsd.getCbsdSeria(), cpiData.isIndoorDeployment(), cpiData.getAntennaGain(), cpiData.getEirpCapability());
    }

    @Override
    public String getName() {
        return "cbrs-cpi";
    }
}
