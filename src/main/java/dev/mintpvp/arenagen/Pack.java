package dev.mintpvp.arenagen;

/** pack position */
final class Pack {
    private Pack() {}

    static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (y & 0xFFF) << 26) | (z & 0x3FFFFFF);
    }

    static int x(long k) { return (int) (k >> 38); }

    static int y(long k) { return (int) ((k << 26) >> 52); }

    static int z(long k) { return (int) ((k << 38) >> 38); }
}
