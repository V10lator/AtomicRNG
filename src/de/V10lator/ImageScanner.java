package de.V10lator;

import java.nio.ByteBuffer;
import java.util.ArrayList;

class ImageScanner {

    private final int x, y;
    private static final int width, height;
//    private static final int[][][] lastValues;
    private final int[] bgr_filter = { 255, 255, 255 };
    long lastImpact = System.currentTimeMillis();
    
    static {
        width = AtomicRNG.width / (AtomicRNG.width >> 6);
        height = AtomicRNG.height / (AtomicRNG.height >> 6);
    }
    
    ImageScanner(int x, int y) {
        this.x = x;
        this.y = y;
    }
    
    private boolean adjust[][] = new boolean[3][2];
    ArrayList<Pixel> scan(ByteBuffer img, int wS, int nC, long frameTime, boolean[][] ignorePixels) {
        ArrayList<Pixel> impacts = new ArrayList<Pixel>();
        Pixel pixel;
        for(int i = 0; i < 3; i++) {
            adjust[i][0] = false;
            adjust[i][1] = true;
        }
        for(int y = this.y; y < this.y + height; y++) {
            for(int x = this.x; x < this.x + width; x++) {
                if(ignorePixels[x][y])
                    continue;
                pixel = filter(x, y, frameTime, img, wS, nC, ignorePixels, null);
                if(pixel != null)
                    impacts.add(pixel);
            }
        }
        for(int i = 0; i < 3; i++)
            if(adjust[i][0])
                if(adjust[i][1])
                    bgr_filter[i]--;
        if(!impacts.isEmpty()) {
            AtomicRNG.toOSrng(frameTime - lastImpact);
            lastImpact = frameTime;
        }
        return impacts;
    }
    
    private Pixel filter(int x, int y, long start, ByteBuffer buffer, int wS, int nC, boolean[][] ignore, Pixel pixel) {
        ignore[x][y] = true;
        int index = (y * wS) + (x * nC);
        int[] bgr = new int[3];
        for(int i = 0; i < 3; i++)
            bgr[i] = buffer.get(index + i) & 0xFF;
        int strength = 0, f, h = 0, hc = 0;
        /*
         * First filter each color channel independently.
         */
        for(int i = 0; i < 3; i++) {
            if(bgr[i] > bgr_filter[i]) {
                f = bgr_filter[i] + 13;
                if(bgr[i] > f) {
                    strength += bgr[i] - f;
                    continue;
                }
                h += bgr[i] - bgr_filter[i];
                hc++;
                bgr_filter[i] = bgr[i];
                adjust[i][1] = false;
            } else if(bgr[i] < bgr_filter[i])
                adjust[i][0] = true;
        }
        if(AtomicRNG.firstRun)
            return pixel;
        /*
         * If that failes combine the channels and filter again.
         * This is to eliminate blooming between the channels.
         */
        if(strength == 0) {
            if(hc < 2 || h < 13)
                return pixel;
            strength = h - 13;
        }
        if(pixel == null)
            pixel = new Pixel(x, y, strength, start);
        else
            pixel.charge(x, y, strength);
        raytrace(this, x, y, start, buffer, wS, nC, ignore, pixel);
        return pixel;
    }
    
    private static void raytrace(ImageScanner instance, int x, int y, long start, ByteBuffer buffer, int wS, int nC, boolean[][] ignore, Pixel pixel) {
        x -= 1;
        y -= 1;
        for(int ym = y; ym < y + 3; ym++) {
            if(ym < 0 || ym >= AtomicRNG.height)
                continue;
            for(int xm = x; xm < x + 3; xm++) {
                if(xm < 0 || xm >= AtomicRNG.width)
                    continue;
                if(!ignore[xm][ym])
                    instance.filter(xm, ym, start, buffer, wS, nC, ignore, pixel);
            }
        }
    }
}
