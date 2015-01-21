package de.V10lator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.jna.LastErrorException;
import com.sun.jna.Memory;

class EntropyQueue extends Thread {

    private static final AtomicBoolean lock = new AtomicBoolean(false);
    private static final ArrayList<LibCwrapper.Rand_pool_info> queue = new ArrayList<LibCwrapper.Rand_pool_info>();
    private static final long minCapacity = 256; //  4 hashes
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
                LibCwrapper.Rand_pool_info rpi;
                Iterator<LibCwrapper.Rand_pool_info> iter = queue.iterator();
                while(iter.hasNext()) {
                    rpi = iter.next();
                    if(qs < 512 || AtomicRNG.rand.nextBoolean())
                        continue; // randomly skip some bytes. We won't loose them, in the next round they'll again have a change to get feedet.
                    if(free-- == 0 || !toOSrng(rpi))
                        break;
                    iter.remove();
                    qs--;
                }
            }
            lock.set(false);
        }
    }
    
    static void add(byte[] bytes) {
        while(!lock.compareAndSet(false, true))
            try {
                Thread.sleep(1L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        LibCwrapper.Rand_pool_info rpi;
        for(int i = 0; i < bytes.length; i++) {
            rpi = new LibCwrapper.Rand_pool_info(bytes[i]);
            queue.add(rpi);
        }
        lock.set(false);
    }
    
    static FileOutputStream f = null;
    private static boolean toOSrng(LibCwrapper.Rand_pool_info rpi) {
        try {
            LibCwrapper.ioctl(fd, LibCwrapper.RNDADDENTROPY, rpi);
            if(f != null)
                try {
                    f.write(rpi.buf[0]);
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
}
