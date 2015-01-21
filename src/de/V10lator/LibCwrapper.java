package de.V10lator;

import java.util.ArrayList;
import java.util.List;

import com.sun.jna.LastErrorException;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;

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
    
   // static native int ioctl(long fd, long request, byte arg) throws LastErrorException;
    static native int ioctl(int fd, int request, Pointer args) throws LastErrorException;
    static native int ioctl(int fd, int request, Structure args) throws LastErrorException;
    
    static native int open(String path, int flags); 
    
    static native int close(int fd);
    
    private static int _IOR(char who, int nr, int size) {
        return _IOC(2L, who, nr, size);
    }
    
    private static int _IOW(char who, int nr, int size) {
        return _IOC(1L, who, nr, size);
    }
    
    private static int _IOC(long dir, int type, int nr, int size) {
        return (int)(dir << 30) |
                (type << 8) |
                nr |
                (size << 16);
    }
    
    public static class Rand_pool_info extends Structure {
        
        public final int entropy_count;
        public final int buf_size;
        public final byte[] buf;
        private static final ArrayList<String> fieldOrder;
        
        static {
            fieldOrder = new ArrayList<String>(3);
            fieldOrder.add("entropy_count");
            fieldOrder.add("buf_size");
            fieldOrder.add("buf");
        }
        
        Rand_pool_info(byte b) {
            buf = new byte[1];
            buf[0] = b;
            entropy_count = 8;
            buf_size = 1;
            
            setAutoRead(false);
        }
        
        @Override
        protected List<String> getFieldOrder() {
            return fieldOrder;
        }
    }
}
