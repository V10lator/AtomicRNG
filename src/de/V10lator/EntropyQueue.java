package de.V10lator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.jna.LastErrorException;
import com.sun.jna.Memory;

class EntropyQueue extends Thread {

    private static final AtomicBoolean lock = new AtomicBoolean(false);
    private static final ArrayList<Byte> queue = new ArrayList<Byte>();
    private static final long minCapacity = 256L; // 4 hashes
    private static final long maxCapacity = 2097152L; // 2 GB TODO: Don't hardcode.
    private static final long maxEntropy;
    private static final Memory window = new Memory(4L);
    private static int fd = -1;
    
    static {
        long maxEnt = 4096;
        File f = new File("/proc/sys/kernel/random/poolsize");
        if(f.exists() && !f.isDirectory() && f.canRead()) {
            BufferedReader r = null;
            try {
                r = new BufferedReader(new FileReader(f));
                maxEnt = Integer.parseInt(r.readLine());
            } catch(Exception e) {
                e.printStackTrace();
            }
            if(r != null)
                try {
                    r.close();
                } catch (IOException e) {}
        }
        maxEntropy = maxEnt;
    }
    
    EntropyQueue() {}
    
    static void fileInit() {
        File d = new File("random.sample");
        if(d.exists())
            d.delete();
        FileOutputStream tf = null;
        try {
            d.createNewFile();
            tf = new FileOutputStream(d);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        f = tf;
    }
    
    @Override
    public void run() {
        while(!AtomicRNG.stopped) {
            try {
                Thread.sleep(5L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            while(!lock.compareAndSet(false, true))
                try {
                    Thread.sleep(2L);
                } catch (InterruptedException e) {}
            int qs = queue.size();
            if(qs < minCapacity) {
                lock.set(false);
                continue;
            }
            try {
                LibCwrapper.ioctl(fd, LibCwrapper.RNDGETENTCNT, window);
            } catch (LastErrorException e) {
                e.printStackTrace();
                lock.set(false);
                continue;
            }
            long entropyBitsAvail = (window.getInt(0) & 0xffffffffL);
            window.clear(4L);
            long entropyAvail = entropyBitsAvail >> 3;
            if(entropyBitsAvail % 8 != 0)
                entropyAvail++;
            int free = (int) (maxEntropy - entropyAvail);
            if(free > 0) {
                ByteBuffer buffer = ByteBuffer.allocate(free);
                byte b;
                Iterator<Byte> iter;
                while(free > 0 && qs >= minCapacity) {
                    iter = queue.iterator();
                    while(iter.hasNext()) {
                        b = iter.next();
                        if(AtomicRNG.rand.nextBoolean())
                            continue;
                        buffer.put(b);
                        qs--;
                        free--;
                        iter.remove();
                    }
                }
                buffer.flip();
                if(!toOSrng(new LibCwrapper.Rand_pool_info(buffer))) {
                    while(buffer.hasRemaining())
                        queue.add(buffer.get());
                }
                lock.set(false);
            }
        }
    }
    
    static void add(byte[] bytes) {
        while(!lock.compareAndSet(false, true))
            try {
                Thread.sleep(1L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        for(int i = 0; i < bytes.length; i++) {
            if(queue.size() >= maxCapacity)
                break;
            queue.add(bytes[i]);
        }
        lock.set(false);
    }
    
    static FileOutputStream f = null;
    private static boolean toOSrng(LibCwrapper.Rand_pool_info rpi) {
        try {
            LibCwrapper.ioctl(fd, LibCwrapper.RNDADDENTROPY, rpi);
            if(f != null)
                try {
                    for(int i = 0; i < rpi.buf_size; i++)
                        f.write(rpi.buf[i]);
                    f.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
    
    static void resetOSrng() {
        try {
            byte[] dummy = new byte[1];
            AtomicRNG.rand.nextBytes(dummy);
            if(fd == -1) 
                LibCwrapper.close(fd);
            fd = LibCwrapper.open("/dev/random", 0x00000002);
        } catch (LastErrorException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    static void cleanup() {
        while(!lock.compareAndSet(false, true))
            continue;
        queue.clear();
        if(fd != -1)
            LibCwrapper.close(fd);
        lock.set(false);
    }
    
    static String getStats() {
        HashSet<Byte> set = new HashSet<Byte>();
        while(!lock.compareAndSet(false, true))
            try {
                Thread.sleep(1L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        for(Byte b: queue)
            if(!set.contains(b))
                set.add(b);
        String ret = set.size()+"//"+queue.size();
        lock.set(false);
        return ret;
    }
}
