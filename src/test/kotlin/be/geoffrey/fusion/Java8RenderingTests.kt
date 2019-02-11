package be.geoffrey.fusion

import org.junit.Test

class Java8RenderingTests {

    @Test
    fun renderSimpleString() {

        val kb = KnowledgeBase()
        val str = kb.get("String")

        val output = Java8Renderer().render(str!!)
        println(output)
    }

}
