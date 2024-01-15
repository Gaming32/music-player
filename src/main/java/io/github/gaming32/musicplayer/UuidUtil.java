package io.github.gaming32.musicplayer;

import com.google.common.hash.HashCode;

import java.util.UUID;

public class UuidUtil {
    @SuppressWarnings("lossy-conversions")
    public static UUID uuidFromHashCode(HashCode hashCode) {
        final byte[] bytes = new byte[16];
        hashCode.writeBytesTo(bytes, 0, 16);
        bytes[6] &= 0x0f; // clear version
        bytes[6] |= 0x50; // set to version 5
        bytes[8] &= 0x3f; // clear variant
        bytes[8] |= 0x80; // set to IETF variant
        return uuidFromBytes(bytes);
    }

    public static UUID uuidFromBytes(byte[] data) {
        if (data.length != 16) {
            throw new IllegalArgumentException("UUID data must be exactly 16 bytes long");
        }
        long msb = 0;
        long lsb = 0;
        for (int i = 0; i < 8; i++)
            msb = (msb << 8) | (data[i] & 0xff);
        for (int i = 8; i < 16; i++)
            lsb = (lsb << 8) | (data[i] & 0xff);
        return new UUID(msb, lsb);
    }
}
