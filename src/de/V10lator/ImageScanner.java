package de.V10lator;

import java.nio.ByteBuffer;
import java.util.ArrayList;

class ImageScanner {

    private final int[] bgr = { 255, 255, 255 };
    private final int x, y, width, height;
    long lastImpact = System.currentTimeMillis();
    
    ImageScanner(int x, int y) {
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
    
    ArrayList<Pixel> scan(ByteBuffer img, int wS, int nC, long frameTime, boolean[][] ignorePixels) {
        ArrayList<Pixel> impacts = new ArrayList<Pixel>();
        Pixel pixel;
        for(int y = this.y; y < this.y + this.height; y++) {
            for(int x = this.x; x < this.x + this.width; x++) {
                if(ignorePixels[x][y])
                    continue;
                pixel = filter(this, x, y, frameTime, img, wS, nC, ignorePixels, null);
                if(pixel != null)
                    impacts.add(pixel);
            }
        }
        if(!impacts.isEmpty()) {
            AtomicRNG.toOSrng(frameTime - lastImpact);
            lastImpact = frameTime;
        }
        return impacts;
    }
    
    private static Pixel filter(ImageScanner pixelGroup, int x, int y, long start, ByteBuffer buffer, int wS, int nC, boolean[][] ignore, Pixel pixel) {
        ignore[x][y] = true;
        int index = (y * wS) + (x * nC);
        int[] bgr = new int[3];
        for(int i = 0; i < 3; i++)
            bgr[i] = buffer.get(index + i) & 0xFF;
        int strength = pixelGroup.getStrength(bgr);
        if(strength == 0 || AtomicRNG.firstRun)
            return pixel;
        if(pixel == null)
            pixel = new Pixel(x, y, strength, start);
        else
            pixel.charge(x, y, strength);
        raytrace(pixelGroup, x, y, start, buffer, wS, nC, ignore, pixel);
        return pixel;
    }
    
    private static void raytrace(ImageScanner pixelGroup, int x, int y, long start, ByteBuffer buffer, int wS, int nC, boolean[][] ignore, Pixel pixel) {
        x -= 1;
        y -= 1;
        for(int ym = y; ym < y + 3; ym++) {
            if(ym < 0 || ym >= AtomicRNG.height)
                continue;
            for(int xm = x; xm < x + 3; xm++) {
                if(xm < 0 || xm >= AtomicRNG.width)
                    continue;
                if(!ignore[xm][ym])
                    filter(pixelGroup, xm, ym, start, buffer, wS, nC, ignore, pixel);
            }
        }
    }
}
