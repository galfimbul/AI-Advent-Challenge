package com.example.aiadventchallenge.docindex

fun normalizeNewlines(text: String): String =
  text.replace("\r\n", "\n").replace('\r', '\n')
