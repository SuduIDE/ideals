package org;

class DefinitionJava {
    private static int x;

    public static void foo() {}

    public static void main() {
        int z = 1;
        int c = z + x;

        foo();

        org.Another m = new org.Another(10, 32);
        com.Another mm = new com.Another(11, 42);
    }
}