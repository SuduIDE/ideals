package org;

public class ForHighlight {
  private int elem;

  public ForHighlight(int elem) {
    this.elem = elem;
  }

  public ForHighlight(int coordX, int coordY) {
    this.elem = coordX + coordY;
  }

  public int get() { return elem; }
}