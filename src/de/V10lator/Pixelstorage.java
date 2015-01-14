package de.V10lator;

class Pixelstorage {
    private final int[] maxRGB;
    private final int[] lastRGB;
    final int[] power = { 0, 0, 0 };
    private final short cooldown[] = { 0, 0, 0 };
    private long started = 0L;
    boolean active = false;
    private boolean tick = true;
    
    Pixelstorage(int[] rgb, int[] maxRGB) {
        this.maxRGB = maxRGB;
        lastRGB = rgb;
    }
    
    Pixelstorage update(int rgb[]) {
        active = false;
        for(int i = 0; i < 3; i++) {
            if(maxRGB[i] < rgb[i]) {
                if(cooldown[i] == 0) {
                    started = System.currentTimeMillis();
                    power[i] = lastRGB[i] = rgb[i];
                    cooldown[i] = 10;
                    continue;
                } else {
                    if(rgb[i] > lastRGB[i])
                        lastRGB[i] = rgb[i];
                    if(cooldown[i] == 1)
                        maxRGB[i] = lastRGB[i];
                    else
                        power[i] += rgb[i];
                    cooldown[i]--;
                }
            } else {
                if(cooldown[i] > 0) {
                    active = true;
                    lastRGB[i] = rgb[i];
                    cooldown[i] = 0;
                } else
                    if(tick && maxRGB[i] > rgb[i])
                        maxRGB[i]--;
            }
        }
        tick = !tick;
        return this;
    }
    
    long getFlashTimeTill(long millis) {
        return millis - started; 
    }
}
