package be.geoffrey.fusion

import org.w3._2001.xmlschema.*
import org.w3._2001.xmlschema.Element
import java.io.File
import java.io.StringReader
import java.util.*
import javax.xml.bind.JAXB
import javax.xml.bind.JAXBElement


class SchemaParser {

    fun readAllElementsAndTypesInFile(schemaFile: String,
                                      targetNamespaceOverride: String? = null): KnownBuildingBlocks {

        val knownBlocks = XmlBuildingBlocks()

        val asString = File(schemaFile).readText()
        val sw = StringReader(asString)
        val schema = JAXB.unmarshal(sw, Schema::class.java)

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
                knownBlocks.add(parseComplexType(item, thisSchemaTargetNamespace))
            } else if (item is SimpleType) {
                knownBlocks.add(parseSimpleType(item, thisSchemaTargetNamespace))
            } else if (item is org.w3._2001.xmlschema.TopLevelElement) {

                // If the item has no type then it defines its type inside this element... Handle that!
                val elementName = QName(thisSchemaTargetNamespace, item.name)

                if (item.type != null) {
                    knownBlocks.add(TopLevelElement(elementName, determineNamespace(thisSchemaTargetNamespace, item.type)))
                } else {
                    // Look inside this element for a complexType
                    if (item.complexType != null) {
                        // Give it a random name
                        val customRandomName = item.name + UUID.randomUUID().toString()
                        val generatedType = parseComplexType(item.complexType, thisSchemaTargetNamespace, customRandomName)
                        knownBlocks.add(generatedType)
                        // Save the element with that type...

                        knownBlocks.add(TopLevelElement(elementName, generatedType.name))
                    } else if (item.simpleType != null) {
                        // Give it a random name
                        val customRandomName = item.name + UUID.randomUUID().toString()
                        val generatedType = parseSimpleType(item.simpleType, thisSchemaTargetNamespace, customRandomName)
                        knownBlocks.add(generatedType)

                        knownBlocks.add(TopLevelElement(elementName, generatedType.getQName()))
                    }
                }
            }
        }
        return knownBlocks
    }

    private fun parseSimpleType(
            item: SimpleType,
            thisSchemaTargetNamespace: String,
            nameOverride: String? = null
    ): SimpleField {

        val baseSimpleType = item.restriction.base

        if (baseSimpleType == null) {
            throw IllegalArgumentException("I have no clue how to parse this, sorry :(");
        }

        val nameOfThisType = QName(thisSchemaTargetNamespace, nameOverride ?: item.name)

        if (baseSimpleType.namespaceURI == XMLNS) {
            if (baseSimpleType.localPart == "string") {
                val enumRestrictions = findEnumRestrictions(item.restriction.facets)

                if (enumRestrictions.isNotEmpty()) {
                    return EnumField(nameOfThisType, enumRestrictions)
                } else {
                    return StringField(nameOfThisType)
                }
            }
        }

        return UnknownField(nameOfThisType, QName(baseSimpleType.namespaceURI, baseSimpleType.localPart))
    }

    private fun findEnumRestrictions(facets: MutableList<Any>): MutableList<String> {

        val enumRestrictions = mutableListOf<String>()

        for (facetJaxbElement in facets) {
            if (facetJaxbElement is JAXBElement<*> && facetJaxbElement.name.localPart == "enumeration") {
                val value = (facetJaxbElement.value as Facet).value
                enumRestrictions.add(value)
            }
        }

        return enumRestrictions
    }

    private fun parseComplexType(item: ComplexType,
                                 thisSchemaTargetNamespace: String,
                                 customName: String? = null): GroupOfSimpleFields {

        val elementsInComplexType = mutableListOf<be.geoffrey.fusion.Element>()
        if (item.sequence != null) {
            for (sequenceItem in item.sequence.particle) {
                if (sequenceItem is JAXBElement<*>) {

                    val actualEntry = sequenceItem.value
                    if (actualEntry is Element) {
                        val referencedType = determineNamespace(thisSchemaTargetNamespace, actualEntry.type!!)
                        elementsInComplexType.add(Element(actualEntry.name, referencedType))
                    }
                }
            }
        }

        return GroupOfSimpleFields(QName(thisSchemaTargetNamespace, customName ?: item.name!!), elementsInComplexType)
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