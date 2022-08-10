def func():
    pass


def do_twice(f):
    def wrapper_do_twice(*args, **kwargs):
        f(*args, **kwargs)
        f(*args, **kwargs)

    return wrapper_do_twice
