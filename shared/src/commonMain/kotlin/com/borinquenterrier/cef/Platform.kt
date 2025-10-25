package com.borinquenterrier.cef

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform