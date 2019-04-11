package be.geoffrey.fusion

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.w3._2001.xmlschema.Element
import org.w3._2001.xmlschema.Facet
import org.w3._2001.xmlschema.Schema
import java.io.File
import java.io.StringReader
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

private const val NAMESPACE_INDICATION = "namespace_definition"

class SchemaParser {

    fun parse(file: String): TypeDb {

        val asString = File(file).readText()

        var replacedForInterpreting = asString.replace(Regex("xmlns(:?)(.*?)=\"(.*?)\"")) {
            "${it.groupValues[0]} xmlns_hack${it.groupValues[1]}${it.groupValues[2]}=\"${it.groupValues[3]}\""
        }

        replacedForInterpreting = replacedForInterpreting.replaceFirst("schema", "schema xmlns:xmlns_hack=\"$NAMESPACE_INDICATION\"")
        val sw = StringReader(replacedForInterpreting)
        val schema = JAXB.unmarshal(sw, Schema::class.java)

        val knownNamespaces = extractNamespaces(schema)
        val defaultNamespace = knownNamespaces[""]!!

        val typesInFile = mutableListOf<KnownType>()

        for (item in schema.simpleTypeOrComplexTypeOrGroup) {
            if (item is org.w3._2001.xmlschema.TopLevelComplexType) {

                val elementsInComplexType = mutableListOf<be.geoffrey.fusion.Element>()
                if (item.sequence != null) {
                    for (sequenceItem in item.sequence.particle) {
                        if (sequenceItem is JAXBElement<*>) {

                            val actualEntry = sequenceItem.value
                            if (actualEntry is Element) {
                                elementsInComplexType.add(Element(actualEntry.name, QName(actualEntry.type!!.namespaceURI, actualEntry.type!!.localPart))
                                )
                            }
                        }
                    }
                }

                val myType = ComplexType(QName(defaultNamespace, item.name!!), elementsInComplexType)
                typesInFile.add(myType)

            } else if (item is org.w3._2001.xmlschema.SimpleType) {

                val name = item.name
                val base = item.restriction.base

                val restrictions = mutableListOf<Restriction>()

                for (facetJaxbElement in item.restriction.facets) {
                    if (facetJaxbElement is JAXBElement<*> && facetJaxbElement.name.localPart == "enumeration") {
                        val value = (facetJaxbElement.value as Facet).value
                        restrictions.add(EnumRestriction(value))
                    } else if (facetJaxbElement is JAXBElement<*> && facetJaxbElement.name.localPart == "minLength") {
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

    private fun extractNamespaces(schema: Schema): HashMap<String, String> {
        val foundNamespaces = hashMapOf<String, String>()
        schema.otherAttributes.forEach { name, value ->
            if (name.namespaceURI == NAMESPACE_INDICATION) {
                foundNamespaces[name!!.localPart] = value
            } else if (name.localPart == "xmlns_hack") {
                foundNamespaces[""] = value
            }
        }

        foundNamespaces.putIfAbsent("", "")
        return foundNamespaces
    }
}

class Testing {

    /*

    TODO list

    - [ ] Inheritance
    - [ ] Internal complex type should be added as a complex type with a random name
    - [ ] Add all possible restrictions
    - [ ] formDefault test
    - [ ] test xmlns with single quotes

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

    @Test
    fun testNamespaceResolution() {

        val parser = SchemaParser()
        val typeDb = parser.parse("src/test/resources/namespace_shizzle/defined_namespaces.xsd")

        assertThat(typeDb.getType(QName("", "Hoi"))).isEqualTo(
                ComplexType(QName("", "Hoi"),
                        listOf(
                                Element("Ns1", QName("namespace1", "woep")),
                                Element("Ns2", QName("namespace2", "woep"))
                        )))
    }

    @Test
    fun testNamespaceResolutionXmlns() {

        val parser = SchemaParser()
        val typeDb = parser.parse("src/test/resources/namespace_shizzle/xmlns_testing.xsd")

        assertThat(typeDb.getType(QName("DefaultNamespace", "Hoi"))).isEqualTo(
                ComplexType(QName("DefaultNamespace", "Hoi"),
                        listOf(
                                Element("Ns1", QName("http://www.w3.org/2001/XMLSchema", "string"))
                        )))
    }

}