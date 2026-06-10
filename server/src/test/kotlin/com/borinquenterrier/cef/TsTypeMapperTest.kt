package com.borinquenterrier.cef

import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TsTypeMapperTest {

    @Test
    fun testPrimitiveMapping() {
        val desc = serializer<Boolean>().descriptor
        assertEquals("boolean", desc.toTsType())
    }

    @Test
    fun testNullablePrimitiveMapping() {
        val desc = serializer<String?>().descriptor
        assertEquals("string | null", desc.toTsType())
    }

    @Test
    fun testListMapping() {
        val desc = serializer<List<Int>>().descriptor
        assertEquals("Array<number>", desc.toTsType())
    }

    @Test
    fun testMapMapping() {
        val desc = serializer<Map<String, Double>>().descriptor
        assertEquals("Record<string, number>", desc.toTsType())
    }

    @Test
    fun testInterfaceGeneration() {
        val desc = serializer<RemoteCalendarMetadata>().descriptor
        val tsInterface = desc.toTsInterface()
        assertTrue(tsInterface.contains("export interface RemoteCalendarMetadata"))
        assertTrue(tsInterface.contains("id: string"))
        assertTrue(tsInterface.contains("name: string"))
    }
}
