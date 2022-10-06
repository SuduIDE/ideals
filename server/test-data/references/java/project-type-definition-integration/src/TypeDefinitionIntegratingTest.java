class TypeDefinitionIntegratingTest {
  private class </target id='Another'>Another</> {
    int x, y;
    public Another(int x, int y) {
      this.x = x;
      this.y = y;
    }
  }

  public TypeDefinitionIntegratingTest() {
    Another a = new Another(1, 2);
    Another b = </origin id='Another'>a</>;
  }
}