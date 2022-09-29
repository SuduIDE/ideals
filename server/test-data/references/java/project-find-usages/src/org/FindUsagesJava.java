package org;

class FindUsagesJava {
    private static int x;

    public static void </cursor>foo() {}

    public static void main() {
        int z = 1;
        int c = z + x;

        </location id='foo'>foo</>();

        </location id='orgAnother'>org.</cursor>Another</> m = new <location id='orgAnother'>org.Another</>(10, 32);
        com.Another mm = new com.Another(11, 42);
    }
}