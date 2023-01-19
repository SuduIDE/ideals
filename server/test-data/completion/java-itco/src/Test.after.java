class Test {
    void test() {
        for (${5:java.util.Iterator} ${1:iterator} = ${2:collection}.iterator(); ${1:iterator}.hasNext(); ) {
            ${3:Object} ${4:next} = ${6:} ${1:iterator}.next();
            ${0:}
        }
    }
}