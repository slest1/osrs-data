package com.slest.osrsdata.pipeline;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.ToNumberPolicy;

final class Json
{
    // LAZILY_PARSED_NUMBER: 434 in an untyped map is written back as 434, not 434.0
    static final Gson COMPACT = new GsonBuilder()
            .setObjectToNumberStrategy(ToNumberPolicy.LAZILY_PARSED_NUMBER)
            .disableHtmlEscaping()
            .create();

    static final Gson PRETTY = COMPACT.newBuilder().setPrettyPrinting().create();

    static JsonObject object(String text, String what)
    {
        try
        {
            JsonElement parsed = JsonParser.parseString(text);
            if (!parsed.isJsonObject())
            {
                throw new PipelineException(what + " is not a JSON object");
            }
            return parsed.getAsJsonObject();
        }
        catch (JsonParseException e)
        {
            throw new PipelineException(what + " is not valid JSON: " + e.getMessage(), e);
        }
    }

    static String string(JsonObject object, String member)
    {
        JsonElement value = object.get(member);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    private Json()
    {
    }
}
