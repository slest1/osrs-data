package com.slest.osrsdata.pipeline;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public record QuestHelperJar(String runeliteVersion, String questHelperVersion, String jarHash, long jarSize)
{
    public static final String METADATA_FILE = "quest-helper.json";
    public static final String JAR_FILE = "quest-helper.jar";

    public void write(Path dir) throws IOException
    {
        JsonObject json = new JsonObject();
        json.addProperty("runeliteVersion", runeliteVersion);
        json.addProperty("questHelperVersion", questHelperVersion);
        json.addProperty("jarHash", jarHash);
        json.addProperty("jarSize", jarSize);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(METADATA_FILE), Json.PRETTY.toJson(json) + "\n", StandardCharsets.UTF_8);
    }

    public static QuestHelperJar read(Path metadataFile) throws IOException
    {
        JsonObject json = Json.object(Files.readString(metadataFile, StandardCharsets.UTF_8),
                metadataFile.toString());
        String size = Json.string(json, "jarSize");
        return new QuestHelperJar(
                Json.string(json, "runeliteVersion"),
                Json.string(json, "questHelperVersion"),
                Json.string(json, "jarHash"),
                size == null ? -1 : Long.parseLong(size));
    }
}
