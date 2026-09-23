package com.slest.osrsdata.pipeline;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.slest.questdata.QuestData;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BundlerTest
{
    @TempDir
    Path tmp;

    private static List<String> keys(JsonObject object)
    {
        return new ArrayList<>(object.keySet());
    }

    private static JsonObject read(Path file) throws IOException
    {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void anEntryKeepsExactlyTheBundleFieldsInOrder()
    {
        JsonObject entry = BundleTrimmer.entry(ExportFixtures.quest("DORICS_QUEST"));

        assertEquals(List.of("questKey", "name", "questId", "type", "difficulty", "rewards", "itemRequirements",
                "itemRecommended", "generalRequirements", "combatRequirements", "notes", "panels", "steps"),
                keys(entry));
    }

    @Test
    void aStepKeepsExactlyTheBundleFields()
    {
        JsonObject step = BundleTrimmer.entry(ExportFixtures.quest("DORICS_QUEST"))
                .getAsJsonArray("steps").get(0).getAsJsonObject();

        assertEquals(List.of("id", "type", "text", "worldPoint", "requirements", "recommended", "teleports"),
                keys(step));
        assertEquals(List.of("x", "y", "plane"), keys(step.getAsJsonObject("worldPoint")));
    }

    @Test
    void panelsKeepExactlyTheBundleFieldsRecursively()
    {
        JsonObject panel = BundleTrimmer.entry(ExportFixtures.quest("DORICS_QUEST"))
                .getAsJsonArray("panels").get(0).getAsJsonObject();
        List<String> kept = List.of("header", "id", "stepIds", "requirements", "recommended", "children");

        assertEquals(kept, keys(panel));
        JsonObject child = panel.getAsJsonArray("children").get(0).getAsJsonObject();
        assertEquals(kept, keys(child));
        assertEquals("Starting off child", child.get("header").getAsString());
    }

    @Test
    void requirementsAreKeptWholeWithNumbersAsWritten() throws Exception
    {
        Path dir = tmp.resolve("export");
        ExportFixtures.write(dir, "q.json", ExportFixtures.quest("DORICS_QUEST"));
        Path out = tmp.resolve("out");
        Bundler.bundle(ExportFixtures.request(dir, null), out);

        String bundle = Files.readString(out.resolve("quests-normal.json"), StandardCharsets.UTF_8);
        assertTrue(bundle.contains("{\"type\":\"ItemRequirement\",\"displayText\":\"6 x Clay\",\"data\":{\"id\":434,"
                + "\"quantity\":6,\"ratio\":1.5,\"nested\":{\"skill\":\"MINING\",\"level\":10},\"alternates\":[435,436]}}"),
                bundle);
    }

    @Test
    void droppedFieldsAppearNowhereInTheBundle() throws Exception
    {
        Path out = tmp.resolve("out");
        Bundler.bundle(ExportFixtures.request(ExportFixtures.exportDir(tmp, "normal"), null), out);

        String bundle = Files.readString(out.resolve("quests-normal.json"), StandardCharsets.UTF_8);
        for (String dropped : List.of("progressVarbitId", "progressVarpId", "completeValue", "stateSkill",
                "stageSteps", "overlayText", "targets", "lockable", "lockingCondition", "substeps", "conditional",
                "dialogChoices", "widgetChoices", "extra", "lockingStepId", "vars", "schemaVersion"))
        {
            assertFalse(bundle.contains("\"" + dropped + "\""), dropped + " survived trimming");
        }
    }

    @Test
    void nullStepsAreSkipped()
    {
        QuestData quest = ExportFixtures.quest("DORICS_QUEST");
        quest.steps.add(1, null);
        quest.steps.add(null);

        JsonArray steps = BundleTrimmer.entry(quest).getAsJsonArray("steps");
        assertEquals(2, steps.size());
        for (JsonElement step : steps)
        {
            assertTrue(step.isJsonObject());
        }
    }

    @Test
    void theBundleHasTheV1HeaderAndEntriesSortedByQuestKey() throws Exception
    {
        Path out = tmp.resolve("out");
        Bundler.bundle(ExportFixtures.request(ExportFixtures.exportDir(tmp, "normal"),
                ExportFixtures.exportDir(tmp, "ironman")), out);

        JsonObject bundle = read(out.resolve("quests-ironman.json"));
        assertEquals(List.of("format", "formatVersion", "accountType", "skillLevel", "source", "attribution",
                "entries"), keys(bundle));
        assertEquals("osrs-questdata-bundle", bundle.get("format").getAsString());
        assertEquals(1, bundle.get("formatVersion").getAsInt());
        assertEquals("IRONMAN", bundle.get("accountType").getAsString());
        assertEquals(1, bundle.get("skillLevel").getAsInt());

        JsonObject source = bundle.getAsJsonObject("source");
        assertEquals(List.of("runeliteVersion", "questHelperVersion", "questHelperJarHash", "exporterSchemaVersion",
                "upstreamCommit"), keys(source));
        assertEquals("4.17.1", source.get("questHelperVersion").getAsString());
        assertEquals(3, source.get("exporterSchemaVersion").getAsInt());
        assertEquals(ExportFixtures.UPSTREAM, source.get("upstreamCommit").getAsString());

        JsonObject attribution = bundle.getAsJsonObject("attribution");
        assertEquals("Quest data from Quest Helper by Zoinkwiz and contributors, BSD 2-Clause License",
                attribution.get("notice").getAsString());
        assertTrue(attribution.get("licenseText").getAsString().startsWith("BSD 2-Clause License"));

        List<String> order = new ArrayList<>();
        bundle.getAsJsonArray("entries").forEach(e -> order.add(e.getAsJsonObject().get("questKey").getAsString()));
        assertEquals(List.of("ALPHA_QUEST", "MIKE_QUEST", "ZULU_QUEST"), order);
    }

    @Test
    void theBundleIsCompactUtf8WithoutHtmlEscaping() throws Exception
    {
        Path out = tmp.resolve("out");
        Bundler.bundle(ExportFixtures.request(ExportFixtures.exportDir(tmp, "normal"), null), out);

        String bundle = Files.readString(out.resolve("quests-normal.json"), StandardCharsets.UTF_8);
        assertFalse(bundle.contains("\n"));
        assertTrue(bundle.contains("ALPHA QUEST's <quest>"), "names are written as they read");
    }

    @Test
    void twoRunsAreByteIdentical() throws Exception
    {
        Bundler.Request request = ExportFixtures.request(ExportFixtures.exportDir(tmp, "normal"),
                ExportFixtures.exportDir(tmp, "ironman"));
        Path first = tmp.resolve("first");
        Path second = tmp.resolve("second");
        Bundler.bundle(request, first);
        Bundler.bundle(request, second);

        List<String> names = List.of("quests-normal.json", "quests-normal.json.gz", "quests-ironman.json",
                "quests-ironman.json.gz", "manifest.json");
        for (String name : names)
        {
            assertArrayEquals(Files.readAllBytes(first.resolve(name)), Files.readAllBytes(second.resolve(name)), name);
        }
        try (var listing = Files.list(first))
        {
            assertEquals(names.size(), listing.count(), "no other files");
        }
    }

    @Test
    void theGzipHasNoTimestampAndHoldsTheRawBundle() throws Exception
    {
        Path out = tmp.resolve("out");
        Bundler.bundle(ExportFixtures.request(ExportFixtures.exportDir(tmp, "normal"), null), out);

        byte[] gz = Files.readAllBytes(out.resolve("quests-normal.json.gz"));
        assertEquals((byte) 0x1f, gz[0]);
        assertEquals((byte) 0x8b, gz[1]);
        assertArrayEquals(new byte[4], new byte[]{gz[4], gz[5], gz[6], gz[7]}, "mtime must be 0");
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gz)))
        {
            assertArrayEquals(Files.readAllBytes(out.resolve("quests-normal.json")), in.readAllBytes());
        }
    }

    @Test
    void theManifestListsOnlyThePublishedGzipFilesWithTheirUncompressedIdentity() throws Exception
    {
        Path out = tmp.resolve("out");
        List<Bundler.FileEntry> files = Bundler.bundle(ExportFixtures.request(ExportFixtures.exportDir(tmp, "normal"),
                ExportFixtures.exportDir(tmp, "ironman")), out);

        JsonObject manifest = read(out.resolve(Bundler.MANIFEST_FILE));
        assertEquals(List.of("format", "formatVersion", "source", "files"), keys(manifest));
        assertEquals(read(out.resolve("quests-normal.json")).get("source"), manifest.get("source"));
        JsonArray listed = manifest.getAsJsonArray("files");
        assertEquals(2, files.size());
        List<String> names = new ArrayList<>();
        for (JsonElement element : listed)
        {
            JsonObject file = element.getAsJsonObject();
            String name = file.get("name").getAsString();
            names.add(name);
            assertEquals(List.of("name", "accountType", "bytes", "sha256", "uncompressedBytes",
                    "uncompressedSha256", "entries"), keys(file));
            byte[] gz = Files.readAllBytes(out.resolve(name));
            assertEquals(gz.length, file.get("bytes").getAsLong());
            assertEquals(Hashes.sha256Hex(gz), file.get("sha256").getAsString());
            byte[] raw;
            try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gz)))
            {
                raw = in.readAllBytes();
            }
            assertEquals(raw.length, file.get("uncompressedBytes").getAsLong());
            assertEquals(Hashes.sha256Hex(raw), file.get("uncompressedSha256").getAsString());
            assertArrayEquals(Files.readAllBytes(out.resolve(name.replace(".gz", ""))), raw,
                    "the raw file written locally is the one inside the gzip");
            assertEquals(3, file.get("entries").getAsInt());
        }
        assertEquals(List.of("quests-ironman.json.gz", "quests-normal.json.gz"), names);
    }

    @Test
    void anEntryWithNoStepsFailsUnlessAllowlisted() throws Exception
    {
        Path dir = ExportFixtures.exportDir(tmp, "normal");
        QuestData empty = ExportFixtures.quest("EMPTY_QUEST");
        empty.steps.clear();
        empty.steps.add(null); // a null step is not a step
        ExportFixtures.write(dir, "empty.json", empty);
        Path out = tmp.resolve("out");

        PipelineException e = assertThrows(PipelineException.class,
                () -> Bundler.bundle(ExportFixtures.request(dir, null), out));
        assertTrue(e.getMessage().contains("EMPTY_QUEST"), e.getMessage());
        assertFalse(Files.exists(out.resolve("quests-normal.json")), "nothing is written when a guard fires");

        Bundler.Request base = ExportFixtures.request(dir, null);
        Bundler.bundle(new Bundler.Request(base.inputs(), base.questHelper(), base.upstreamCommit(), 1, null,
                Set.of("EMPTY_QUEST")), out);
        assertTrue(Files.exists(out.resolve("quests-normal.json")));
    }

    @Test
    void anEntryWithNoPanelsFailsUnlessAllowlisted() throws Exception
    {
        Path dir = ExportFixtures.exportDir(tmp, "normal");
        QuestData bare = ExportFixtures.quest("PANELLESS_QUEST");
        bare.panels.clear();
        ExportFixtures.write(dir, "bare.json", bare);

        PipelineException e = assertThrows(PipelineException.class,
                () -> Bundler.bundle(ExportFixtures.request(dir, null), tmp.resolve("out")));
        assertTrue(e.getMessage().contains("PANELLESS_QUEST"), e.getMessage());
    }

    @Test
    void theCommittedAllowlistLoads()
    {
        assertEquals(Set.of(), Bundler.defaultAllowlist());
    }

    @Test
    void fewerEntriesThanThePreviousBundleFails() throws Exception
    {
        Bundler.Request base = ExportFixtures.request(ExportFixtures.exportDir(tmp, "normal"),
                ExportFixtures.exportDir(tmp, "ironman"));
        Path previous = tmp.resolve("previous");
        Bundler.bundle(base, previous);

        Bundler.Request same = new Bundler.Request(base.inputs(), base.questHelper(), base.upstreamCommit(), 1,
                previous.resolve(Bundler.MANIFEST_FILE), Set.of());
        Bundler.bundle(same, tmp.resolve("same"));

        Files.delete(tmp.resolve("ironman").resolve("b.json"));
        PipelineException e = assertThrows(PipelineException.class,
                () -> Bundler.bundle(same, tmp.resolve("shrunk")));
        assertTrue(e.getMessage().contains("IRONMAN") && e.getMessage().contains("2")
                && e.getMessage().contains("3"), e.getMessage());
    }

    @Test
    void anUnreadablePreviousManifestFails() throws Exception
    {
        Path bogus = tmp.resolve("bogus.json");
        Files.writeString(bogus, "not json");
        Bundler.Request base = ExportFixtures.request(ExportFixtures.exportDir(tmp, "normal"), null);

        assertThrows(PipelineException.class, () -> Bundler.bundle(new Bundler.Request(base.inputs(),
                base.questHelper(), base.upstreamCommit(), 1, bogus, Set.of()), tmp.resolve("out")));
    }

    @Test
    void missingSourceMetadataFails() throws Exception
    {
        Path dir = ExportFixtures.exportDir(tmp, "normal");
        Bundler.Request base = ExportFixtures.request(dir, null);
        QuestHelperJar noVersion = new QuestHelperJar("1.12.39", null, "hash", 1);

        PipelineException e = assertThrows(PipelineException.class, () -> Bundler.bundle(new Bundler.Request(
                base.inputs(), noVersion, base.upstreamCommit(), 1, null, Set.of()), tmp.resolve("a")));
        assertTrue(e.getMessage().contains("questHelperVersion"), e.getMessage());

        e = assertThrows(PipelineException.class, () -> Bundler.bundle(new Bundler.Request(
                base.inputs(), base.questHelper(), "", 1, null, Set.of()), tmp.resolve("b")));
        assertTrue(e.getMessage().contains("upstreamCommit"), e.getMessage());
    }

    @Test
    void anExportFileWithoutASchemaVersionFails() throws Exception
    {
        Path dir = ExportFixtures.exportDir(tmp, "normal");
        String json = ExportFixtures.EXPORTER_GSON.toJson(ExportFixtures.quest("OLD_QUEST"))
                .replace("\"schemaVersion\": 3,", "");
        Files.writeString(dir.resolve("old.json"), json);

        PipelineException e = assertThrows(PipelineException.class,
                () -> Bundler.bundle(ExportFixtures.request(dir, null), tmp.resolve("out")));
        assertTrue(e.getMessage().contains("schemaVersion"), e.getMessage());
    }

    @Test
    void anEmptyExportDirectoryFails() throws Exception
    {
        Path dir = Files.createDirectories(tmp.resolve("nothing"));
        assertThrows(PipelineException.class,
                () -> Bundler.bundle(ExportFixtures.request(dir, null), tmp.resolve("out")));
    }
}
