package com;

// aaa
// foo()
// Inner1

public class TestClass {
  public static class Inner1 {
    public static class Inner2 {
    }
  }

  public final int aaa;

  public void foo() {}

  public TestClass(int aaa) {
    this.aaa = aaa;
    foo();
  }
}