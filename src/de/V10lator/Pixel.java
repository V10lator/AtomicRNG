package de.V10lator;

class Pixel {

    int x, y, power;
    private int last;
    
    Pixel(int x, int y, int power) {
        this.x = x;
        this.y = y;
        this.power = power;
        this.last = power;
    }
    
    boolean charge(int x, int y, int power) {
        this.power += power;
        if(this.last < power) {
            this.x = x;
            this.y = y;
            return true;
        }
        return false;
    }
}
