package de.V10lator;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

class ImageScanner implements Runnable {

    private final int[] bgr = { 255, 255, 255 };
    private final int x, y, width, height;
    long lastImpact = System.currentTimeMillis();
    
    // Shared temp values for faster ray-tracing:
    private final int[] tmp_bgr = new int[3];
    private int tmp_index, tmp_fi, tmp_s;
    private static int tmp_wS, tmp_nC;
    private Pixel tmp_pixel;
    private static AtomicBoolean[][] tmp_ignore;
    private static ByteBuffer tmp_buffer;
    private static long tmp_start;
    ArrayList<Pixel> impacts;
    
    
    ImageScanner(int x, int y) {
        this.x = x;
        this.y = y;
        width = AtomicRNG.width / (AtomicRNG.width >> 5);
        height = AtomicRNG.height / (AtomicRNG.height >> 5);
    }
    
    
    static int getStrength(ImageScanner instance) {
        int ret = 0;
        int[] adjust = { -1, -1, -1 };
        boolean active = false;
        for(int i = 0; i < 3; i++) {
            if(instance.tmp_bgr[i] > instance.bgr[i]) {
                if(instance.tmp_bgr[i] > instance.bgr[i] * 1.6125f) {
                    active = true;
                    ret += instance.tmp_bgr[i] - instance.bgr[i];
                } else
                    adjust[i] = instance.tmp_bgr[i];
            } else if(AtomicRNG.rand.nextInt(10000) < 5)
                adjust[i] = instance.bgr[i] - ((instance.bgr[i] - instance.tmp_bgr[i]) / 8);
                
        }
        if(!active) { // Update filter
            for(int i = 0; i < 3; i++) {
                if(adjust[i] != -1)
                    instance.bgr[i] = adjust[i];
            }
            return 0;
        }
        return ret;
    }
    
    static void init(ByteBuffer img, int wS, int nC, long frameTime, AtomicBoolean[][] ignorePixels) {
        tmp_ignore = ignorePixels;
        tmp_wS = wS;
        tmp_nC = nC;
        tmp_buffer = img;
        tmp_start = frameTime;
    }
    
    @Override
    public void run() {
        impacts = new ArrayList<Pixel>();
        for(int y = this.y; y < this.y + this.height; y++) {
            for(int x = this.x; x < this.x + this.width; x++) {
                if(tmp_ignore[x][y].get())
                    continue;
                filter(x, y, this);
                if(tmp_pixel != null)
                    impacts.add(tmp_pixel);
            }
        }
        if(!impacts.isEmpty()) {
            AtomicRNG.toOSrng(tmp_start - lastImpact);
            lastImpact = tmp_start;
        }
    }
    
    static void cleanup() {
        tmp_wS =
                tmp_nC =
                0;
        tmp_start = 0L;
        tmp_ignore = null;
        tmp_buffer = null;
    }
    
    void cleanupInstance() {
        tmp_bgr[0] =
                tmp_bgr[1] =
                tmp_bgr[2] =
                tmp_index =
                tmp_fi =
                tmp_s =
                0;
        tmp_pixel = null;
    }
    
    private static void filter(int x, int y, ImageScanner instance) {
        if(!tmp_ignore[x][y].compareAndSet(false, true))
            return;
        instance.tmp_index = (y * tmp_wS) + (x * tmp_nC);
        for(instance.tmp_fi = 0; instance.tmp_fi < 3; instance.tmp_fi++)
            instance.tmp_bgr[instance.tmp_fi] = tmp_buffer.get(instance.tmp_index + instance.tmp_fi) & 0xFF;
        instance.tmp_s = getStrength(instance);
        if(instance.tmp_s == 0 || AtomicRNG.firstRun)
            return;
        if(instance.tmp_pixel == null)
            instance.tmp_pixel = new Pixel(x, y, instance.tmp_s, tmp_start);
        else
            instance.tmp_pixel.charge(x, y, instance.tmp_s);
        raytrace(x, y, instance);
    }
    
    private static void raytrace(int x, int y, ImageScanner instance) {
        x -= 1;
        y -= 1;
        for(int ye = y + 3; y < ye + 3; y++) {
            if(y < 0 || y >= AtomicRNG.height)
                continue;
            for(int xe = x + 3; x < xe + 3; x++) {
                if(x < 0 || x >= AtomicRNG.width)
                    continue;
                if(!tmp_ignore[x][y].get())
                    filter(x, y, instance);
            }
        }
    }
}
