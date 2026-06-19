package com.borinquenterrier.cef

import com.russhwolf.settings.Settings

actual fun createTracer(settings: Settings): Tracer = NoopTracer
