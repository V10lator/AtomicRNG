package de.V10lator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;

class EntropyQueue extends Thread {

    private static final AtomicBoolean lock = new AtomicBoolean(false);
    private static final ArrayList<Pointer> queue = new ArrayList<Pointer>();
    private static final long maxEntropy;
    private static final Pointer window = new Memory(4L);
    private static final int min_queue_size;
    
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
/*        File d = new File("random.sample");
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
        f = tf;*/
        
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
        min_queue_size = (int)(maxEnt >> 3);
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
            if(queue.size() < min_queue_size) { // Ensure min capacity.
                lock.set(false);
                continue;
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
            int free = (int) (maxEntropy - entropyAvail);
            
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
            queue.clear();
            queue.ensureCapacity(min_queue_size + newQueue.length - free);
            r = 0;
            for(Pointer p: newQueue) {
                if(newQueue.length - r++ > 512 && free > 0 && toOSrng(p)) {
                    free--;
                    p.clear(1L);
                } else
                    queue.add(p);
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
            p.setByte(0, bytes[i]);
            queue.add(p);
        }
        lock.set(false);
    }
    
    //static FileOutputStream f;
    private static boolean toOSrng(Pointer pointer) {
    /*    try {
            f.write(pointer.getByte(0));
            f.flush();
        } catch (IOException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }*/
        try {
            int nRet = LibCwrapper.ioctl(getRealFileDescriptor(AtomicRNG.osRNG.getFD()), LibCwrapper.RNDADDENTROPY, pointer);
            return nRet > -1;
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
