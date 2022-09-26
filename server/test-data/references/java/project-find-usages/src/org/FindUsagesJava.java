package org;

class FindUsagesJava {
    private static int x;

    public static void f</cursor>oo() {}

    public static void main() {
        int z = 1;
        int c = z + x;

        </target id='foo'>foo</>();

        org.Another m = new org.Another(10, 32);
        com.Another mm = new com.Another(11, 42);
    }
}