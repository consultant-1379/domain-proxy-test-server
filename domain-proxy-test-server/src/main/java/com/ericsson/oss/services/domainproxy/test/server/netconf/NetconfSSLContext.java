package com.ericsson.oss.services.domainproxy.test.server.netconf;

import com.ericsson.oss.mediation.netconf.config.SecurityDefinition;
import com.ericsson.oss.mediation.netconf.config.TlsConfiguration;
import com.ericsson.oss.mediation.netconf.config.TrustedCertificate;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static javax.xml.bind.DatatypeConverter.parseBase64Binary;

public class NetconfSSLContext {

    private static final Logger logger = LoggerFactory.getLogger(NetconfSSLContext.class);

    private static final Pattern KEY_PATTERN = Pattern.compile(
            "\\s?\\-{5}BEGIN\\s\\w*\\s?PRIVATE KEY\\-{5}\\s?(.+)\\s?\\-{5}END\\s\\w*\\s?PRIVATE KEY\\-{5}\\s?", Pattern.DOTALL);            
    private static final char[] TINY_PASSWORD = "not_important".toCharArray();

    static {
            Security.insertProviderAt(new BouncyCastleProvider(), 1);
    }

    protected static KeyManager[] getKeyManagers(TlsConfiguration tlsConfiguration) {
        try {
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509", "SunJSSE");
            kmf.init(getKeyStoreForKeyManager(tlsConfiguration), TINY_PASSWORD);
            return kmf.getKeyManagers();
        } catch (NoSuchAlgorithmException | UnrecoverableKeyException | KeyStoreException | NoSuchProviderException
                | IOException | CertificateException e) {
            logger.warn("I wasn't able to prepare key managers [\"SunX509\", \"SunJSSE\"] for SSLContext", e);
        }
        return null;
    }

    protected static TrustManager[] getTrustManagers(TlsConfiguration tlsConfiguration) {
        try {
            final TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
            tmf.init(getKeyStoreForTrustManager(tlsConfiguration));
            return tmf.getTrustManagers();
        } catch (NoSuchAlgorithmException | IOException | KeyStoreException | CertificateException e) {
            logger.warn("I wasn't able to prepare trusted managers [\"SunX509\"] for SSLContext", e);
        }
        return null;
    }

    protected static KeyStore getKeyStoreForTrustManager(final TlsConfiguration tlsConfiguration)
            throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
        final KeyStore keystore = KeyStore.getInstance("JKS");
        keystore.load(null);
        for (TrustedCertificate trustedCertificate : tlsConfiguration.getTrustedCertificates()) {
            try (InputStream certificate = Files.newInputStream(Paths.get(trustedCertificate.getCertificate()))) {
                keystore.setCertificateEntry(trustedCertificate.getAlias(), CertificateFactory.getInstance("X.509").generateCertificate(certificate));
            } catch (KeyStoreException | CertificateException | IOException e) {
                logger.warn("I wasn't able to get a trusted certificate {}", trustedCertificate.getCertificate(), e);
            }
        }
        return keystore;
    }

    protected static KeyStore getKeyStoreForKeyManager(TlsConfiguration tlsConfiguration) throws KeyStoreException,
            CertificateException, NoSuchAlgorithmException, IOException {
        final KeyStore keystore = KeyStore.getInstance("JKS");
        keystore.load(null);
        for (SecurityDefinition securityDefinition : tlsConfiguration.getSecurityDefinitions()) {
            try {
                final String keyFileContent = new String(Files.readAllBytes(Paths.get(securityDefinition.getKey())), StandardCharsets.UTF_8);
                final PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(
                        new PKCS8EncodedKeySpec(extractKey(keyFileContent, KEY_PATTERN)));

                final FileInputStream certificate = new FileInputStream(securityDefinition.getCertificate());
                KeyStore.PrivateKeyEntry entry = new KeyStore.PrivateKeyEntry(key, CertificateFactory
                        .getInstance("X.509")
                        .generateCertificates(certificate)
                        .toArray(new Certificate[0]));
                keystore.setEntry(securityDefinition.getAlias(), entry, new KeyStore.PasswordProtection(TINY_PASSWORD));
            } catch (InvalidKeySpecException | KeyStoreException | CertificateException | NoSuchAlgorithmException e) {
                logger.warn("I wasn't able to get key {} and certificates {}", securityDefinition.getKey(),
                        securityDefinition.getCertificate(), e);
            }
        }
        return keystore;
    }

    protected static byte[] extractKey(final String pem, final Pattern pattern) throws InvalidKeySpecException {
        Matcher m = pattern.matcher(pem);
        if (m.matches()) {
            return parseBase64Binary(m.group(1));
        }
        throw new InvalidKeySpecException("Wrong format of a private key. Check if you passed exactly the content of "
                + "a pem file generated by openssl. It should be wrapped into "
                + KEY_PATTERN.toString());
    }

    public SSLContext getSSLContext(TlsConfiguration tlsConfiguration) {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(getKeyManagers(tlsConfiguration), getTrustManagers(tlsConfiguration), SecureRandom.getInstance("SHA1PRNG"));
            return sslContext;
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException(e);
        }
    }
}
