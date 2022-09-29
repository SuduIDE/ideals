class DocumentHighlightTest {
  private int </cursor></documentHighlight id='var' type='Text'>var</>;
  private </cursor></documentHighlight id='ForHighlight' type='Read'>org.ForHighlight</> obj1;
  private com.ForHighlight obj2;
//  private org.ForHighlight obj2;

  private final void <documentHighlight id='foo' type='Text'>foo</>() {
//    foo();
    obj = new </documentHighlight id='ForHighlight' type='Read'>org.ForHighlight</>(1, 2);
  }

  public DocumentHighlightTest() {
//    var = 100;
    </documentHighlight id='var' type='Write'>var</> = 10;
    </cursor></documentHighlight id='foo' type='Text'>foo</>();
  }
}