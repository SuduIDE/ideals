</rename RenameFile newName='TestClassNext'>
package com;

// aaa
// foo()
// Inner1

public class <rename newName='TestClassNext'>TestClass</> {
  public static class Inner1 {

    public Inner1() {
      int x = 0;
    }
    
    public static class Inner2 {
    }
  }

  public final int aaa;

  public void foo() {}

  public <rename newName='TestClassNext'>TestClass</>(int aaa) {
    this.aaa = aaa;
    foo();
  }
}