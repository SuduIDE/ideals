package org;

public class Another {
    private int elem;

    public Another(int elem) {
        this.elem = elem;
    }

    public Another(int coordX, int coordY) {
        this.elem = coordX + coordY;
        DefinitionJava.foo();
    }

    public int get() { return elem; }
}