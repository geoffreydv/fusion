package be.geoffrey.fusion

import org.w3._2001.xmlschema.*
import org.w3._2001.xmlschema.Element
import org.xml.sax.SAXParseException
import java.io.File
import java.io.StringReader
import java.util.*
import javax.xml.bind.JAXB
import javax.xml.bind.JAXBElement
import javax.xml.bind.UnmarshalException

class XmlSchemaParser {

    fun readAllElementsAndTypesInFile(schemaFile: String, targetNamespaceOverride: String? = null): KnownBuildingBlocks {

        val knownBlocks = XmlBuildingBlocks()

        val asString = File(schemaFile).readText()
        val sw = StringReader(asString)
        val schema: Schema
        try {
            schema = JAXB.unmarshal(sw, Schema::class.java)
        } catch (e: Throwable) {
            println("HEY")
            throw IllegalArgumentException("bla")
        }

        val thisSchemaTargetNamespace = determineTargetNamespace(schema, targetNamespaceOverride)

        for (extension in schema.includeOrImportOrRedefine) {
            if (extension is Include) {
                val pathOfOtherXsd = File(schemaFile).parent + File.separator + extension.schemaLocation
                knownBlocks.addAllOfOther(readAllElementsAndTypesInFile(pathOfOtherXsd, thisSchemaTargetNamespace))
            } else if (extension is Import) {
                val pathOfOtherXsd = File(schemaFile).parent + File.separator + extension.schemaLocation
                knownBlocks.addAllOfOther(readAllElementsAndTypesInFile(pathOfOtherXsd, extension.namespace))
            }
        }
        for (item in schema.simpleTypeOrComplexTypeOrGroup) {

            if (item is TopLevelComplexType) {
                knownBlocks.add(parseComplexType(item, thisSchemaTargetNamespace, knownBlocks = knownBlocks))
            } else if (item is SimpleType) {
                knownBlocks.add(parseSimpleType(item, thisSchemaTargetNamespace, knownBlocks = knownBlocks))
            } else if (item is org.w3._2001.xmlschema.TopLevelElement) {

                // If the item has no type then it defines its type inside this element... Handle that!
                val elementName = QName(thisSchemaTargetNamespace, item.name)

                if (item.type != null) {
                    knownBlocks.add(TopLevelElement(elementName, determineNamespace(thisSchemaTargetNamespace, item.type)))
                } else {
                    val dynamicStructure = extractDynamicStructureFromInlineTypeOfElement(item, thisSchemaTargetNamespace, knownBlocks)
                    knownBlocks.add(dynamicStructure)
                    knownBlocks.add(TopLevelElement(elementName, dynamicStructure.getQName()))
                }
            }
        }
        return knownBlocks
    }

    private fun extractDynamicStructureFromInlineTypeOfElement(
            item: Element,
            thisSchemaTargetNamespace: String,
            knownBlocks: KnownBuildingBlocks
    ): Structure {
        if (item.complexType != null) {
            return parseComplexType(item.complexType, thisSchemaTargetNamespace, generateRandomStructureName(item), knownBlocks = knownBlocks)
        } else if (item.simpleType != null) {
            return parseSimpleType(item.simpleType, thisSchemaTargetNamespace, generateRandomStructureName(item), knownBlocks)
        }
        throw IllegalArgumentException("Sorry, I have no clue how to parse this...")
    }

    private fun generateRandomStructureName(item: Element) = item.name + UUID.randomUUID().toString()

    private fun parseSimpleType(
            item: SimpleType,
            thisSchemaTargetNamespace: String,
            nameOverride: String? = null,
            knownBlocks: KnownBuildingBlocks
    ): SimpleField {

        val baseSimpleTypeName = item.restriction.base
                ?: throw IllegalArgumentException("I have no clue how to parse this, sorry :(")

        val nameOfThisType = QName(thisSchemaTargetNamespace, nameOverride ?: item.name)

        // When parsing, just lookup the type from the registry
        // When it's a string, specialize it with additional restrictions

        if (baseSimpleTypeName.namespaceURI == XMLNS) {

            val baseType = knownBlocks.getStructure(QName(XMLNS, baseSimpleTypeName.localPart))

            when (baseType) {
                is StringField -> {
                    val enumRestrictions = findEnumRestrictions(item.restriction.facets)
                    if (enumRestrictions.isNotEmpty()) {
                        return EnumField(nameOfThisType, enumRestrictions)
                    }

                    val pattern: String? = findPatternRestriction(item.restriction.facets)
                    if (pattern != null) {
                        return RegexField(nameOfThisType, pattern)
                    }

                    return StringField(nameOfThisType)
                }
                is IntField -> return IntField(nameOfThisType)
                is DecimalField -> return DecimalField(nameOfThisType)
                is DateTimeField -> return DateTimeField(nameOfThisType)
                is Base64Field -> return Base64Field(nameOfThisType)
            }
        }

        throw IllegalArgumentException("Could not determine the basetype of a custom simpletype: $nameOfThisType, base: ${QName(baseSimpleTypeName.namespaceURI, baseSimpleTypeName.localPart)}")
    }

    private fun findPatternRestriction(facets: List<Any>): String? =
            facets.filter { it is Pattern }
                    .map { (it as Pattern).value }
                    .firstOrNull()

    private fun findEnumRestrictions(facets: List<Any>): MutableList<String> {

        val enumRestrictions = mutableListOf<String>()

        for (facetJaxbElement in facets) {
            if (facetJaxbElement is JAXBElement<*> && facetJaxbElement.name.localPart == "enumeration") {
                enumRestrictions.add((facetJaxbElement.value as Facet).value)
            }
        }

        return enumRestrictions
    }

    private fun parseComplexType(item: ComplexType,
                                 thisSchemaTargetNamespace: String,
                                 customName: String? = null,
                                 knownBlocks: KnownBuildingBlocks): GroupOfSimpleFields {

        val elementsInComplexType = mutableListOf<be.geoffrey.fusion.Element>()

        if (item.complexContent?.extension?.sequence != null) {
            elementsInComplexType.addAll(createElementsFromItemsInSequence(item.complexContent.extension.sequence, thisSchemaTargetNamespace, knownBlocks))
        } else if (item.sequence != null) {
            elementsInComplexType.addAll(createElementsFromItemsInSequence(item.sequence, thisSchemaTargetNamespace, knownBlocks))
        }

        val baseType: QName? = findBaseType(item)

        return GroupOfSimpleFields(
                QName(thisSchemaTargetNamespace, customName ?: item.name!!),
                elementsInComplexType,
                item.isAbstract,
                baseType)
    }

    private fun createElementsFromItemsInSequence(
            sequence: ExplicitGroup,
            thisSchemaTargetNamespace: String,
            knownBlocks: KnownBuildingBlocks
    ): MutableList<be.geoffrey.fusion.Element> {

        val elementsInComplexType = mutableListOf<be.geoffrey.fusion.Element>()

        for (sequenceItem in sequence.particle) {
            if (sequenceItem is JAXBElement<*>) {
                val actualEntry = sequenceItem.value

                if (actualEntry is Element) {
                    if (actualEntry.type != null) {
                        elementsInComplexType.add(Element(actualEntry.name, determineNamespace(thisSchemaTargetNamespace, actualEntry.type)))
                    } else {
                        val dynamicStructure = extractDynamicStructureFromInlineTypeOfElement(actualEntry, thisSchemaTargetNamespace, knownBlocks)
                        knownBlocks.add(dynamicStructure)
                        elementsInComplexType.add(Element(actualEntry.name, dynamicStructure.getQName()))
                    }
                }
            }
        }

        return elementsInComplexType
    }

    private fun findBaseType(item: ComplexType): QName? {
        val base = item.complexContent?.extension?.base ?: return null
        return QName(base.namespaceURI, base.localPart)
    }

    private fun determineNamespace(thisSchemaTargetNamespace: String, originalQName: javax.xml.namespace.QName): QName {
        val referencedNamespace = if (!originalQName.namespaceURI.isNullOrEmpty()) originalQName.namespaceURI else thisSchemaTargetNamespace
        return QName(referencedNamespace, originalQName.localPart)
    }

    private fun determineTargetNamespace(schema: Schema, targetNamespaceOverride: String?): String {
        if (targetNamespaceOverride != null) {
            return targetNamespaceOverride
        }

        return schema.targetNamespace ?: ""
    }
}