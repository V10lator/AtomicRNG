package de.V10lator;

import java.nio.ByteBuffer;
import java.util.ArrayList;

class PixelGroup {

    private final int[] bgr = { 255, 255, 255 };
    private final int x, y, width, height;
    long lastImpact = System.currentTimeMillis();
    
    // Shared temp values for faster ray-tracing:
    private static final int[] tmp_bgr = new int[3];
    private static int tmp_index, tmp_fi, tmp_s, tmp_wS, tmp_nC;
    private static Pixel tmp_pixel;
    private static boolean[][] tmp_ignore;
    private static ByteBuffer tmp_buffer;
    private static long tmp_start;
    private static PixelGroup tmp_instance;
    
    
    PixelGroup(int x, int y) {
        this.x = x;
        this.y = y;
        width = AtomicRNG.width / (AtomicRNG.width >> 5);
        height = AtomicRNG.height / (AtomicRNG.height >> 5);
    }
    
    
    int getStrength(int[] bgr) {
        int ret = 0;
        int[] adjust = { -1, -1, -1 };
        boolean active = false;
        for(int i = 0; i < 3; i++) {
            if(bgr[i] > this.bgr[i]) {
                if(bgr[i] > this.bgr[i] * 1.6125f) {
                    active = true;
                    ret += bgr[i] - this.bgr[i];
                } else
                    adjust[i] = bgr[i];
            } else if(AtomicRNG.rand.nextInt(10000) < 5)
                adjust[i] = this.bgr[i] - ((this.bgr[i] - bgr[i]) / 8);
                
        }
        if(!active) { // Update filter
            for(int i = 0; i < 3; i++) {
                if(adjust[i] != -1)
                    this.bgr[i] = adjust[i];
            }
            return 0;
        }
        return ret;
    }
    
    static void init(ByteBuffer img, int wS, int nC, long frameTime, boolean[][] ignorePixels) {
        tmp_ignore = ignorePixels;
        tmp_wS = wS;
        tmp_nC = nC;
        tmp_buffer = img;
        tmp_start = frameTime;
    }
    
    ArrayList<Pixel> scan() {
        ArrayList<Pixel> impacts = new ArrayList<Pixel>();
        tmp_instance = this;
        for(int y = this.y; y < this.y + this.height; y++) {
            for(int x = this.x; x < this.x + this.width; x++) {
                if(tmp_ignore[x][y])
                    continue;
                filter(x, y);
                if(tmp_pixel != null)
                    impacts.add(tmp_pixel);
            }
        }
        if(!impacts.isEmpty()) {
            AtomicRNG.toOSrng(tmp_start - lastImpact);
            lastImpact = tmp_start;
        }
        return impacts;
    }
    
    static void cleanup() {
        tmp_bgr[0] =
                tmp_bgr[1] =
                tmp_bgr[2] =
                tmp_index =
                tmp_fi =
                tmp_s =
                tmp_wS =
                tmp_nC =
                0;
        tmp_start = 0L;
        tmp_pixel = null;
        tmp_ignore = null;
        tmp_buffer = null;
        tmp_instance = null;
    }
    
    private static void filter(int x, int y) {
        tmp_ignore[x][y] = true;
        tmp_index = (y * tmp_wS) + (x * tmp_nC);
        for(tmp_fi = 0; tmp_fi < 3; tmp_fi++)
            tmp_bgr[tmp_fi] = tmp_buffer.get(tmp_index + tmp_fi) & 0xFF;
        tmp_s = tmp_instance.getStrength(tmp_bgr);
        if(tmp_s == 0 || AtomicRNG.firstRun)
            return;
        if(tmp_pixel == null)
            tmp_pixel = new Pixel(x, y, tmp_s, tmp_start);
        else
            tmp_pixel.charge(x, y, tmp_s);
        raytrace(x, y);
    }
    
    private static void raytrace(int x, int y) {
        x -= 1;
        y -= 1;
        for(int ye = y + 3; y < ye + 3; y++) {
            if(y < 0 || y >= AtomicRNG.height)
                continue;
            for(int xe = x + 3; x < xe + 3; x++) {
                if(x < 0 || x >= AtomicRNG.width)
                    continue;
                if(!tmp_ignore[x][y])
                    filter(x, y);
            }
        }
    }
}
