package be.geoffrey.fusion

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/*
 TODO more
    - meer variatie toevoegen in generatie
 */

class XmlRenderingTests {

    @Test
    fun testRenderingASimpleType() {

        val elementToRender = TopLevelElement(
                QName("shwoep", "MyName"),
                QName("http://www.w3.org/2001/XMLSchema", "string"))

        val typdb = TypeDb(listOf(elementToRender))

        val renderingOptions = mapOf(
                Pair(QName("http://www.w3.org/2001/XMLSchema", "string"), { _: QName -> "Goeiendag!" })
        )

        val output = XmlRenderer(typdb).render(elementToRender, renderingOptions)

        assertThat(output).isEqualTo("""<MyName xmlns="shwoep">Goeiendag!</MyName>""")
    }

    @Test
    fun testRenderingASimpleTypeWithNoRendering() {

        val elementToRender = TopLevelElement(
                QName("shwoep", "MyName"),
                QName("http://www.w3.org/2001/XMLSchema", "string"))

        val typdb = TypeDb(listOf(elementToRender))

        val output = XmlRenderer(typdb).render(elementToRender, hashMapOf())

        assertThat(output).isEqualTo("""<MyName xmlns="shwoep"/>""")
    }

    @Test
    fun testComplexTypeWithChildren() {

        val elementToRender = TopLevelElement(
                QName("shwoep", "MyName"),
                QName("shwoep", "SomeType"))

        val structure = ComplexType(QName("shwoep", "SomeType"), listOf(
                Element("FieldOne", QName("http://www.w3.org/2001/XMLSchema", "string")),
                Element("FieldTwo", QName("http://www.w3.org/2001/XMLSchema", "string"))
        ))

        val typdb = TypeDb(listOf(elementToRender, structure))

        val renderingOptions = mapOf(
                Pair(QName("http://www.w3.org/2001/XMLSchema", "string"), { _: QName -> "Goeiendag!" })
        )

        val output = XmlRenderer(typdb).render(elementToRender, renderingOptions)

        assertThat(output).isEqualTo("""<MyName xmlns="shwoep"/>""")
    }
}
