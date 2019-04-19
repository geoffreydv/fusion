package be.geoffrey.fusion

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/*
 TODO more
    - meer variatie toevoegen in generatie
 */

class XmlRenderingTests {

    @Test
    fun testRenderingAString() {

        val blocks = XmlBuildingBlocks()
        val output = XmlRenderer(blocks).render(TopLevelElement(QName("shwoep", "MyName"), QName("http://www.w3.org/2001/XMLSchema", "string")))
        assertThat(output).isEqualTo("""<MyName xmlns="shwoep">string</MyName>""")
    }

    @Test
    fun testComplexTypeWithChildren() {

        val blocks = XmlBuildingBlocks()
        blocks.add(GroupOfSimpleFields(QName("shwoep", "SomeType"), listOf(
                Element("FieldOne", QName("http://www.w3.org/2001/XMLSchema", "string")),
                Element("FieldTwo", QName("http://www.w3.org/2001/XMLSchema", "string"))
        )))

        val output = XmlRenderer(blocks).render(TopLevelElement(QName("shwoep", "MyName"), QName("shwoep", "SomeType")))
        assertThat(output).isEqualToIgnoringWhitespace("""
            <MyName xmlns="shwoep">
                <FieldOne xmlns="">string</FieldOne>
                <FieldTwo xmlns="">string</FieldTwo>
            </MyName>""".trimIndent())
    }
}
