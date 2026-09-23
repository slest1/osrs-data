package com.slest.osrsdata.pipeline;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.slest.questdata.QuestData;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class Bundler
{
    public static final String FORMAT = "osrs-questdata-bundle";
    public static final String MANIFEST_FORMAT = "osrs-questdata-bundle-manifest";
    public static final int FORMAT_VERSION = 1;
    public static final String MANIFEST_FILE = "manifest.json";
    public static final String NOTICE =
            "Quest data from Quest Helper by Zoinkwiz and contributors, BSD 2-Clause License";

    private static final Set<String> ACCOUNT_TYPES = Set.of("NORMAL", "IRONMAN", "ULTIMATE_IRONMAN");
    private static final Pattern FULL_COMMIT = Pattern.compile("[0-9a-f]{40}");

    public record Input(String accountType, Path exportDir)
    {
    }

    public record Request(List<Input> inputs, QuestHelperJar questHelper, String upstreamCommit, int skillLevel,
                          Path previousManifest, Set<String> allowlist)
    {
    }

    public record FileEntry(String name, String accountType, long bytes, String sha256, long uncompressedBytes,
                            String uncompressedSha256, int entries)
    {
    }

    public static Set<String> defaultAllowlist()
    {
        try (InputStream in = Bundler.class.getResourceAsStream("unwalkable-allowlist.txt"))
        {
            if (in == null)
            {
                throw new PipelineException("unwalkable-allowlist.txt is missing from the pipeline's resources");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
            {
                return reader.lines()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .collect(Collectors.toCollection(TreeSet::new));
            }
        }
        catch (IOException e)
        {
            throw new PipelineException("cannot read unwalkable-allowlist.txt: " + e.getMessage(), e);
        }
    }

    public static List<FileEntry> bundle(Request request, Path outDir) throws IOException
    {
        if (request.inputs() == null || request.inputs().isEmpty())
        {
            throw new PipelineException("no export directories given");
        }

        Map<String, List<QuestData>> exports = new LinkedHashMap<>();
        Set<Integer> schemaVersions = new TreeSet<>();
        for (Input input : request.inputs())
        {
            String type = input.accountType() == null ? "" : input.accountType().toUpperCase(Locale.ROOT);
            if (!ACCOUNT_TYPES.contains(type))
            {
                throw new PipelineException("unknown account type '" + input.accountType() + "'; expected one of "
                        + new TreeSet<>(ACCOUNT_TYPES));
            }
            if (exports.containsKey(type))
            {
                throw new PipelineException(type + " is given twice");
            }
            exports.put(type, readExport(input.exportDir(), schemaVersions));
        }
        if (schemaVersions.size() > 1)
        {
            throw new PipelineException("exports mix schema versions " + schemaVersions
                    + "; re-export everything with one exporter build");
        }

        JsonObject source = source(request, schemaVersions.iterator().next());
        for (Map.Entry<String, List<QuestData>> export : exports.entrySet())
        {
            checkWalkable(export.getKey(), export.getValue(), request.allowlist());
        }
        if (request.previousManifest() != null)
        {
            Map<String, Integer> previous = previousCounts(request.previousManifest());
            for (Map.Entry<String, List<QuestData>> export : exports.entrySet())
            {
                Integer before = previous.get(export.getKey());
                int now = export.getValue().size();
                if (before != null && now < before)
                {
                    throw new PipelineException(export.getKey() + " has " + now + " entries but the previous bundle ("
                            + request.previousManifest() + ") had " + before + "; refusing to publish a smaller bundle");
                }
            }
        }

        String licenseText = licenseText();
        Map<String, byte[]> files = new LinkedHashMap<>();
        List<FileEntry> entries = new ArrayList<>();
        for (Map.Entry<String, List<QuestData>> export : exports.entrySet())
        {
            String type = export.getKey();
            List<QuestData> quests = export.getValue();
            byte[] raw = Json.COMPACT.toJson(document(type, request.skillLevel(), source, licenseText, quests))
                    .getBytes(StandardCharsets.UTF_8);
            byte[] gz = DeterministicGzip.gzip(raw);
            String name = "quests-" + type.toLowerCase(Locale.ROOT) + ".json";
            files.put(name, raw);
            files.put(name + ".gz", gz);
            entries.add(new FileEntry(name + ".gz", type, gz.length, Hashes.sha256Hex(gz), raw.length,
                    Hashes.sha256Hex(raw), quests.size()));
        }
        entries.sort(Comparator.comparing(FileEntry::name));

        Files.createDirectories(outDir);
        for (Map.Entry<String, byte[]> file : files.entrySet())
        {
            Files.write(outDir.resolve(file.getKey()), file.getValue());
        }
        Files.writeString(outDir.resolve(MANIFEST_FILE), Json.PRETTY.toJson(manifest(source, entries)) + "\n",
                StandardCharsets.UTF_8);
        return entries;
    }

    private static List<QuestData> readExport(Path dir, Set<Integer> schemaVersions) throws IOException
    {
        if (dir == null || !Files.isDirectory(dir))
        {
            throw new PipelineException("export directory " + dir + " does not exist");
        }
        List<Path> jsonFiles;
        try (Stream<Path> listing = Files.list(dir))
        {
            jsonFiles = listing.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
        }
        if (jsonFiles.isEmpty())
        {
            throw new PipelineException("export directory " + dir + " has no .json files");
        }

        List<QuestData> quests = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (Path file : jsonFiles)
        {
            JsonObject json = Json.object(Files.readString(file, StandardCharsets.UTF_8), file.toString());
            JsonElement version = json.get("schemaVersion");
            if (version == null || !version.isJsonPrimitive() || !version.getAsJsonPrimitive().isNumber())
            {
                throw new PipelineException(file + " has no schemaVersion; not an exporter file");
            }
            schemaVersions.add(version.getAsInt());
            QuestData quest;
            try
            {
                quest = Json.COMPACT.fromJson(json, QuestData.class);
            }
            catch (JsonParseException e)
            {
                throw new PipelineException(file + " does not match the exporter schema: " + e.getMessage(), e);
            }
            if (quest.questKey == null || quest.questKey.isBlank())
            {
                throw new PipelineException(file + " has no questKey");
            }
            if (!keys.add(quest.questKey))
            {
                throw new PipelineException(dir + " has " + quest.questKey + " twice");
            }
            quests.add(quest);
        }
        quests.sort(Comparator.comparing(q -> q.questKey));
        return quests;
    }

    private static JsonObject source(Request request, int schemaVersion)
    {
        QuestHelperJar qh = request.questHelper();
        List<String> missing = new ArrayList<>();
        if (qh == null || blank(qh.runeliteVersion()))
        {
            missing.add("runeliteVersion");
        }
        if (qh == null || blank(qh.questHelperVersion()))
        {
            missing.add("questHelperVersion");
        }
        if (qh == null || blank(qh.jarHash()))
        {
            missing.add("questHelperJarHash");
        }
        if (request.upstreamCommit() == null || !FULL_COMMIT.matcher(request.upstreamCommit()).matches())
        {
            missing.add("upstreamCommit (a full 40-character commit hash)");
        }
        if (!missing.isEmpty())
        {
            throw new PipelineException("missing source metadata: " + String.join(", ", missing));
        }

        JsonObject source = new JsonObject();
        source.addProperty("runeliteVersion", qh.runeliteVersion());
        source.addProperty("questHelperVersion", qh.questHelperVersion());
        source.addProperty("questHelperJarHash", qh.jarHash());
        source.addProperty("exporterSchemaVersion", schemaVersion);
        source.addProperty("upstreamCommit", request.upstreamCommit());
        return source;
    }

    private static void checkWalkable(String type, List<QuestData> quests, Set<String> allowlist)
    {
        Set<String> allowed = allowlist == null ? Set.of() : allowlist;
        List<String> offenders = new ArrayList<>();
        for (QuestData quest : quests)
        {
            long steps = quest.steps == null ? 0 : quest.steps.stream().filter(s -> s != null).count();
            int panels = quest.panels == null ? 0 : quest.panels.size();
            if ((steps == 0 || panels == 0) && !allowed.contains(quest.questKey))
            {
                offenders.add(quest.questKey + " (" + steps + " steps, " + panels + " panels)");
            }
        }
        if (!offenders.isEmpty())
        {
            throw new PipelineException(type + ": " + offenders.size() + " entries have no steps or no panels and are "
                    + "not in unwalkable-allowlist.txt: " + String.join(", ", offenders));
        }
    }

    private static Map<String, Integer> previousCounts(Path manifest) throws IOException
    {
        if (!Files.isRegularFile(manifest))
        {
            throw new PipelineException("previous manifest " + manifest + " does not exist");
        }
        JsonObject json = Json.object(Files.readString(manifest, StandardCharsets.UTF_8),
                "previous manifest " + manifest);
        JsonElement files = json.get("files");
        if (files == null || !files.isJsonArray())
        {
            throw new PipelineException("previous manifest " + manifest + " has no \"files\" list");
        }
        Map<String, Integer> counts = new HashMap<>();
        for (JsonElement element : (JsonArray) files)
        {
            if (!element.isJsonObject())
            {
                continue;
            }
            JsonObject file = element.getAsJsonObject();
            String type = Json.string(file, "accountType");
            String entries = Json.string(file, "entries");
            if (type == null || entries == null)
            {
                throw new PipelineException("previous manifest " + manifest + " lists a file without accountType/entries");
            }
            counts.merge(type, Integer.parseInt(entries), Math::max);
        }
        return counts;
    }

    private static JsonObject document(String type, int skillLevel, JsonObject source, String licenseText,
                                       List<QuestData> quests)
    {
        JsonObject doc = new JsonObject();
        doc.addProperty("format", FORMAT);
        doc.addProperty("formatVersion", FORMAT_VERSION);
        doc.addProperty("accountType", type);
        doc.addProperty("skillLevel", skillLevel);
        doc.add("source", source.deepCopy());
        JsonObject attribution = new JsonObject();
        attribution.addProperty("notice", NOTICE);
        attribution.addProperty("licenseText", licenseText);
        doc.add("attribution", attribution);
        JsonArray entries = new JsonArray();
        for (QuestData quest : quests)
        {
            entries.add(BundleTrimmer.entry(quest));
        }
        doc.add("entries", entries);
        return doc;
    }

    private static JsonObject manifest(JsonObject source, List<FileEntry> entries)
    {
        JsonObject manifest = new JsonObject();
        manifest.addProperty("format", MANIFEST_FORMAT);
        manifest.addProperty("formatVersion", FORMAT_VERSION);
        manifest.add("source", source.deepCopy());
        JsonArray files = new JsonArray();
        for (FileEntry entry : entries)
        {
            JsonObject file = new JsonObject();
            file.addProperty("name", entry.name());
            file.addProperty("accountType", entry.accountType());
            file.addProperty("bytes", entry.bytes());
            file.addProperty("sha256", entry.sha256());
            file.addProperty("uncompressedBytes", entry.uncompressedBytes());
            file.addProperty("uncompressedSha256", entry.uncompressedSha256());
            file.addProperty("entries", entry.entries());
            files.add(file);
        }
        manifest.add("files", files);
        return manifest;
    }

    private static String licenseText() throws IOException
    {
        try (InputStream in = Bundler.class.getResourceAsStream("quest-helper-LICENSE.txt"))
        {
            if (in == null)
            {
                throw new PipelineException("quest-helper-LICENSE.txt is missing from the pipeline's resources");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static boolean blank(String value)
    {
        return value == null || value.isBlank();
    }

    private Bundler()
    {
    }
}
