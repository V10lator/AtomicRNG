package de.V10lator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;

class EntropyQueue extends Thread {

    private static final AtomicBoolean lock = new AtomicBoolean(false);
    private static List<Pointer> queue = new ArrayList<Pointer>();
    private static final long maxEntropy;
    private static final Pointer window = new Memory(4L);
    
    private static final Field __fd;
    static {
        Field _fd;
        try {
            _fd = FileDescriptor.class.getDeclaredField("fd");
            _fd.setAccessible(true);
        } catch (Exception ex) {
            ex.printStackTrace();
            _fd = null;
            System.exit(1);
        }   
        __fd = _fd;
    }
    
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
        new EntropyQueue().start();
    }
    
    private EntropyQueue() {}
    
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
            if(queue.isEmpty()) {
                lock.set(false);
                continue;
            }
            
            int r;
            Pointer[] newQueue = new Pointer[queue.size()];
            for(Pointer p: queue)
                while(true) {
                    r = AtomicRNG.rand.nextInt(queue.size());
                    if(newQueue[r] != null)
                        continue;
                    newQueue[r] = p;
                    break;
                }
            
            int ret;
            try {
                if((ret = LibCwrapper.ioctl(getRealFileDescriptor(AtomicRNG.osRNG.getFD()), LibCwrapper.RNDGETENTCNT, window)) != 0) {
                    System.err.println("ioctl RNDGETENTCNT returned "+ret);
                    lock.set(false);
                    continue;
                }
            } catch (IOException e) {
                e.printStackTrace();
                lock.set(false);
                continue;
            }
            long entropyAvail = window.getInt(0) & 0xffffffffL;
            window.clear(4L);
            long free = maxEntropy - entropyAvail;
            queue = new ArrayList<Pointer>(Arrays.asList(newQueue));
            Iterator<Pointer> iter = queue.iterator();
            Memory pointer;
            while(iter.hasNext()) {
                pointer = (Memory) iter.next();
                if(free < pointer.size())
                    continue;
                if(toOSrng(pointer)) {
                    iter.remove();
                    free -= pointer.size();
                    pointer.clear(pointer.size());
                } else
                    break;
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
        Pointer p;
        for(int i = 0; i < bytes.length; i++) {
            p = new Memory(1L);
            queue.add(p);
        }
        lock.set(false);
    }
    
    private static boolean toOSrng(Pointer pointer) {
        try {
            return LibCwrapper.ioctl(getRealFileDescriptor(AtomicRNG.osRNG.getFD()), LibCwrapper.RNDADDENTROPY, pointer) == 0;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    private static int rFDC = -1;
    static int getRealFileDescriptor(FileDescriptor fd) {
        if(rFDC == -1)
            try {
                rFDC = __fd.getInt(fd);
            } catch (Exception e) {
                e.printStackTrace();
            }
        return rFDC;
    }
}
