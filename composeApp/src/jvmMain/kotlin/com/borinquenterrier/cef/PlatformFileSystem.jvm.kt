package com.borinquenterrier.cef

import okio.FileSystem

@Suppress("SameReturnValue")
actual fun getFileSystem(): FileSystem = FileSystem.SYSTEM
