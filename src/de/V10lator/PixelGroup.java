package de.V10lator;

class PixelGroup {

    private final int[] bgr = new int[3];
    
    PixelGroup(int init) {
        for(int i = 0; i < 3; i++)
            bgr[i] = init;
    }
    
    
    int getStrength(int[] bgr) {
        int ret = 0;
        int[] adjust = { -1, -1, -1 };
        boolean active = false;
        for(int i = 0; i < 3; i++) {
            if(bgr[i] > this.bgr[i]) {
                if(bgr[i] > this.bgr[i] * 1.75f) {
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
}
