package org

import com.Class1

enum class Letter {
  A, B
}

interface Interface {
  fun foo(x: Int, str: String): Int;
}

@Special("for test")
open class DocumentSymbol(x: Int) : BaseClass(x), Interface {
  private var x = x;
  private val cls = Class1();

  override fun foo(x: Int, str: String): Int {
    val a = 1
    val cls = null
    val b = true
    return func()
  }

  fun bar(): Int = 42
}

fun buz(a: Int): Int = a + 1