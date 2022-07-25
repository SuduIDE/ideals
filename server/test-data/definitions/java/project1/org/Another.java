package org;

class Another {
    private int elem;

    public Another(int elem) {
        this.elem = elem;
    }

    public Another(int coordX, int coordY) {
        this.elem = coordX + coordY;
        Main.foo();
    }

    public int get() { return elem; }
}