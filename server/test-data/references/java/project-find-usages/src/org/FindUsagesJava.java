package org;

class FindUsagesJava {
    private static int x;

    public static void </cursor id='foo'/>foo() {}

    public static void main() {
        int z = 1;
        int c = z + x;

        </location id='foo'>foo</>();

        </location id='orgAnother'>org.</cursor id='orgAnother'/>Another</> m = new </location id='orgAnother'>org.Another</>(10, 32);
        com.Another mm = new com.Another(11, 42);
    }
}