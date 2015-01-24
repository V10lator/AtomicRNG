package de.V10lator;

import java.nio.ByteBuffer;
import java.util.Arrays;
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
        private static final List<String> fieldOrder = Arrays.asList(
                "entropy_count",
                "buf_size",
                "buf"
                );
        
        Rand_pool_info(ByteBuffer buffer) {
            buf_size = buffer.limit();
            entropy_count = buf_size << 3;
            buf = new byte[buf_size];
            for(int i = 0; i < buf_size; i++)
                buf[i] = buffer.get();
            buffer.flip();
            setAutoRead(false);
        }
        
        @Override
        protected List<String> getFieldOrder() {
            return fieldOrder;
        }
    }
}
