package com.slest.osrsdata.pipeline;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class FakeHttp implements Http
{
    private final Map<String, byte[]> bodies = new HashMap<>();
    final List<String> requested = new ArrayList<>();

    FakeHttp serve(String url, byte[] body)
    {
        bodies.put(url, body);
        return this;
    }

    @Override
    public byte[] get(String url) throws IOException
    {
        requested.add(url);
        byte[] body = bodies.get(url);
        if (body == null)
        {
            throw new IOException("404 for " + url);
        }
        return body.clone();
    }
}
