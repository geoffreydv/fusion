package be.geoffrey.fusion

import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ParsingTests {

    @Test
    fun loadingEmptyComplexTypeShouldNotCrash() {

        val parser = XmlSchemaParser()
        val typeDb = parser.readAllElementsAndTypesInFile("src/test/resources/simple_types/empty_complex_type.xsd")

        val emptyName = QName("", "EmptyOne")

        val type = typeDb.getStructure(emptyName)

        Assertions.assertThat(type).isEqualTo(ComplexType(emptyName, listOf()))
    }

    @Test
    fun defaultMinOccursShouldBeOne() {

        val parser = XmlSchemaParser()
        val typeDb = parser.readAllElementsAndTypesInFile("src/test/resources/minoccurs/min_occurs.xsd")

        val emptyName = QName("", "MinOccursSpecificDefault")

        val type = typeDb.getStructure(emptyName)

        Assertions.assertThat(type).isEqualTo(ComplexType(emptyName, listOf(SequenceOfElements(listOf(
                Element("OneDefault", QName("http://www.w3.org/2001/XMLSchema", "string"), minOccurs=1)
        )))))
    }

    @Test
    fun minOccursShouldBeRecognized() {

        val parser = XmlSchemaParser()
        val typeDb = parser.readAllElementsAndTypesInFile("src/test/resources/minoccurs/min_occurs.xsd")

        val targetName = QName("", "MinOccursSpecific")

        val type = typeDb.getStructure(targetName)

        Assertions.assertThat(type).isEqualTo(ComplexType(targetName, listOf(SequenceOfElements(listOf(
                Element("TwoPlease", QName("http://www.w3.org/2001/XMLSchema", "string"), minOccurs=2)
        )))))
    }

    @Test
    fun loadingOneComplexTypeWithSomeBasicFields() {

        val parser = XmlSchemaParser()
        val typeDb = parser.readAllElementsAndTypesInFile("src/test/resources/simple_types/one_simple_type_container.xsd")
        val type = typeDb.getStructure(QName("", "TypesTest"))

        Assertions.assertThat(type).isEqualTo(ComplexType(QName("", "TypesTest"), listOf(SequenceOfElements(listOf(
                Element("AString", QName("http://www.w3.org/2001/XMLSchema", "string")),
                Element("AnInteger", QName("http://www.w3.org/2001/XMLSchema", "integer")),
                Element("ADouble", QName("http://www.w3.org/2001/XMLSchema", "double")
                ))))))
    }

    @Test
    fun loadingSimpleTypeVariations() {

        val parser = XmlSchemaParser()
        val typeDb = parser.readAllElementsAndTypesInFile("src/test/resources/simple_types/custom_simple_type_variations.xsd")

        assertThat(typeDb.getStructure(QName("", "Enum"))).isEqualTo(EnumField(
                QName("", "Enum"),
                listOf("Audi", "BMW")))

        assertThat(typeDb.getStructure(QName("", "VersionNumber"))).isEqualTo(RegexField(
                QName("", "VersionNumber"), "\\d{2}\\.\\d{2}"))
    }

    @Test
    fun testNamespaceResolution() {

        val parser = XmlSchemaParser()
        val typeDb = parser.readAllElementsAndTypesInFile("src/test/resources/namespace_shizzle/defined_namespaces.xsd")

        Assertions.assertThat(typeDb.getStructure(QName("", "Hoi"))).isEqualTo(
                ComplexType(QName("", "Hoi"),
                        listOf(
                                SequenceOfElements(listOf(
                                        Element("Ns1", QName("namespace1", "woep")),
                                        Element("Ns2", QName("namespace2", "woep"))
                                )))))
    }

    @Test
    fun testNamespaceResolutionXmlns() {

        val parser = XmlSchemaParser()
        val typeDb = parser.readAllElementsAndTypesInFile("src/test/resources/namespace_shizzle/targetnamespace_test.xsd")

        Assertions.assertThat(typeDb.getStructure(QName("DefaultNamespace", "SimpleHoi"))).isNotNull

        Assertions.assertThat(typeDb.getStructure(QName("DefaultNamespace", "Hoi"))).isEqualTo(
                ComplexType(QName("DefaultNamespace", "Hoi"),
                        listOf(
                                SequenceOfElements(listOf(
                                        Element("Ns1", QName("http://www.w3.org/2001/XMLSchema", "string"))
                                )))))
    }

    @Test
    fun testSimpleInclude() {
        val parser = XmlSchemaParser()
        val typeDb = parser.readAllElementsAndTypesInFile("src/test/resources/includes/top_level.xsd")

        Assertions.assertThat(typeDb.getStructure(QName("something_else", "IncludedType"))).isNull()
        Assertions.assertThat(typeDb.getStructure(QName("top-level-woep", "IncludedType"))).isNotNull

        Assertions.assertThat(typeDb.getStructure(QName("top-level-woep", "Hallo"))).isEqualTo(
                ComplexType(QName("top-level-woep", "Hallo"),
                        listOf(
                                SequenceOfElements(listOf(
                                        Element("Included", QName("top-level-woep", "IncludedType"))
                                )))))
    }

    @Test
    fun testSimpleImport() {
        val parser = XmlSchemaParser()
        val typeDb = parser.readAllElementsAndTypesInFile("src/test/resources/includes/importer.xsd")

        assertThat(typeDb.getElement(QName("BowShikaWow", "Root"))).isEqualTo(
                TopLevelElement(QName("BowShikaWow", "Root"), QName("something_else", "IncludedType"))
        )

        assertThat(typeDb.getStructure(QName("something_else", "IncludedType"))).isNotNull
    }

    @Test
    fun testParsingSimpleElement() {
        val parser = XmlSchemaParser()
        val typeDb = parser.readAllElementsAndTypesInFile("src/test/resources/simple_types/single_element.xsd")


        Assertions.assertThat(typeDb.getElement(QName("", "Geoffrey")))
                .isEqualTo(TopLevelElement(QName("", "Geoffrey"), QName("http://www.w3.org/2001/XMLSchema", "string")))
    }

    @Test
    fun testParsingElementWithInlineDefinedComplexType() {

        val parser = XmlSchemaParser()
        val typeDb = parser.readAllElementsAndTypesInFile("src/test/resources/inline_definitions/element_with_complex_type.xsd")

        val inlineType = typeDb.getStructureByPartOfName("foobar", "FoodBar")
        assertThat(inlineType).isNotNull

        Assertions.assertThat(typeDb.getElement(QName("foobar", "FoodBar")))
                .isEqualTo(TopLevelElement(QName("foobar", "FoodBar"), inlineType!!.getQName()))
    }

    @Test
    fun testParsingElementWithInlineDefinedSimpleType() {

        val parser = XmlSchemaParser()
        val typeDb = parser.readAllElementsAndTypesInFile("src/test/resources/inline_definitions/element_with_inline_simple_type.xsd")

        val inlineType = typeDb.getStructureByPartOfName("", "Van")
        assertThat(inlineType).isNotNull
        assertThat(inlineType).isExactlyInstanceOf(IntField::class.java)

        Assertions.assertThat(typeDb.getElement(QName("", "Van")))
                .isEqualTo(TopLevelElement(QName("", "Van"), inlineType!!.getQName()))
    }

    @Test
    fun testParsingElementWithInlineDefinedSimpleType2() {

        val parser = XmlSchemaParser()
        val typeDb = parser.readAllElementsAndTypesInFile("src/test/resources/inline_definitions/element_with_inline_nesting.xsd")

        val generatedType = typeDb.getStructureByPartOfName("", "Van")
        assertThat(generatedType).isNotNull
        assertThat(typeDb.getStructure(QName("", "Wrapper")))
                .isEqualTo(ComplexType(QName("", "Wrapper"), listOf(SequenceOfElements(listOf(
                        Element("Van", generatedType!!.getQName())
                )))))
    }

    @Test
    fun testExtensionOfBaseTypeIsIndicated() {

        val parser = XmlSchemaParser()

        val typeDb = parser.readAllElementsAndTypesInFile("src/test/resources/inheritance/abstract_and_concrete.xsd")

        val string = QName(XMLNS, "string")
        val abstractType = QName("", "AbstractOpdrachtType")
        val concreteOpdracht = QName("", "ConcreteOpdracht")
        val extension2 = QName("", "NogSpecifieker")

        assertThat(typeDb.getStructure(abstractType)).isEqualTo(ComplexType(abstractType, listOf(SequenceOfElements()), true))

        assertThat(typeDb.getStructure(concreteOpdracht)).isEqualTo(ComplexType(concreteOpdracht, listOf(
                SequenceOfElements(listOf(Element("ConcreteOpdrachtFieldOne", string)))
        ), extensionOf = abstractType))

        assertThat(typeDb.getStructure(extension2)).isEqualTo(ComplexType(extension2, listOf(
                SequenceOfElements(listOf(Element("NogSpecifiekerFieldOne", string)))
        ), extensionOf = concreteOpdracht))
    }

    @Test
    fun parsingAChoiceShouldStoreItCorrectly() {

        val parser = XmlSchemaParser()
        val typeDb = parser.readAllElementsAndTypesInFile("src/test/resources/group_types/choice.xsd")

        val testTypeName = QName("ouch", "TestType")

        val type = typeDb.getStructure(testTypeName)

        Assertions.assertThat(type).isEqualTo(ComplexType(testTypeName, listOf(
                ChoiceOfElements(listOf(
                        Element("EitherThis", QName("http://www.w3.org/2001/XMLSchema", "string")),
                        SequenceOfElements(listOf(
                                Element("Hallo", QName("http://www.w3.org/2001/XMLSchema", "int"))
                        )),
                        ChoiceOfElements(listOf(
                                Element("bla", QName("http://www.w3.org/2001/XMLSchema", "string")),
                                SequenceOfElements(listOf(
                                        Element("hey", QName("http://www.w3.org/2001/XMLSchema", "string"))
                                )),
                                Element("HEY", QName("http://www.w3.org/2001/XMLSchema", "int"))
                        )))))))
    }

    @Test
    fun parsingAWsdlShouldFindSchemas() {
        val parser = XmlSchemaParser()
        val typeDb = parser.readAllElementsAndTypesInFile("src/test/resources/wsdl_support/testschema.wsdl")

        assertThat(typeDb.getStructureByPartOfName("http://esb.mow.vlaanderen.be/wsdl/Opdracht-v4.0","JustTesting")).isNotNull
    }
}