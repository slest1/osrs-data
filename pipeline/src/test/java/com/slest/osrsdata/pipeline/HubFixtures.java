package com.slest.osrsdata.pipeline;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

final class HubFixtures
{
    static final String RUNELITE = "1.12.39";
    static final String QH_VERSION = "4.17.1";
    static final byte[] JAR = "not really a jar, but bytes all the same".getBytes(StandardCharsets.UTF_8);

    static final PrivateKey KEY = privateKey("test-hub.key");
    static final X509Certificate CERT = certificate("test-hub.crt");
    static final X509Certificate OTHER_CERT = certificate("other-hub.crt");

    static byte[] bootstrap(String version)
    {
        // injected-client is listed first, so a loose match on "client-" would pick it
        String json = "{\"version\":\"" + version + "\",\"artifacts\":["
                + "{\"name\":\"injected-client-" + version + ".jar\",\"path\":\"x\",\"size\":1,\"hash\":\"h\"},"
                + "{\"name\":\"guava-33.jar\",\"path\":\"x\",\"size\":1,\"hash\":\"h\"},"
                + "{\"name\":\"client-" + version + ".jar\",\"path\":\"x\",\"size\":1,\"hash\":\"h\"}]}";
        return json.getBytes(StandardCharsets.UTF_8);
    }

    static String manifestJson(String jarHash, long jarSize)
    {
        return "{\"display\":["
                + "{\"internalName\":\"0-xp-drops\",\"displayName\":\"0 XP Drops\",\"version\":\"1.0\"},"
                + "{\"internalName\":\"quest-helper\",\"displayName\":\"Quest Helper\",\"version\":\"" + QH_VERSION
                + "\",\"author\":\"Zoinkwiz\"}],"
                + "\"jars\":["
                + "{\"internalName\":\"0-xp-drops\",\"displayName\":\"0 XP Drops\",\"jarHash\":\"abc\",\"jarSize\":10},"
                + "{\"internalName\":\"quest-helper\",\"displayName\":\"Quest Helper\",\"jarHash\":\"" + jarHash
                + "\",\"jarSize\":" + jarSize + "}]}";
    }

    static byte[] framed(String json)
    {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        try
        {
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(KEY);
            signer.update(body);
            return frame(signer.sign(), body);
        }
        catch (GeneralSecurityException e)
        {
            throw new IllegalStateException(e);
        }
    }

    static byte[] braceFramed(String json)
    {
        byte[] signature = new byte[512];
        Arrays.fill(signature, (byte) '{');
        return frame(signature, json.getBytes(StandardCharsets.UTF_8));
    }

    static byte[] frame(byte[] signature, byte[] body)
    {
        return ByteBuffer.allocate(4 + signature.length + body.length)
                .putInt(signature.length).put(signature).put(body).array();
    }

    private static byte[] resource(String name)
    {
        try (InputStream in = HubFixtures.class.getResourceAsStream(name))
        {
            if (in == null)
            {
                throw new IllegalStateException("missing test resource " + name);
            }
            return in.readAllBytes();
        }
        catch (IOException e)
        {
            throw new UncheckedIOException(e);
        }
    }

    private static X509Certificate certificate(String name)
    {
        try
        {
            return (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new java.io.ByteArrayInputStream(resource(name)));
        }
        catch (GeneralSecurityException e)
        {
            throw new IllegalStateException(e);
        }
    }

    private static PrivateKey privateKey(String name)
    {
        String pem = new String(resource(name), StandardCharsets.US_ASCII)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        try
        {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
        }
        catch (GeneralSecurityException e)
        {
            throw new IllegalStateException(e);
        }
    }

    static String hashOf(byte[] bytes) throws Exception
    {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    static FakeHttp hub() throws Exception
    {
        String hash = hashOf(JAR);
        return hub(hash, JAR.length, JAR);
    }

    static FakeHttp hub(String jarHash, long jarSize, byte[] jar)
    {
        return new FakeHttp()
                .serve(PluginHub.BOOTSTRAP_URL, bootstrap(RUNELITE))
                .serve(String.format(PluginHub.MANIFEST_URL, RUNELITE), framed(manifestJson(jarHash, jarSize)))
                .serve(String.format(PluginHub.JAR_URL, "quest-helper", jarHash), jar);
    }

    private HubFixtures()
    {
    }
}
