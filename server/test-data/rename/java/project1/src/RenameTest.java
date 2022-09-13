import org.TestClass;

public class RenameTest {
  public RenameTest() {
    TestClass test = new TestClass(0);
    TestClass.Inner1 in1 = new TestClass.Inner1();
    TestClass.Inner1.Inner2 in2 = new TestClass.Inner1.Inner2();

    com.TestClass comTest = new com.TestClass(0);
    com.TestClass.Inner1 comIn1 = new com.TestClass.Inner1();
    com.TestClass.Inner1.Inner2 comIn2 = new com.TestClass.Inner1.Inner2();

    int bbb = test.aaa + 1;
    test.foo();
  }
}