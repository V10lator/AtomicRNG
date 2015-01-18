package de.V10lator;

import java.util.ArrayList;

class Pixel {

    int x, y, power;
    private int last;
    final long found;
    ArrayList<Pixel> storage = new ArrayList<Pixel>(); // TODO: Ugly hack for debugging.
    
    Pixel(int x, int y, int power, long found) {
        this.x = x;
        this.y = y;
        this.power = power;
        this.last = power;
        this.found = found;
    }
    
    boolean charge(int x, int y, int power) {
        storage.add(new Pixel(x, y, power, 9L));
        this.power += power;
        if(this.last < power) {
            this.x = x;
            this.y = y;
            this.last = power;
            return true;
        }
        return false;
    }
}
