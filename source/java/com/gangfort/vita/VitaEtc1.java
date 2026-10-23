package com.gangfort.vita;

import java.nio.ByteBuffer;

public final class VitaEtc1 {
    private VitaEtc1() {
    }

    public static int getWidthPKM(ByteBuffer data, int offset) {
        return readUnsignedShort(data, offset + 12);
    }

    public static int getHeightPKM(ByteBuffer data, int offset) {
        return readUnsignedShort(data, offset + 14);
    }

    private static int readUnsignedShort(ByteBuffer data, int offset) {
        return ((data.get(offset) & 0xFF) << 8) | (data.get(offset + 1) & 0xFF);
    }
}
