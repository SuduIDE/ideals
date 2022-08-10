import class1
import class2 as cls2
from funcs import *

p = "lsp"
b = True
func()


class Document_symbol(cls2.Class2):
    def __init__(self):
        self.x = 1
        self.__CONST = None

    def foo(self, x, y):
        pass

    def bar(self, *args, **kwargs):
        pass


@do_twice
def foo_bar(x, /, y, *, z):
    return class1.Class1()
