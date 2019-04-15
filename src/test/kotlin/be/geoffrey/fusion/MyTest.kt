package be.geoffrey.fusion

import be.geoffrey.fusion.ContentType.DEFINITION
import be.geoffrey.fusion.ContentType.ELEMENT
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.w3._2001.xmlschema.Element
import org.w3._2001.xmlschema.Facet
import org.w3._2001.xmlschema.Include
import org.w3._2001.xmlschema.Schema
import java.io.File
import java.io.StringReader
import javax.xml.bind.JAXB
import javax.xml.bind.JAXBElement

data class QName(val namespace: String, val name: String)

enum class ContentType {
    ELEMENT,
    DEFINITION
}

interface Indexable {
    fun getQName(): QName

    fun getContentType(): ContentType
}

interface PossiblePartOfGroup

// Maybe add "top-level" element?

data class Element(val name: String,
                   val type: QName) : PossiblePartOfGroup

data class TopLevelElement(val name: String, val type: QName) : Indexable {

    override fun getQName(): QName {
        return type
    }

    override fun getContentType(): ContentType {
        return ELEMENT
    }
}

interface Restriction

data class MinLengthRestriction(val minLength: Int) : Restriction

data class EnumRestriction(val value: String) : Restriction

data class SimpleType(private val name: QName,
                      private val baseType: QName,
                      val restrictions: List<Restriction>) : Indexable {

    override fun getContentType(): ContentType {
        return DEFINITION
    }

    override fun getQName(): QName {
        return name
    }
}

data class ComplexType(val name: QName,
                       val fields: List<be.geoffrey.fusion.Element>) : PossiblePartOfGroup, Indexable {

    override fun getContentType(): ContentType {
        return DEFINITION
    }

    override fun getQName(): QName {
        return name
    }
}

class TypeDb(entries: Collection<Indexable> = listOf()) {

    private val knownTypesAsMap = hashMapOf<ContentType, HashMap<QName, Indexable>>(
            Pair(ELEMENT, hashMapOf()),
            Pair(DEFINITION, hashMapOf()))

    init {
        addEntries(entries)
    }

    fun addEntries(entries: Collection<Indexable>) {
        for (entry in entries) {
            knownTypesAsMap[entry.getContentType()]!![entry.getQName()] = entry
        }
    }

    fun addEntries(other: TypeDb) {
        for (value in other.knownTypesAsMap.values) {
            addEntries(value.values)
        }
    }

    fun getEntry(name: QName, type: ContentType = DEFINITION): Indexable? {
        return knownTypesAsMap[type]!![name]
    }

    fun getEntryByPartOfName(namespace: String,
                             partOfName: String,
                             contentType: ContentType): List<Indexable> {

        val results = mutableListOf<Indexable>()

        knownTypesAsMap[contentType]!!.forEach { (_, type) ->
            if (type.getQName().namespace == namespace && type.getQName().name.contains(partOfName)) {
                results.add(type)
            }
        }

        return results
    }
}

private const val NAMESPACE_INDICATION = "namespace_definition"

class SchemaParser {

    fun readAllElementsAndTypesInFile(schemaFile: String,
                                      targetNamespaceOverride: String? = null): TypeDb {

        val knownTypes = TypeDb()

        val asString = File(schemaFile).readText()

        var replacedForInterpreting = asString.replace(Regex("xmlns(:?)(.*?)=\"(.*?)\"")) {
            "${it.groupValues[0]} xmlns_hack${it.groupValues[1]}${it.groupValues[2]}=\"${it.groupValues[3]}\""
        }

        replacedForInterpreting = replacedForInterpreting.replaceFirst("schema", "schema xmlns:xmlns_hack=\"$NAMESPACE_INDICATION\"")
        val sw = StringReader(replacedForInterpreting)
        val schema = JAXB.unmarshal(sw, Schema::class.java)

//        val knownNamespaces = extractNamespaces(schema)

        val thisSchemaTargetNamespace = determineTargetNamespace(schema, targetNamespaceOverride)

        val entriesInThisFile = mutableListOf<Indexable>()

        for (extension in schema.includeOrImportOrRedefine) {
            if (extension is Include) {
                val pathOfOtherXsd = File(schemaFile).parent + File.separator + extension.schemaLocation
                knownTypes.addEntries(readAllElementsAndTypesInFile(pathOfOtherXsd, thisSchemaTargetNamespace))
            }
        }

        for (item in schema.simpleTypeOrComplexTypeOrGroup) {
            if (item is org.w3._2001.xmlschema.TopLevelComplexType) {

                val elementsInComplexType = mutableListOf<be.geoffrey.fusion.Element>()
                if (item.sequence != null) {
                    for (sequenceItem in item.sequence.particle) {
                        if (sequenceItem is JAXBElement<*>) {

                            val actualEntry = sequenceItem.value
                            if (actualEntry is Element) {
                                val referencedNamespace = if (!actualEntry.type!!.namespaceURI.isNullOrEmpty()) actualEntry.type!!.namespaceURI else thisSchemaTargetNamespace
                                val referencedType = QName(referencedNamespace, actualEntry.type!!.localPart)
                                elementsInComplexType.add(Element(actualEntry.name, referencedType))
                            }
                        }
                    }
                }

                val myType = ComplexType(QName(thisSchemaTargetNamespace, item.name!!), elementsInComplexType)
                entriesInThisFile.add(myType)

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

                entriesInThisFile.add(
                        SimpleType(QName(thisSchemaTargetNamespace, name),
                                QName("http://www.w3.org/2001/XMLSchema", base.localPart),
                                restrictions))
            }
        }

        knownTypes.addEntries(entriesInThisFile)
        return knownTypes
    }

    private fun determineTargetNamespace(schema: Schema, targetNamespaceOverride: String?): String {
        if (targetNamespaceOverride != null) {
            return targetNamespaceOverride
        }

        return schema.targetNamespace ?: ""
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
    - [ ] Make sure includes don't recurse
    - [ ] Detect include cases that don't work (different namespaces in the files)
    - [ ] Include w/ directory traversal (recursion)
    - [ ] Solve target namespace vs xmlns

     */

    @Test
    fun loadingOneComplexTypeWithSomeBasicFields() {

        val parser = SchemaParser()
        val typeDb = parser.readAllElementsAndTypesInFile("src/test/resources/simple_types/one_simple_type_container.xsd")
        val type = typeDb.getEntry(QName("", "TypesTest"))

        assertThat(type).isEqualTo(ComplexType(QName("", "TypesTest"), listOf(
                Element("AString", QName("http://www.w3.org/2001/XMLSchema", "string")),
                Element("AnInteger", QName("http://www.w3.org/2001/XMLSchema", "integer")),
                Element("ADouble", QName("http://www.w3.org/2001/XMLSchema", "double")
                ))))
    }

    @Test
    fun loadingSimpleTypeVariations() {

        val parser = SchemaParser()
        val typeDb = parser.readAllElementsAndTypesInFile("src/test/resources/simple_types/custom_simple_type_variations.xsd")

        assertThat(typeDb.getEntry(QName("", "MinLengthNumber"))).isEqualTo(SimpleType(
                QName("", "MinLengthNumber"),
                QName("http://www.w3.org/2001/XMLSchema", "int"),
                listOf(
                        MinLengthRestriction(10)
                )))

        assertThat(typeDb.getEntry(QName("", "Enum"))).isEqualTo(SimpleType(
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
        val typeDb = parser.readAllElementsAndTypesInFile("src/test/resources/namespace_shizzle/defined_namespaces.xsd")

        assertThat(typeDb.getEntry(QName("", "Hoi"))).isEqualTo(
                ComplexType(QName("", "Hoi"),
                        listOf(
                                Element("Ns1", QName("namespace1", "woep")),
                                Element("Ns2", QName("namespace2", "woep"))
                        )))
    }

    @Test
    fun testNamespaceResolutionXmlns() {

        val parser = SchemaParser()
        val typeDb = parser.readAllElementsAndTypesInFile("src/test/resources/namespace_shizzle/targetnamespace_test.xsd")

        assertThat(typeDb.getEntry(QName("DefaultNamespace", "SimpleHoi"))).isNotNull

        assertThat(typeDb.getEntry(QName("DefaultNamespace", "Hoi"))).isEqualTo(
                ComplexType(QName("DefaultNamespace", "Hoi"),
                        listOf(
                                Element("Ns1", QName("http://www.w3.org/2001/XMLSchema", "string"))
                        )))
    }

    @Test
    fun detectIncludeWithInvalidTargetNamespace() {

    }

    @Test
    fun testSimpleInclude() {
        val parser = SchemaParser()
        val typeDb = parser.readAllElementsAndTypesInFile("src/test/resources/includes/top_level.xsd")

        assertThat(typeDb.getEntry(QName("something_else", "IncludedType"))).isNull()
        assertThat(typeDb.getEntry(QName("top-level-woep", "IncludedType"))).isNotNull

        assertThat(typeDb.getEntry(QName("top-level-woep", "Hallo"))).isEqualTo(
                ComplexType(QName("top-level-woep", "Hallo"),
                        listOf(
                                Element("Included", QName("top-level-woep", "IncludedType"))
                        )))
    }

    @Test
    fun testParsingSimpleElement() {
        val parser = SchemaParser()
        val typeDb = parser.readAllElementsAndTypesInFile("src/test/resources/simple_types/single_element.xsd")


        assertThat(typeDb.getEntry(QName("", "Geoffrey"), ELEMENT))
                .isEqualTo(TopLevelElement("Geoffrey", QName("http://www.w3.org/2001/XMLSchema", "string")))
    }

    @Test
    fun testParsingElementWithInlineDefinedComplexType() {

        val parser = SchemaParser()
        val typeDb = parser.readAllElementsAndTypesInFile("src/test/resources/inline_definitions/element_with_complex_type.xsd")

        val foundTypes = typeDb.getEntryByPartOfName("foobar", "FoodBar", DEFINITION)
        assertThat(foundTypes).hasSize(1)

        val autoCreatedType = foundTypes[0]

        assertThat(typeDb.getEntry(QName("foobar", "FoodBar")))
                .isEqualTo(Element("FoodBar", autoCreatedType.getQName()))
    }
}