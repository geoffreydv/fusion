package be.geoffrey.fusion

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class XmlRenderingTests {

    @Test
    fun testRenderingASimpleType() {

        val elementToRender = TopLevelElement(
                QName("shwoep", "MyName"),
                QName("http://www.w3.org/2001/XMLSchema", "string"))

        val typdb = TypeDb(listOf(elementToRender))

        val output = XmlRenderer(typdb).render(elementToRender)

        assertThat(output).isEqualTo("""<MyName xmlns="shwoep"/>""")
    }

}
