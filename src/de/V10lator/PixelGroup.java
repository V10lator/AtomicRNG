package de.V10lator;

class PixelGroup {

    private final int[] rgb = new int[3];
    
    PixelGroup(int init) {
        for(int i = 0; i < 3; i++)
            rgb[i] = init;
    }
    
    
    int getStrength(int[] rgb) {
        int ret = 0;
        int[] adjust = new int[3];
        boolean active = false;
        for(int i = 0; i < 3; i++) {
            if(rgb[i] > this.rgb[i]) {
                if(rgb[i] > this.rgb[i] + (this.rgb[i] / 100 * 10)) {
                    active = true;
                    ret += rgb[i] - this.rgb[i];
                    adjust[i] = rgb[i] > this.rgb[i] ? rgb[i] : this.rgb[i];
                } else if(rgb[i] < this.rgb[i] && AtomicRNG.rand.nextInt(1000) < 5)
                    adjust[i] = this.rgb[i] - ((this.rgb[i] - rgb[i]) / 2);
            }
        }
        if(!active) { // Update filter
            for(int i = 0; i < 3; i++) {
                this.rgb[i] = adjust[i];
            }
            return 0;
        }
        return ret;
    }
}
