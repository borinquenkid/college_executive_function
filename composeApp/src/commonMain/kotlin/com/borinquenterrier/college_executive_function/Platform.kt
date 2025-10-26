package com.borinquenterrier.college_executive_function

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform