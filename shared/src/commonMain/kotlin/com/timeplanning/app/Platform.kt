package com.timeplanning.app

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform