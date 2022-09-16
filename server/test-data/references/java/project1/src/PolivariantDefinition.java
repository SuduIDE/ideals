public class PolivariantDefinition {
  private int foo(String s) {
    return s.length();
  }

  private int foo(int x) {
    return x;
  }

  public PolivariantDefinition() {
    foo(a);
  }
}