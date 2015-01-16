package de.V10lator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;

class EntropyQueue extends Thread {

    private static final AtomicBoolean lock = new AtomicBoolean(false);
    private static final ArrayList<Pointer> queue = new ArrayList<Pointer>();
    private static final long maxEntropy;
    private static final Pointer window = new Memory(4L);
    
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
            int ret;
            try {
                if((ret = LibCwrapper.ioctl(AtomicRNG.getRealFileDescriptor(AtomicRNG.osRNG.getFD()), LibCwrapper.RNDGETENTCNT, window)) != 0) {
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
            Iterator<Pointer> iter = queue.iterator();
            while(iter.hasNext()) {
                if(free < 4)
                    break;
                if(AtomicRNG.toOSrng(iter.next(), false)) {
                    iter.remove();
                    free -= 4;
                } else
                    break;
            }
            lock.set(false);
        }
    }
    
    static void add(Pointer pointer) {
        while(!lock.compareAndSet(false, true))
            try {
                Thread.sleep(1L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        queue.add(pointer);
        lock.set(false);
    }
}
