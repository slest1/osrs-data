package com.slest.osrsdata.pipeline;

import java.io.ByteArrayOutputStream;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

public final class DeterministicGzip
{
    // magic, deflate, no flags, mtime 0 so equal input gives equal bytes, max compression, OS unknown
    private static final byte[] HEADER = {
            0x1f, (byte) 0x8b,
            8,
            0,
            0, 0, 0, 0,
            2,
            (byte) 0xff
    };

    public static byte[] gzip(byte[] data)
    {
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length / 4 + 64);
        out.writeBytes(HEADER);

        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        try
        {
            deflater.setInput(data);
            deflater.finish();
            byte[] buffer = new byte[64 * 1024];
            while (!deflater.finished())
            {
                int n = deflater.deflate(buffer);
                out.write(buffer, 0, n);
            }
        }
        finally
        {
            deflater.end();
        }

        CRC32 crc = new CRC32();
        crc.update(data);
        writeIntLE(out, (int) crc.getValue());
        writeIntLE(out, data.length);
        return out.toByteArray();
    }

    private static void writeIntLE(ByteArrayOutputStream out, int value)
    {
        out.write(value);
        out.write(value >>> 8);
        out.write(value >>> 16);
        out.write(value >>> 24);
    }

    private DeterministicGzip()
    {
    }
}
