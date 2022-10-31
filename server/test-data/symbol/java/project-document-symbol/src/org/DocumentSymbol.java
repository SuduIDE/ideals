package org;

import com.Class1;

public class </docsym name='DocumentSymbol' kind='Class'>DocumentSymbol</> extends BaseClass implements Interface {
  private int </docsym name='x' kind='Field' father='DocumentSymbol'>x</>;
  private static final Class1 </docsym name='cls' kind='Constant' father='DocumentSymbol'>cls</> = new Class1();

  public </docsym name='DocumentSymbol(int)' kind='Constructor' father='DocumentSymbol'>DocumentSymbol(int </docsym name='x' kind='Variable' father='DocumentSymbol(int)'>x</>) {
    this.x = x;
  }

  @Override
  public int </docsym name='foo(int, String)' kind='Method' father='DocumentSymbol'>foo</>(</n
        >int </docsym name='xx' kind='Variable' father='foo(int, String)'>xx</>, </n
        >String <docsym name='str' kind='Variable' father='foo(int, String)'>str</>) {
    int </docsym name='a' kind='Variable' father='foo(int, String)'>a</> = 1;
    String </docsym name='project' kind='Variable' father='foo(int, String)'>project</> = "lsp";
    Class </docsym name='cls' kind='Variable' father='foo(int, String)'>cls</> = null;
    Boolean </docsym name='b' kind='Variable' father='foo(int, String)'>b</> = true;
    func();
  }

  public interface </docsym name='EntryInter' kind='Interface' father='DocumentSymbol'>EntryInter</> {
    void </docsym name='foo()' kind='Method' father='EntryInterface'>foo</>();
  }

  public enum </docsym name='Letter' kind='Enum' father='DocumentSymbol'>Letter</> {
    </docsym name='A' kind='EnumMember' father='Letter'>A</>, </docsym name='B' kind='EnumMember' father='Letter'>B</>;
  }
}