package de.V10lator;

import com.sun.jna.Native;

public class XXHashWrapper {
    
    static {
        Native.register("xxhash.so");
    }
    
    static native long XXH64(byte[] input, int offset, int len, long seed);
}
