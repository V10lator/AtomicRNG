package de.V10lator;

class Pixelstorage {
    private final int[] maxRGB;
    private final int[] lastRGB;
    final int[] power = { 0, 0, 0 };
    private final short cooldown[] = { 0, 0, 0 };
    private long started = 0L;
    boolean active = false;
    
    Pixelstorage(int[] rgb, int[] maxRGB) {
        this.maxRGB = maxRGB;
        lastRGB = rgb;
    }
    
    Pixelstorage update(int rgb[]) {
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
                    if(--cooldown[i] == 1)
                        maxRGB[i] = lastRGB[i];
                    else
                        power[i] += rgb[i];
                }
            } else {
                if(cooldown[i] > 0) {
                    active = true;
                    lastRGB[i] = rgb[i];
                    cooldown[i] = 0;
                }
            }
        }
        active = false;
        return this;
    }
    
    long getFlashTimeTill(long millis) {
        return millis - started; 
    }
}
