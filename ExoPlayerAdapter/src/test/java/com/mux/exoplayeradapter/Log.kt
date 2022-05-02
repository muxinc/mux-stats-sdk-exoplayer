package com.mux.exoplayeradapter

import java.lang.Exception

fun log(tag: String = "\t", message: String, ex: Exception? = null) {
  println("$tag :: $message")
  ex?.let {
    print(it)
    println()
  }
}
