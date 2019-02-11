package be.geoffrey.fusion

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class Java8RenderingTests {

    @Test
    fun renderSimpleString() {

        val kb = KnowledgeBase()
        val str = kb.get("String")

        assertThat(buildRenderer().render(str!!)).isEqualTo("""
            String tmp = "TestString";
        """.trimIndent())
    }

    private fun buildRenderer() = Java8Renderer(
            variableName = "tmp",
            stringTemplate = "TestString")

}
