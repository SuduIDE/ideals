class DocumentHighlightTest {
  private int var;
  private org.Another obj1;
  private com.Another obj2;
//  private org.Another obj2;

  private final void foo() {
//    foo();
    obj = new org.Another(1, 2);
  }

  public DocumentHighlightTest() {
//    var = 100;
    var = 10;
    foo();
  }
}