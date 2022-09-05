package org.rri.ideals.server.completions;

import java.util.Objects;

@SuppressWarnings("FieldMayBeFinal")  // fields are set via reflection
final class CompletionItemData {
  private int completionDataVersion;
  private int lookupElementIndex;

  CompletionItemData(int completionDataVersion, int lookupElementIndex) {
    this.completionDataVersion = completionDataVersion;
    this.lookupElementIndex = lookupElementIndex;
  }

  public int getCompletionDataVersion() {
    return completionDataVersion;
  }

  public int getLookupElementIndex() {
    return lookupElementIndex;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) return true;
    if (obj == null || obj.getClass() != this.getClass()) return false;
    var that = (CompletionItemData) obj;
    return this.completionDataVersion == that.completionDataVersion &&
        this.lookupElementIndex == that.lookupElementIndex;
  }

  @Override
  public int hashCode() {
    return Objects.hash(completionDataVersion, lookupElementIndex);
  }

  @Override
  public String toString() {
    return "CompletionResolveData[" +
        "completionDataVersion=" + completionDataVersion + ", " +
        "lookupElementIndex=" + lookupElementIndex + ']';
  }
}
