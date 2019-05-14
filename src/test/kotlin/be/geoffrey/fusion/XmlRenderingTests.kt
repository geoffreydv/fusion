package be.geoffrey.fusion

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class XmlRenderingTests {

    @Test
    fun testRenderingAString() {
        val blocks = XmlBuildingBlocks()
        val output = XmlRenderer(blocks).render(TopLevelElement(QName("shwoep", "MyName"), QName("http://www.w3.org/2001/XMLSchema", "string")))
        assertThat(output).isEqualTo("""<MyName xmlns="shwoep">string</MyName>""")
    }

    @Test
    fun testRenderingAnInt() {
        val blocks = XmlBuildingBlocks()
        val output = XmlRenderer(blocks).render(TopLevelElement(QName("shwoep", "MyName"), QName("http://www.w3.org/2001/XMLSchema", "int")))
        assertThat(output).isEqualTo("""<MyName xmlns="shwoep">1</MyName>""")
    }

    @Test
    fun testProvidingRegexValueRenderingChoice() {

        val blocks = XmlBuildingBlocks()
        val versienummerQName = QName("shwoep", "VersieNummer")

        blocks.add(RegexField(versienummerQName, "\\d{2}"))

        val output = XmlRenderer(blocks).render(
                TopLevelElement(QName("shwoep", "Nummer"), versienummerQName),
                RenderingConfig(listOf(RegexValueForType(versienummerQName, "38")))
        )

        assertThat(output).isEqualTo("""<Nummer xmlns="shwoep">38</Nummer>""")
    }

    @Test
    fun testComplexTypeWithChildren() {

        val blocks = XmlBuildingBlocks()
        blocks.add(ComplexType(QName("shwoep", "SomeType"), listOf(SequenceOfElements(listOf(
                Element("FieldOne", QName("http://www.w3.org/2001/XMLSchema", "string")),
                Element("FieldTwo", QName("http://www.w3.org/2001/XMLSchema", "string"))
        )))))

        val output = XmlRenderer(blocks).render(TopLevelElement(QName("shwoep", "MyName"), QName("shwoep", "SomeType")))
        assertThat(output).isEqualToIgnoringWhitespace("""
            <MyName xmlns="shwoep">
                <FieldOne xmlns="">string</FieldOne>
                <FieldTwo xmlns="">string</FieldTwo>
            </MyName>""".trimIndent())
    }

    @Test
    fun testAbstractTypeDetection() {

        val blocks = XmlBuildingBlocks()

        val baseType = QName("", "BaseType")
        val implementation = QName("", "Implementation")

        blocks.add(ComplexType(baseType, listOf(), true))
        blocks.add(ComplexType(implementation, listOf(), false, baseType))

        val output = XmlRenderer(blocks).render(TopLevelElement(QName("", "SomeElement"), baseType))
        assertThat(output).isEqualToIgnoringWhitespace("""<SomeElement xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="Implementation"/>""")
    }

    @Test
    fun testRenderImplementationsFields() {

        val blocks = XmlBuildingBlocks()

        val string = QName(XMLNS, "string")
        val baseType = QName("", "BaseType")
        val implementation = QName("", "Implementation")

        blocks.add(ComplexType(baseType, listOf(), true))
        blocks.add(ComplexType(implementation, listOf(SequenceOfElements(listOf(
                Element("SomeField", string)
        ))), false, baseType))

        val output = XmlRenderer(blocks).render(TopLevelElement(QName("", "SomeElement"), baseType))
        assertThat(output).isEqualToIgnoringWhitespace("""
            <SomeElement xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:type="Implementation">
                <SomeField>string</SomeField>
            </SomeElement>""")
    }

    @Test
    fun testCopyingFieldsFromParentClasses() {
        val blocks = XmlBuildingBlocks()

        val string = QName(XMLNS, "string")
        val baseType = QName("", "BaseType")
        val implementation = QName("", "Implementation")
        val moreSpecific = QName("", "MoreSpecific")

        blocks.add(ComplexType(baseType, listOf(SequenceOfElements(listOf(
                Element("BaseField", string)
        ))), true))

        blocks.add(ComplexType(implementation, listOf(SequenceOfElements(listOf(
                Element("ImplField", string)
        ))), false, baseType))

        blocks.add(ComplexType(moreSpecific, listOf(SequenceOfElements(listOf(
                Element("SpecificField", string)
        ))), false, implementation))

        val output = XmlRenderer(blocks).render(TopLevelElement(QName("", "SomeElement"), moreSpecific))
        assertThat(output).isEqualToIgnoringWhitespace("""
            <SomeElement>
                <BaseField>string</BaseField>
                <ImplField>string</ImplField>
                <SpecificField>string</SpecificField>
            </SomeElement>
            """)
    }

    @Test
    fun testRecursionBreaking() {

        val blocks = XmlBuildingBlocks()

        val recursingTypeName = QName("", "RecursingType")
        blocks.add(ComplexType(recursingTypeName, listOf(SequenceOfElements(listOf(
                Element("JustSomeRandomField", QName(XMLNS, "string")),
                Element("TheCurseOfRecursalot", recursingTypeName)
        )))))

        val output = XmlRenderer(blocks).render(TopLevelElement(QName("", "TestElement"), recursingTypeName))
        assertThat(output).isEqualToIgnoringWhitespace("""
            <TestElement>
                <JustSomeRandomField>string</JustSomeRandomField>
                <TheCurseOfRecursalot>
                    <JustSomeRandomField>string</JustSomeRandomField>
                </TheCurseOfRecursalot>
            </TestElement>
        """)
    }
}