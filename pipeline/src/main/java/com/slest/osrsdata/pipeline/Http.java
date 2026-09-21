package com.slest.osrsdata.pipeline;

import java.io.IOException;

public interface Http
{
    byte[] get(String url) throws IOException;
}
