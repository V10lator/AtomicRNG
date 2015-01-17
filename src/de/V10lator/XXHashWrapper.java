package de.V10lator;

import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;

public class XXHashWrapper {
    
    static {
        Native.register("xxhash.so");
    }
    
    static native NativeLong XXH64(Pointer input, int length, long seed);
}
