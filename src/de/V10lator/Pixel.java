package de.V10lator;

class Pixel {

    int x, y, power;
    private int last;
    final long found;
    
    Pixel(int x, int y, int power, long found) {
        this.x = x;
        this.y = y;
        this.power = power;
        this.last = power;
        this.found = found;
    }
    
    boolean charge(int x, int y, int power) {
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
