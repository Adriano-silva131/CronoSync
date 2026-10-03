package com.adriano.cronosync.core

/** Número com pelo menos dois dígitos: 7 → "07". Base dos formatadores de tempo de cada modo. */
fun Long.pad2(): String = toString().padStart(2, '0')
