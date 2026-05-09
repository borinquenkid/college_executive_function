package com.borinquenterrier.cef

import okio.FileSystem

actual fun getFileSystem(): FileSystem = FileSystem.SYSTEM
