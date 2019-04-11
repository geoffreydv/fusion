package be.geoffrey.fusion

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.w3._2001.xmlschema.*
import org.w3._2001.xmlschema.Element
import java.io.FileInputStream
import javax.xml.bind.JAXB
import javax.xml.bind.JAXBElement

data class QName(val namespace: String, val name: String)

interface KnownType {
    fun getQName(): QName
}

interface PossiblePartOfGroup

data class Element(val name: String,
                   val type: QName) : PossiblePartOfGroup

interface Restriction

data class MinLengthRestriction(val minLength: Int) : Restriction

data class EnumRestriction(val value: String) : Restriction

data class SimpleType(private val name: QName,
                      private val baseType: QName,
                      val restrictions: List<Restriction>) : KnownType {

    override fun getQName(): QName {
        return name
    }
}

data class ComplexType(val name: QName,
                       val fields: List<be.geoffrey.fusion.Element>) : PossiblePartOfGroup, KnownType {

    override fun getQName(): QName {
        return name
    }
}

class TypeDb(knownTypes: List<KnownType>) {

    private val knownTypesAsMap = HashMap<QName, KnownType>()

    init {
        for (knownType in knownTypes) {
            knownTypesAsMap[knownType.getQName()] = knownType
        }
    }

    fun getType(type: QName): KnownType? {
        return knownTypesAsMap[type]
    }
}

class SchemaParser {

    fun parse(file: String): TypeDb {
        val schema = JAXB.unmarshal(FileInputStream(file), Schema::class.java)

        val typesInFile = mutableListOf<KnownType>()

        for (item in schema.simpleTypeOrComplexTypeOrGroup) {
            if (item is org.w3._2001.xmlschema.TopLevelComplexType) {

                val elementsInComplexType = mutableListOf<be.geoffrey.fusion.Element>()

                for (sequenceItem in item.sequence.particle) {
                    if (sequenceItem is JAXBElement<*>) {

                        val actualEntry = sequenceItem.value
                        if (actualEntry is Element) {
                            elementsInComplexType.add(
                                    Element(actualEntry.name,
                                            QName("http://www.w3.org/2001/XMLSchema", actualEntry.type!!.localPart))
                            )
                        }
                    }

                    val myType = ComplexType(QName("", item.name!!), elementsInComplexType)
                    typesInFile.add(myType)
                }
            } else if (item is org.w3._2001.xmlschema.SimpleType) {

                val name = item.name
                val base = item.restriction.base

                val restrictions = mutableListOf<Restriction>()

                for (facetJaxbElement in item.restriction.facets) {
                    if(facetJaxbElement is JAXBElement<*> && facetJaxbElement.name.localPart == "enumeration") {
                        val value = (facetJaxbElement.value as Facet).value
                        restrictions.add(EnumRestriction(value))
                    } else if(facetJaxbElement is JAXBElement<*> && facetJaxbElement.name.localPart == "minLength") {
                        val minLength = (facetJaxbElement.value as Facet).value
                        restrictions.add(MinLengthRestriction(Integer.valueOf(minLength)))
                    }
                }

                typesInFile.add(
                        SimpleType(QName("", name),
                                QName("http://www.w3.org/2001/XMLSchema", base.localPart),
                                restrictions))
            }
        }

        return TypeDb(typesInFile)
    }
}

class Testing {

    /*

    TODO list

    - [ ] Inheritance
    - [ ] Internal complex type should be added as a complex type with a random name
    - [ ] Add all possible restrictions

     */

    @Test
    fun loadingOneComplexTypeWithSomeBasicFields() {

        val parser = SchemaParser()
        val typeDb = parser.parse("src/test/resources/simple_types/one_simple_type_container.xsd")
        val type = typeDb.getType(QName("", "TypesTest"))

        assertThat(type).isEqualTo(ComplexType(QName("", "TypesTest"), listOf(
                Element("AString", QName("http://www.w3.org/2001/XMLSchema", "string")),
                Element("AnInteger", QName("http://www.w3.org/2001/XMLSchema", "integer")),
                Element("ADouble", QName("http://www.w3.org/2001/XMLSchema", "double")
                ))))
    }

    @Test
    fun loadingSimpleTypeVariations() {

        val parser = SchemaParser()
        val typeDb = parser.parse("src/test/resources/simple_types/custom_simple_type_variations.xsd")

        assertThat(typeDb.getType(QName("", "MinLengthNumber"))).isEqualTo(SimpleType(
                QName("", "MinLengthNumber"),
                QName("http://www.w3.org/2001/XMLSchema", "int"),
                listOf(
                        MinLengthRestriction(10)
                )))

        assertThat(typeDb.getType(QName("", "Enum"))).isEqualTo(SimpleType(
                QName("", "Enum"),
                QName("http://www.w3.org/2001/XMLSchema", "string"),
                listOf(
                        EnumRestriction("Audi"),
                        EnumRestriction("BMW")
                )))
    }

}