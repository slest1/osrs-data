package com.slest.osrsdata.pipeline;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginHubTest
{
    static final String RUNELITE_FINGERPRINT =
            "1A:A5:EF:F5:2A:14:7C:8D:96:C9:66:73:0F:B0:7C:9E:FF:38:BA:31:C5:D1:AB:DD:AF:8A:50:26:F3:F3:9D:A3";

    @TempDir
    Path out;

    @Test
    void jarHashIsUnpaddedBase64UrlSha256()
    {
        assertEquals("47DEQpj8HBSa-_TImW-5JCeuQeRkm5NMpJWZG3hSuFU", Hashes.jarHash(new byte[0]));
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                Hashes.sha256Hex(new byte[0]));
    }

    @Test
    void manifestIsUnframedByItsLengthPrefixNotByScanningForABrace()
    {
        String json = HubFixtures.manifestJson("HASH", 123);
        PluginManifest.Framed parts = PluginManifest.unframe(HubFixtures.braceFramed(json));
        assertEquals(512, parts.signature().length);
        assertEquals(json, new String(parts.signed(), StandardCharsets.UTF_8));
    }

    @Test
    void aCorrectlySignedManifestVerifiesAndParses()
    {
        PluginManifest manifest = PluginManifest.parse(HubFixtures.framed(HubFixtures.manifestJson("HASH", 123)),
                HubFixtures.CERT);

        PluginManifest.PluginRelease release = manifest.release("quest-helper");
        assertEquals("quest-helper", release.internalName());
        assertEquals("4.17.1", release.version());
        assertEquals("HASH", release.jarHash());
        assertEquals(123, release.jarSize());
    }

    @Test
    void aLengthPrefixLongerThanTheBodyIsRejected()
    {
        byte[] json = "{}".getBytes(StandardCharsets.UTF_8);
        byte[] framed = ByteBuffer.allocate(4 + json.length).putInt(4096).put(json).array();

        PipelineException e = assertThrows(PipelineException.class,
                () -> PluginManifest.parse(framed, HubFixtures.CERT));
        assertTrue(e.getMessage().contains("signature length"), e.getMessage());
    }

    @Test
    void unframedJsonIsRejected()
    {
        byte[] bare = HubFixtures.manifestJson("HASH", 1).getBytes(StandardCharsets.UTF_8);
        assertThrows(PipelineException.class, () -> PluginManifest.parse(bare, HubFixtures.CERT));
    }

    @Test
    void aManifestWithoutQuestHelperFailsByName()
    {
        String json = HubFixtures.manifestJson("HASH", 1).replace("quest-helper", "quest-helper-fork");
        PluginManifest manifest = PluginManifest.parse(HubFixtures.framed(json), HubFixtures.CERT);

        PipelineException e = assertThrows(PipelineException.class, () -> manifest.release("quest-helper"));
        assertTrue(e.getMessage().contains("quest-helper"), e.getMessage());
    }

    @Test
    void aTamperedManifestFailsVerification()
    {
        byte[] framed = HubFixtures.framed(HubFixtures.manifestJson("HASH", 123));
        byte[] tampered = new String(framed, StandardCharsets.ISO_8859_1).replace("4.17.1", "4.17.2")
                .getBytes(StandardCharsets.ISO_8859_1);

        PipelineException e = assertThrows(PipelineException.class,
                () -> PluginManifest.parse(tampered, HubFixtures.CERT));
        assertTrue(e.getMessage().contains("signature"), e.getMessage());
        assertTrue(e.getMessage().contains(PluginManifest.fingerprint(HubFixtures.CERT)), e.getMessage());
    }

    @Test
    void aManifestSignedByAnotherKeyFailsAndSaysTheCertMayHaveRotated()
    {
        byte[] framed = HubFixtures.framed(HubFixtures.manifestJson("HASH", 123));

        PipelineException e = assertThrows(PipelineException.class,
                () -> PluginManifest.parse(framed, HubFixtures.OTHER_CERT));
        assertTrue(e.getMessage().contains("rotated"), e.getMessage());
        assertTrue(e.getMessage().contains(PluginManifest.fingerprint(HubFixtures.OTHER_CERT)), e.getMessage());
    }

    @Test
    void theVendoredCertificateIsRuneLitesPluginHubCertificate()
    {
        assertEquals(RUNELITE_FINGERPRINT, PluginManifest.fingerprint(PluginManifest.runeliteCertificate()));
    }

    @Test
    void theHubRefusesAnUnverifiableManifestAndWritesNothing() throws Exception
    {
        PipelineException e = assertThrows(PipelineException.class,
                () -> new PluginHub(HubFixtures.hub(), HubFixtures.OTHER_CERT).fetch(out));
        assertTrue(e.getMessage().contains("signature"), e.getMessage());
        assertFalse(Files.exists(out.resolve(QuestHelperJar.JAR_FILE)));
        assertFalse(Files.exists(out.resolve(QuestHelperJar.METADATA_FILE)));
    }

    @Test
    void theRuneLiteVersionComesFromTheClientArtifactNotTheInjectedClient() throws Exception
    {
        assertEquals("1.12.39", new PluginHub(HubFixtures.hub(), HubFixtures.CERT).runeliteVersion());
    }

    @Test
    void resolveReadsVersionHashAndSizeWithoutDownloadingTheJar() throws Exception
    {
        FakeHttp http = HubFixtures.hub();
        QuestHelperJar jar = new PluginHub(http, HubFixtures.CERT).resolve();

        assertEquals(new QuestHelperJar("1.12.39", "4.17.1", HubFixtures.hashOf(HubFixtures.JAR),
                HubFixtures.JAR.length), jar);
        assertFalse(http.requested.stream().anyMatch(url -> url.endsWith(".jar")), http.requested.toString());
    }

    @Test
    void fetchWritesTheJarAndItsMetadata() throws Exception
    {
        QuestHelperJar jar = new PluginHub(HubFixtures.hub(), HubFixtures.CERT).fetch(out);

        assertArrayEquals(HubFixtures.JAR, Files.readAllBytes(out.resolve(QuestHelperJar.JAR_FILE)));
        assertEquals(jar, QuestHelperJar.read(out.resolve(QuestHelperJar.METADATA_FILE)));
        String metadata = Files.readString(out.resolve(QuestHelperJar.METADATA_FILE));
        assertTrue(metadata.indexOf("runeliteVersion") < metadata.indexOf("questHelperVersion")
                && metadata.indexOf("questHelperVersion") < metadata.indexOf("jarHash")
                && metadata.indexOf("jarHash") < metadata.indexOf("jarSize"), metadata);
    }

    @Test
    void aJarWhoseHashDiffersFromTheManifestFailsAndWritesNothing() throws Exception
    {
        byte[] tampered = HubFixtures.JAR.clone();
        tampered[0] ^= 1;
        FakeHttp http = HubFixtures.hub(HubFixtures.hashOf(HubFixtures.JAR), HubFixtures.JAR.length, tampered);

        PipelineException e = assertThrows(PipelineException.class, () -> new PluginHub(http, HubFixtures.CERT).fetch(out));
        assertTrue(e.getMessage().contains("hash"), e.getMessage());
        assertFalse(Files.exists(out.resolve(QuestHelperJar.JAR_FILE)));
        assertFalse(Files.exists(out.resolve(QuestHelperJar.METADATA_FILE)));
    }

    @Test
    void aJarWhoseSizeDiffersFromTheManifestFailsAndWritesNothing() throws Exception
    {
        FakeHttp http = HubFixtures.hub(HubFixtures.hashOf(HubFixtures.JAR), HubFixtures.JAR.length + 1,
                HubFixtures.JAR);

        PipelineException e = assertThrows(PipelineException.class, () -> new PluginHub(http, HubFixtures.CERT).fetch(out));
        assertTrue(e.getMessage().contains("size"), e.getMessage());
        assertFalse(Files.exists(out.resolve(QuestHelperJar.JAR_FILE)));
    }
}
