package be.geoffrey.fusion

import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ParsingTests {

    @Test
    fun loadingOneComplexTypeWithSomeBasicFields() {

        val parser = SchemaParser()
        val typeDb = parser.readAllElementsAndTypesInFile("src/test/resources/simple_types/one_simple_type_container.xsd")
        val type = typeDb.getStructure(QName("", "TypesTest"))

        Assertions.assertThat(type).isEqualTo(GroupOfSimpleFields(QName("", "TypesTest"), listOf(
                Element("AString", QName("http://www.w3.org/2001/XMLSchema", "string")),
                Element("AnInteger", QName("http://www.w3.org/2001/XMLSchema", "integer")),
                Element("ADouble", QName("http://www.w3.org/2001/XMLSchema", "double")
                ))))
    }

    @Test
    fun loadingSimpleTypeVariations() {

        val parser = SchemaParser()
        val typeDb = parser.readAllElementsAndTypesInFile("src/test/resources/simple_types/custom_simple_type_variations.xsd")

        Assertions.assertThat(typeDb.getStructure(QName("", "Enum"))).isEqualTo(EnumField(
                QName("", "Enum"),
                listOf("Audi", "BMW")))
    }

    @Test
    fun testNamespaceResolution() {

        val parser = SchemaParser()
        val typeDb = parser.readAllElementsAndTypesInFile("src/test/resources/namespace_shizzle/defined_namespaces.xsd")

        Assertions.assertThat(typeDb.getStructure(QName("", "Hoi"))).isEqualTo(
                GroupOfSimpleFields(QName("", "Hoi"),
                        listOf(
                                Element("Ns1", QName("namespace1", "woep")),
                                Element("Ns2", QName("namespace2", "woep"))
                        )))
    }

    @Test
    fun testNamespaceResolutionXmlns() {

        val parser = SchemaParser()
        val typeDb = parser.readAllElementsAndTypesInFile("src/test/resources/namespace_shizzle/targetnamespace_test.xsd")

        Assertions.assertThat(typeDb.getStructure(QName("DefaultNamespace", "SimpleHoi"))).isNotNull

        Assertions.assertThat(typeDb.getStructure(QName("DefaultNamespace", "Hoi"))).isEqualTo(
                GroupOfSimpleFields(QName("DefaultNamespace", "Hoi"),
                        listOf(
                                Element("Ns1", QName("http://www.w3.org/2001/XMLSchema", "string"))
                        )))
    }

    @Test
    fun testSimpleInclude() {
        val parser = SchemaParser()
        val typeDb = parser.readAllElementsAndTypesInFile("src/test/resources/includes/top_level.xsd")

        Assertions.assertThat(typeDb.getStructure(QName("something_else", "IncludedType"))).isNull()
        Assertions.assertThat(typeDb.getStructure(QName("top-level-woep", "IncludedType"))).isNotNull

        Assertions.assertThat(typeDb.getStructure(QName("top-level-woep", "Hallo"))).isEqualTo(
                GroupOfSimpleFields(QName("top-level-woep", "Hallo"),
                        listOf(
                                Element("Included", QName("top-level-woep", "IncludedType"))
                        )))
    }

    @Test
    fun testSimpleImport() {
        val parser = SchemaParser()
        val typeDb = parser.readAllElementsAndTypesInFile("src/test/resources/includes/importer.xsd")

        assertThat(typeDb.getElement(QName("BowShikaWow", "Root"))).isEqualTo(
                TopLevelElement(QName("BowShikaWow", "Root"), QName("something_else", "IncludedType"))
        )

        assertThat(typeDb.getStructure(QName("something_else", "IncludedType"))).isNotNull
    }

    @Test
    fun testParsingSimpleElement() {
        val parser = SchemaParser()
        val typeDb = parser.readAllElementsAndTypesInFile("src/test/resources/simple_types/single_element.xsd")


        Assertions.assertThat(typeDb.getElement(QName("", "Geoffrey")))
                .isEqualTo(TopLevelElement(QName("", "Geoffrey"), QName("http://www.w3.org/2001/XMLSchema", "string")))
    }

    @Test
    fun testParsingElementWithInlineDefinedComplexType() {

        val parser = SchemaParser()
        val typeDb = parser.readAllElementsAndTypesInFile("src/test/resources/inline_definitions/element_with_complex_type.xsd")

        val inlineType = typeDb.getStructureByPartOfName("foobar", "FoodBar")
        assertThat(inlineType).isNotNull

        Assertions.assertThat(typeDb.getElement(QName("foobar", "FoodBar")))
                .isEqualTo(TopLevelElement(QName("foobar", "FoodBar"), inlineType!!.getQName()))
    }

    @Test
    fun testParsingElementWithInlineDefinedSimpleType() {

        val parser = SchemaParser()
        val typeDb = parser.readAllElementsAndTypesInFile("src/test/resources/inline_definitions/element_with_inline_simple_type.xsd")

        val inlineType = typeDb.getStructureByPartOfName("", "Van")
        assertThat(inlineType).isNotNull

        Assertions.assertThat(typeDb.getElement(QName("", "Van")))
                .isEqualTo(TopLevelElement(QName("", "Van"), inlineType!!.getQName()))
    }

    @Test
    fun testParsingElementWithInlineDefinedSimpleType2() {

        val parser = SchemaParser()
        val typeDb = parser.readAllElementsAndTypesInFile("src/test/resources/inline_definitions/element_with_inline_nesting.xsd")

        val generatedType = typeDb.getStructureByPartOfName("", "Van")
        assertThat(generatedType).isNotNull
        assertThat(typeDb.getStructure(QName("", "Wrapper")))
                .isEqualTo(GroupOfSimpleFields(QName("", "Wrapper"), listOf(
                        Element("Van", generatedType!!.getQName())
                )))
    }
}