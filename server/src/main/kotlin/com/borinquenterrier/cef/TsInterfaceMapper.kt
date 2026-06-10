package com.borinquenterrier.cef

import kotlinx.serialization.descriptors.*

/** Generates a full TypeScript interface declaration for a CLASS-kind [SerialDescriptor]. */
internal fun SerialDescriptor.toTsInterface(): String {
    require(kind == StructureKind.CLASS) { "Only CLASS descriptors can be converted to TS interfaces" }
    val name = serialName.substringAfterLast(".").trimEnd('?')
    val props = (0 until elementsCount).joinToString("\n") { i ->
        val propName = getElementName(i)
        val propDesc = getElementDescriptor(i)
        val tsType = propDesc.toTsType()
        "  $propName: $tsType;"
    }
    return "export interface $name {\n$props\n}"
}
