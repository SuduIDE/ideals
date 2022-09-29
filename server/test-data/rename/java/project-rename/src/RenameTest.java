import org.TestClass;

public class RenameTest {
  public RenameTest() {
    TestClass test = new TestClass(0);
    TestClass.</cursor newName='Inner111'></rename newName='Inner111'>Inner1</> in1 </n
        >= new TestClass.</cursor newName='Inner111'></rename newName='Inner111'>Inner1</>();
    TestClass.</rename newName='Inner111'>Inner1</>.Inner2 in2 </n
        >= new TestClass.</rename newName='Inner111'>Inner1</>.Inner2();

    com.<rename newName='TestClassNext'>TestClass</> comTest </n
        >= new com.<rename newName='TestClassNext'>TestClass</>(0);
    com.<rename newName='TestClassNext'>TestClass</>.Inner1 comIn1 </n
        >= new com.<rename newName='TestClassNext'>TestClass</>.Inner1();
    com.<rename newName='TestClassNext'>TestClass</>.Inner1.Inner2 comIn2 </n
        >= new com.<rename newName='TestClassNext'>TestClass</>.Inner1.Inner2();

    int bbb = test.</cursor newName='abcd'></rename newName='abcd'>aaa</> + 1;
    test.</cursor newName='fooBar'></rename newName='fooBar'>foo</>();
  }
}