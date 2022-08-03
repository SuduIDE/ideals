package org;

import com.Class1;

public class DocumentSymbol extends BaseClass implements Interface {
  private int x;
  private static final Class1 cls = new Class1();

  public DocumentSymbol(int x) {
    this.x = x;
  }

  @Override
  public int foo(int x, String str) {
    int a = 1;
    String project = "lsp";
    Class cls = null;
    Boolean b = true;
    func();
  }

  public interface EntryInter {
    void foo();
  }

  public enum Litter {
    A, B;
  }
}