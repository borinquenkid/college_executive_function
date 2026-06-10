package com.borinquenterrier.cef

import kotlinx.serialization.descriptors.*

/** Maps primitive serial kinds to their TypeScript type representations. */
private fun mapPrimitive(kind: SerialKind): String? = when (kind) {
    PrimitiveKind.INT,
    PrimitiveKind.LONG,
    PrimitiveKind.SHORT,
    PrimitiveKind.BYTE,
    PrimitiveKind.FLOAT,
    PrimitiveKind.DOUBLE -> "number"
    PrimitiveKind.BOOLEAN -> "boolean"
    PrimitiveKind.STRING,
    PrimitiveKind.CHAR -> "string"
    else -> null
}

/** Maps collection serial descriptors (List, Map) to their TypeScript representations. */
private fun SerialDescriptor.mapCollection(): String? = when (kind) {
    StructureKind.LIST -> "Array<${getElementDescriptor(0).toTsType()}>"
    StructureKind.MAP -> "Record<string, ${getElementDescriptor(1).toTsType()}>"
    else -> null
}

/** Maps custom classes and enums to their TypeScript type names. */
private fun SerialDescriptor.mapStructure(): String? = when (kind) {
    StructureKind.CLASS -> serialName.substringAfterLast(".").trimEnd('?')
    SerialKind.ENUM -> "string"
    else -> null
}

/** Maps a kotlinx-serialization [SerialDescriptor] to its TypeScript equivalent representation. */
internal fun SerialDescriptor.toTsType(): String {
    val base = mapPrimitive(kind)
        ?: mapCollection()
        ?: mapStructure()
        ?: "unknown"
    return if (isNullable) "$base | null" else base
}


