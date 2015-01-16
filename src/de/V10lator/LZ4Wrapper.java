package de.V10lator;

import com.sun.jna.Native;

public class LZ4Wrapper {
    
    static {
        Native.register("liblz4-java.so");
        _init();
    }
    
    static native long XXH64(byte[] input, int offset, int len, long seed);
    
    private static native void _init();
}
