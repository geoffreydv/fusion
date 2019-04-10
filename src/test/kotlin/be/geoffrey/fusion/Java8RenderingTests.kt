package be.geoffrey.fusion

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class Java8RenderingTests {

    @Test
    fun renderSimpleString() {

        val kb = KnowledgeBase()
        val str = kb.getStructure("String")

        assertThat(buildRenderer().render(str!!)).isEqualTo("""
            String tmp = "TestString";
        """.trimIndent())
    }

    @Test
    fun renderTypeWithFields() {

        val kb = KnowledgeBase()

        val struct = ComplexStructure("Address", listOf(
                Field("street", kb.getStructure("String")!!),
                Field("city", kb.getStructure("String")!!))
        )

        assertThat(buildRenderer().render(struct)).isEqualTo("""
            String tmp = "TestString";
        """.trimIndent())
    }

    private fun buildRenderer() = Java8Renderer(
            variableName = "tmp",
            stringTemplate = "TestString")

}
