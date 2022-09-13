import file2
from file1 import *

test = test_class(0)
in1 = test_class.inner1()
in2 = test_class.inner1.inner2()

file2.test = file2.test_class(0)
file2.in1 = file2.test_class.inner1()
file2.in2 = file2.test_class.inner1.inner2()

curr = test.cur + 1
test.foo()
