package org.rri.server.completions;

public class CompletionResolveData {
  public final int resultIndex;
  public final int lookupElementIndex;

  public CompletionResolveData(int resultIndex, int lookupElementIndex) {
    this.resultIndex = resultIndex;
    this.lookupElementIndex = lookupElementIndex;
  }
}
