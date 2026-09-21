package com.slest.osrsdata.pipeline;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HexFormat;

public final class PluginManifest
{
    static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    static final String RUNELITE_CERTIFICATE = "runelite-externalplugins.crt";

    private final JsonObject json;

    private PluginManifest(JsonObject json)
    {
        this.json = json;
    }

    public static PluginManifest parse(byte[] framed, Certificate certificate)
    {
        Framed parts = unframe(framed);
        verify(parts, certificate);
        return new PluginManifest(Json.object(new String(parts.signed(), StandardCharsets.UTF_8),
                "plugin manifest body"));
    }

    record Framed(byte[] signature, byte[] signed)
    {
    }

    // framing: a big-endian int32 signature length, the signature, then the signed JSON
    static Framed unframe(byte[] framed)
    {
        if (framed.length < 4)
        {
            throw new PipelineException("plugin manifest is " + framed.length + " bytes; too short to be signed");
        }
        ByteBuffer buffer = ByteBuffer.wrap(framed);
        int signatureLength = buffer.getInt();
        if (signatureLength <= 0 || signatureLength > buffer.remaining())
        {
            throw new PipelineException("plugin manifest has signature length " + signatureLength
                    + " but only " + buffer.remaining() + " bytes follow; not a signed manifest");
        }
        byte[] signature = new byte[signatureLength];
        buffer.get(signature);
        byte[] signed = new byte[buffer.remaining()];
        buffer.get(signed);
        return new Framed(signature, signed);
    }

    static void verify(Framed parts, Certificate certificate)
    {
        boolean valid;
        try
        {
            Signature verifier = Signature.getInstance(SIGNATURE_ALGORITHM);
            verifier.initVerify(certificate);
            verifier.update(parts.signed());
            valid = verifier.verify(parts.signature());
        }
        catch (GeneralSecurityException e)
        {
            valid = false;
        }
        if (!valid)
        {
            throw new PipelineException("plugin manifest signature does not verify against the plugin-hub "
                    + "certificate with SHA-256 fingerprint " + fingerprint(certificate) + ". RuneLite may have "
                    + "rotated its certificate: compare net/runelite/client/externalplugins/externalplugins.crt "
                    + "in the current RuneLite client jar with pipeline/src/main/resources/com/slest/osrsdata/"
                    + "pipeline/" + RUNELITE_CERTIFICATE + " before trusting anything this manifest says");
        }
    }

    public static X509Certificate runeliteCertificate()
    {
        try (InputStream in = PluginManifest.class.getResourceAsStream(RUNELITE_CERTIFICATE))
        {
            if (in == null)
            {
                throw new PipelineException(RUNELITE_CERTIFICATE + " is missing from the pipeline's resources");
            }
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(in);
        }
        catch (IOException | CertificateException e)
        {
            throw new PipelineException("cannot load " + RUNELITE_CERTIFICATE + ": " + e.getMessage(), e);
        }
    }

    public static String fingerprint(Certificate certificate)
    {
        try
        {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
            return HexFormat.ofDelimiter(":").withUpperCase().formatHex(digest);
        }
        catch (GeneralSecurityException e)
        {
            return "(unavailable: " + e.getMessage() + ")";
        }
    }

    public PluginRelease release(String internalName)
    {
        JsonObject jar = entry("jars", internalName);
        JsonObject display = entry("display", internalName);
        String jarHash = Json.string(jar, "jarHash");
        String version = Json.string(display, "version");
        JsonElement size = jar.get("jarSize");
        if (jarHash == null || jarHash.isEmpty() || size == null || !size.isJsonPrimitive())
        {
            throw new PipelineException("plugin manifest entry for " + internalName + " has no jarHash/jarSize");
        }
        if (version == null || version.isEmpty())
        {
            throw new PipelineException("plugin manifest display entry for " + internalName + " has no version");
        }
        return new PluginRelease(internalName, version, jarHash, size.getAsLong());
    }

    private JsonObject entry(String list, String internalName)
    {
        JsonElement entries = json.get(list);
        if (entries == null || !entries.isJsonArray())
        {
            throw new PipelineException("plugin manifest has no \"" + list + "\" list");
        }
        for (JsonElement element : (JsonArray) entries)
        {
            if (element.isJsonObject() && internalName.equals(Json.string(element.getAsJsonObject(), "internalName")))
            {
                return element.getAsJsonObject();
            }
        }
        throw new PipelineException("plugin manifest has no " + internalName + " entry in \"" + list
                + "\" - has the plugin been renamed or pulled from the hub?");
    }

    public record PluginRelease(String internalName, String version, String jarHash, long jarSize)
    {
    }
}
