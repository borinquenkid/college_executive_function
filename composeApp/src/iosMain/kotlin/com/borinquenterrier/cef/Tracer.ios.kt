package com.borinquenterrier.cef

import com.russhwolf.settings.Settings

@Suppress("UNUSED_PARAMETER")
actual fun createTracer(settings: Settings, appEnv: AppEnv): Tracer =
    HttpOtelTracer.create("cef-ios") ?: NoopTracer
