package com.slest.osrsdata.pipeline;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class JdkHttp implements Http
{
    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    @Override
    public byte[] get(String url) throws IOException
    {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(2))
                .header("User-Agent", "osrs-data-pipeline")
                .GET()
                .build();
        HttpResponse<byte[]> response;
        try
        {
            response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted fetching " + url, e);
        }
        if (response.statusCode() / 100 != 2)
        {
            throw new IOException("GET " + url + " answered HTTP " + response.statusCode());
        }
        return response.body();
    }
}
