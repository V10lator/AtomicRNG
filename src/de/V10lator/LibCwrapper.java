package de.V10lator;

import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;

class LibCwrapper {

    static int
        RNDGETENTCNT = _IOR('R', 0x00, 4),
        RNDADDENTROPY = _IOW('R', 0x03, 8)
        ;
    
    static {
        Native.register(Platform.C_LIBRARY_NAME);
        // Sanity check
        if(RNDADDENTROPY != 0x40085203) {
            System.out.println("Sanity failed: "+RNDADDENTROPY+" vs. "+0x40085203);
            System.exit(1);
        }
    }
    
    static native int ioctl(int fd, int request, Pointer args);
    
    private static int _IOR(char who, int nr, int size) {
        return _IOC(2L, who, nr, size);
    }
    
    private static int _IOW(int who, int nr, int size) {
        return _IOC(1L, who, nr, size);
    }
    
    private static int _IOC(long dir, int type, int nr, int size) {
        return (int)(dir << 30) |
                (type << 8) |
                nr |
                (size << 16);
    }
}
