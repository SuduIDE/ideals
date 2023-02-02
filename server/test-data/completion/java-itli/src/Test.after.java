class Test {
    void test() {
        for (int ${1:i} = 0; ${1:i} < ${2:list}.size(); ${1:i}++) {
            ${3:Object} ${4:object} = ${5:} ${2:list}.get(${1:i});
            ${0:}
        }
    }
}