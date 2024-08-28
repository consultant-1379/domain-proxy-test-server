package com.ericsson.oss.services.domainproxy.test.server;

import com.ericsson.oss.services.domainproxy.test.server.config.TestConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.log4j.helpers.Loader;
import org.apache.log4j.xml.DOMConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import static org.apache.log4j.LogManager.DEFAULT_CONFIGURATION_KEY;
import static org.apache.log4j.LogManager.DEFAULT_INIT_OVERRIDE_KEY;

public class Main {
    static final String DEFAULT_XML_CONFIGURATION_FILE = "log4j.xml";
    static {
        final String configurationOptionStr = System.getProperty(DEFAULT_CONFIGURATION_KEY, null);
        URL url = null;

        if(configurationOptionStr == null) {
            url = Loader.getResource(DEFAULT_XML_CONFIGURATION_FILE);
        } else {
            try {
                url = new URL(configurationOptionStr);
            } catch (MalformedURLException ex) {
                // so, resource is not a URL:
                // attempt to get the resource from the class path
                url = Loader.getResource(configurationOptionStr);
            }
        }

        if (url != null && url.getFile().endsWith(".xml")) {
            System.setProperty(DEFAULT_INIT_OVERRIDE_KEY, "true");
            DOMConfigurator.configureAndWatch(url.getFile());
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        final ArgumentParser argumentParser = argumentParser();
        try {
            final Namespace namespace = argumentParser.parseArgs(args);
            File configurationFile = namespace.get("config");
            if (configurationFile == null) {
                final String configFilePath = System.getProperty("com.ericsson.oss.services.domainproxy.test.server.configuration");
                if (configFilePath != null && !configFilePath.isEmpty()) {
                    configurationFile = new File(configFilePath);
                }
            }
            if (configurationFile == null) {
                logger.error("Missing configuration file.");
                System.exit(1);
            }

            final TestConfiguration testConfiguration = loadConfigurationFile(configurationFile);
            logger.debug("Using configuration. configuration={}", testConfiguration);

            final TestManager testManager = new TestManager(testConfiguration);
            Runtime.getRuntime().addShutdownHook(new Thread(testManager::stop));
            testManager.start();
        } catch (ArgumentParserException e) {
            argumentParser.handleError(e);
        } catch (IOException e) {
            logger.error("Error during execution.", e);
            System.exit(2);
        }

    }

    private static TestConfiguration loadConfigurationFile(final File configurationFile) throws IOException {
        final ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
        return objectMapper.readValue(configurationFile, TestConfiguration.class);
    }

    private static ArgumentParser argumentParser() {
        final ArgumentParser parser = ArgumentParsers.newArgumentParser("dp-server").defaultHelp(true);
        parser.addArgument("-c", "--config").type(Arguments.fileType().verifyExists());

        return parser;
    }
}
