package com.slest.osrsdata.pipeline;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;

public final class Hashes
{
    public static String jarHash(byte[] bytes)
    {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(sha256(bytes));
    }

    public static String sha256Hex(byte[] bytes)
    {
        return HexFormat.of().formatHex(sha256(bytes));
    }

    private static byte[] sha256(byte[] bytes)
    {
        try
        {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("every JVM has SHA-256", e);
        }
    }

    private Hashes()
    {
    }
}
