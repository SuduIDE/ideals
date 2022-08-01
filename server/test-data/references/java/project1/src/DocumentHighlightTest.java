class DocumentHighlightTest {
  private int var;
  private org.ForHighlight obj1;
  private com.ForHighlight obj2;
//  private org.ForHighlight obj2;

  private final void foo() {
//    foo();
    obj = new org.ForHighlight(1, 2);
  }

  public DocumentHighlightTest() {
//    var = 100;
    var = 10;
    foo();
  }
}