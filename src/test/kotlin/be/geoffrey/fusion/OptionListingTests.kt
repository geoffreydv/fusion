package be.geoffrey.fusion

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class OptionListingTests {

    private val STRING = QName("http://www.w3.org/2001/XMLSchema", "string")

    @Test
    fun testNoOptionsForSimpleType() {
        val blocks = XmlBuildingBlocks()

        val output = PossibleOptions(blocks, TopLevelElement(QName("shwoep", "MyName"), STRING))

        assertThat(output.getChoices()).isEmpty()
    }

    @Test
    fun testNoOptionsForRegularComplexType() {

        val blocks = XmlBuildingBlocks()

        val typeName = QName("shwoep", "SomeType")

        blocks.add(ComplexType(typeName, listOf(SequenceOfElements(listOf(
                Element("FieldOne", STRING),
                Element("FieldTwo", STRING)
        )))))

        val output = PossibleOptions(blocks, TopLevelElement(QName("", "Element"), typeName))

        assertThat(output.getChoices()).isEmpty()
    }

    @Test
    fun testImplementationChoicesOnlyOneChoice() {

        val blocks = XmlBuildingBlocks()

        val baseType = QName("", "BaseType")
        val implementation = QName("", "Implementation")

        blocks.add(ComplexType(baseType, listOf(), true))
        blocks.add(ComplexType(implementation, listOf(SequenceOfElements(listOf(Element("SomeField", STRING)))), false, baseType))

        val output = PossibleOptions(blocks, TopLevelElement(QName("", "SomeElement"), baseType))

        assertThat(output.getChoices()).isEmpty()
    }

    @Test
    fun testImplementationChoicesMultipleChanges() {

        val blocks = XmlBuildingBlocks()

        val baseType = QName("", "BaseType")

        val implementation = QName("", "Implementation1")
        val implementation2 = QName("", "Implementation2")

        blocks.add(ComplexType(baseType, listOf(), true))
        blocks.add(ComplexType(implementation, listOf(SequenceOfElements(listOf(Element("SomeField", STRING)))), false, baseType))
        blocks.add(ComplexType(implementation2, listOf(SequenceOfElements(listOf(Element("SomeField", STRING)))), false, baseType))

        val output = PossibleOptions(blocks, TopLevelElement(QName("", "SomeElement"), baseType))

        assertThat(output.getChoices()).contains(ImplementationPath("/SomeElement", listOf(implementation, implementation2)))
    }

    @Test
    fun testImplementationChoicesOneSelected() {

        val blocks = XmlBuildingBlocks()

        val baseType = QName("", "BaseType")
        val implementation = QName("", "Implementation1")
        val implementation2 = QName("", "Implementation2")

        blocks.add(ComplexType(baseType, listOf(), true))
        blocks.add(ComplexType(implementation, listOf(SequenceOfElements(listOf(Element("SomeFieldOne", STRING)))), false, baseType))
        blocks.add(ComplexType(implementation2, listOf(SequenceOfElements(listOf(Element("SomeFieldTwo", STRING)))), false, baseType))

        println("HEY")

        val output = PossibleOptions(blocks, TopLevelElement(QName("", "SomeElement"), baseType), Decisions(listOf(
                ImplementationDecision("/SomeElement", implementation2)
        )))

        assertThat(output.getChoices()).isEmpty()
    }

//    @Test
//    fun testProvidingRegexValueRenderingChoice() {
//
//        val blocks = XmlBuildingBlocks()
//        val versienummerQName = QName("shwoep", "VersieNummer")
//
//        blocks.add(RegexField(versienummerQName, "\\d{2}"))
//
//        val output = XmlRenderer(blocks).render(
//                TopLevelElement(QName("shwoep", "Nummer"), versienummerQName),
//                RenderingConfig(listOf(RegexValueForType(versienummerQName, "38")))
//        )
//
//        assertThat(output).isEqualTo("""<Nummer xmlns="shwoep">38</Nummer>""")
//    }
//
//
//    @Test
//    fun testMinOccursIsAccountedFor() {
//
//        val blocks = XmlBuildingBlocks()
//        blocks.add(ComplexType(QName("shwoep", "SomeType"), listOf(SequenceOfElements(listOf(
//                Element("FieldOne", QName("http://www.w3.org/2001/XMLSchema", "string"), minOccurs = 2)
//        )))))
//
//        val output = XmlRenderer(blocks).render(TopLevelElement(QName("shwoep", "MyName"), QName("shwoep", "SomeType")))
//        assertThat(output).isEqualToIgnoringWhitespace("""
//            <MyName xmlns="shwoep">
//                <FieldOne xmlns="">string</FieldOne>
//                <FieldOne xmlns="">string</FieldOne>
//            </MyName>""".trimIndent())
//    }
//
//
//
//    @Test
//    fun testCopyingFieldsFromParentClasses() {
//        val blocks = XmlBuildingBlocks()
//
//        val string = QName(XMLNS, "string")
//        val baseType = QName("", "BaseType")
//        val implementation = QName("", "Implementation")
//        val moreSpecific = QName("", "MoreSpecific")
//
//        blocks.add(ComplexType(baseType, listOf(SequenceOfElements(listOf(
//        ))), true))
//
//        blocks.add(ComplexType(implementation, listOf(SequenceOfElements(listOf(
//                Element("ImplField", string)
//        ))), false, baseType))
//
//        blocks.add(ComplexType(moreSpecific, listOf(SequenceOfElements(listOf(
//                Element("SpecificField", string)
//        ))), false, implementation))
//
//        val output = XmlRenderer(blocks).render(TopLevelElement(QName("", "SomeElement"), moreSpecific))
//        assertThat(output).isEqualToIgnoringWhitespace("""
//            <SomeElement>
//                <ImplField>string</ImplField>
//                <SpecificField>string</SpecificField>
//            </SomeElement>
//            """)
//    }
//
//    @Test
//    fun testRecursionBreaking() {
//
//        val blocks = XmlBuildingBlocks()
//
//        val recursingTypeName = QName("", "RecursingType")
//        blocks.add(ComplexType(recursingTypeName, listOf(SequenceOfElements(listOf(
//                Element("JustSomeRandomField", QName(XMLNS, "string")),
//                Element("TheCurseOfRecursalot", recursingTypeName)
//        )))))
//
//        val output = XmlRenderer(blocks).render(TopLevelElement(QName("", "TestElement"), recursingTypeName))
//        assertThat(output).isEqualToIgnoringWhitespace("""
//            <TestElement>
//                <JustSomeRandomField>string</JustSomeRandomField>
//                <TheCurseOfRecursalot>
//                    <JustSomeRandomField>string</JustSomeRandomField>
//                </TheCurseOfRecursalot>
//            </TestElement>
//        """)
//    }
}