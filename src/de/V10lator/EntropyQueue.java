package de.V10lator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.jna.Memory;
import com.sun.jna.Pointer;

class EntropyQueue extends Thread {

    private static final AtomicBoolean lock = new AtomicBoolean(false);
    private static final ArrayList<Pointer> queue = new ArrayList<Pointer>();
    private static final long minCapacity = 256; //  4 hashes
    private static final long maxEntropy;
    private static final Pointer window = new Memory(4L);
    private static FileOutputStream osRNG = null;
    
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
            if(queue.size() < minCapacity) {
                lock.set(false);
                continue;
            }
            int ret;
            try {
                if((ret = LibCwrapper.ioctl(getRealFileDescriptor(osRNG.getFD()), LibCwrapper.RNDGETENTCNT, window)) != 0) {
                    System.err.println("ioctl RNDGETENTCNT returned "+ret);
                    lock.set(false);
                    continue;
                }
            } catch (IOException e) {
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
            int qs = queue.size();
            if(free > 0) {
                Pointer p;
                Iterator<Pointer> iter = queue.iterator();
                while(iter.hasNext()) {
                    p = iter.next();
                    if(qs < 512 || AtomicRNG.rand.nextBoolean())
                        continue; // randomly skip some bytes. We won't loose them, in the next round they'll again have a change to get feedet.
                    if(free-- == 0 || !toOSrng(p))
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
        Pointer p;
        for(int i = 0; i < bytes.length; i++) {
            p = new Memory(1L);
            p.setByte(0, bytes[i]);
            queue.add(p);
        }
        lock.set(false);
    }
    
    static FileOutputStream f = null;
    private static boolean toOSrng(Pointer pointer) {
        try {
            int nRet = LibCwrapper.ioctl(getRealFileDescriptor(osRNG.getFD()), LibCwrapper.RNDADDENTROPY, pointer);
            if(nRet > -1) {
                if(f != null)
                    try {
                        f.write(pointer.getByte(0));
                        f.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                return true;
            } else {
                System.out.println("Error: Ioctl returned "+nRet+System.lineSeparator()+
                        "This could be a race condition and should happen rarely only."+System.lineSeparator()+
                        "Will try again in 2 ms.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        resetOSrng();
        return false;
    }
    
    static void resetOSrng() {
        try {
            byte[] dummy = new byte[1];
            AtomicRNG.rand.nextBytes(dummy);
            if(osRNG != null)
                try {
                    osRNG.close();
                } catch (IOException e) {}
            File osRNGfile = new File("/dev/random");
            if(!osRNGfile.exists() || osRNGfile.isDirectory() || !osRNGfile.canWrite()) { // isDirectory() cause isFile() returns false.
                System.out.println("error ("+osRNGfile.exists()+"/"+(!osRNGfile.isDirectory())+"/"+osRNGfile.canWrite()+") !");
                System.exit(1);
            }
            osRNG = new FileOutputStream(osRNGfile);
            osRNG.write(dummy); // we need this to get the file descriptor.
            osRNG.flush();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    static int getRealFileDescriptor(FileDescriptor fd) {
        try {
            return __fd.getInt(fd);
        } catch (IllegalArgumentException | IllegalAccessException e) {}
        return -1;
    }
    
    static void cleanup() {
        while(!lock.compareAndSet(false, true))
            continue;
        queue.clear();
        if(osRNG != null)
            try {
                osRNG.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        lock.set(false);
    }
}
