package be.geoffrey.fusion

import org.w3._2001.xmlschema.*
import org.w3._2001.xmlschema.Element
import org.xmlsoap.schemas.wsdl.TDefinitions
import org.xmlsoap.schemas.wsdl.TTypes
import java.io.File
import java.io.StringReader
import java.math.BigInteger
import java.util.*
import javax.xml.bind.JAXB
import javax.xml.bind.JAXBElement
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.OutputKeys
import java.io.StringWriter
import javax.xml.transform.TransformerFactory



enum class GroupType {
    CHOICE,
    SEQUENCE
}

class XmlSchemaParser {

    fun readAllElementsAndTypesInFile(schemaFile: String, targetNamespaceOverride: String? = null): KnownBuildingBlocks {

        val knownBlocks = XmlBuildingBlocks()

        if(schemaFile.endsWith("wsdl")) {

            val asString = File(schemaFile).readText()
            val sw = StringReader(asString)
            try {
                val definitions = JAXB.unmarshal(sw, TDefinitions::class.java)

                for (documented in definitions.anyTopLevelOptionalElement) {
                    if(documented is TTypes) {
                        for (child in documented.any) {
                            if(child is org.w3c.dom.Element && child.localName == "schema") {
                                val schema = parseXmlSchema(schemaElementToString(child))
                                indexEverythingInSchema(schema, targetNamespaceOverride, schemaFile, knownBlocks)
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                throw IllegalArgumentException(e.message)
            }
        } else if(schemaFile.endsWith("xsd")) {
            val asString = File(schemaFile).readText()
            val schema: Schema = parseXmlSchema(asString)
            indexEverythingInSchema(schema, targetNamespaceOverride, schemaFile, knownBlocks)
        }

        return knownBlocks
    }

    private fun indexEverythingInSchema(schema: Schema, targetNamespaceOverride: String?, schemaFile: String, knownBlocks: XmlBuildingBlocks) {

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
            when (item) {
                is TopLevelComplexType -> knownBlocks.add(parseComplexType(item, thisSchemaTargetNamespace, knownBlocks = knownBlocks))
                is org.w3._2001.xmlschema.SimpleType -> knownBlocks.add(parseSimpleType(item, thisSchemaTargetNamespace, knownBlocks = knownBlocks))
                is org.w3._2001.xmlschema.TopLevelElement -> {
                    val elementName = QName(thisSchemaTargetNamespace, item.name)
                    val elementType = findElementTypeOrCreateDynamically(item, thisSchemaTargetNamespace, knownBlocks);
                    knownBlocks.add(TopLevelElement(elementName, elementType))
                }
            }
        }
    }

    private fun schemaElementToString(element: org.w3c.dom.Element): String {

        val transFactory = TransformerFactory.newInstance()
        val transformer = transFactory.newTransformer()
        val buffer = StringWriter()
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
        transformer.transform(DOMSource(element), StreamResult(buffer))
        return buffer.toString()
    }

    private fun parseXmlSchema(asString: String): Schema {
        val sw = StringReader(asString)
        val schema: Schema
        try {
            schema = JAXB.unmarshal(sw, Schema::class.java)
        } catch (e: Throwable) {
            throw IllegalArgumentException(e.message)
        }
        return schema
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
            item: org.w3._2001.xmlschema.SimpleType,
            thisSchemaTargetNamespace: String,
            nameOverride: String? = null,
            knownBlocks: KnownBuildingBlocks): SimpleType {

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

    private fun parseComplexType(item: org.w3._2001.xmlschema.ComplexType,
                                 thisSchemaTargetNamespace: String,
                                 customName: String? = null,
                                 knownBlocks: KnownBuildingBlocks): ComplexType {

        val baseType: QName? = findBaseType(item)
        val firstContentGroup = findFirstGroup(item)

        return ComplexType(
                QName(thisSchemaTargetNamespace, customName ?: item.name!!),
                parseGroup(firstContentGroup, thisSchemaTargetNamespace, knownBlocks),
                item.isAbstract,
                baseType)
    }

    private fun findFirstGroup(item: org.w3._2001.xmlschema.ComplexType): Pair<ExplicitGroup, GroupType>? {
        return when {
            item.complexContent?.extension?.sequence != null -> Pair(item.complexContent?.extension?.sequence!!, GroupType.SEQUENCE)
            item.sequence != null -> Pair(item.sequence, GroupType.SEQUENCE)
            item.choice != null -> Pair(item.choice, GroupType.CHOICE)
            else -> null
        }
    }

    private fun parseGroup(group: Pair<ExplicitGroup, GroupType>?,
                           thisSchemaTargetNamespace: String,
                           knownBlocks: KnownBuildingBlocks): List<StructureElement> {

        if (group == null) {
            return listOf()
        }

        val children = extractElementsFromGroup(group.first, thisSchemaTargetNamespace, knownBlocks)

        if (group.second == GroupType.SEQUENCE) {
            return listOf(SequenceOfElements(children))
        } else if (group.second == GroupType.CHOICE) {
            return listOf(ChoiceOfElements(children))
        }

        throw IllegalArgumentException("Sorry, I have no clue")
    }

    private fun extractElementsFromGroup(
            group: ExplicitGroup,
            thisSchemaTargetNamespace: String,
            knownBlocks: KnownBuildingBlocks
    ): MutableList<StructureElement> {

        val elementsInThisGroup = mutableListOf<StructureElement>()

        for (sequenceItem in group.particle) {
            if (sequenceItem is JAXBElement<*>) {
                when (val actualEntry = sequenceItem.value) {
                    is Element -> {

                        val ref = actualEntry.ref
                        if(ref != null) {
                            val ns = resolveQName(thisSchemaTargetNamespace, ref)
                            elementsInThisGroup.add(ElementReference(ns, minOccurs = minOccurs(actualEntry.minOccurs)))
                        } else {
                            val elementType: QName = findElementTypeOrCreateDynamically(actualEntry, thisSchemaTargetNamespace, knownBlocks)
                            elementsInThisGroup.add(Element(actualEntry.name, elementType, minOccurs = minOccurs(actualEntry.minOccurs)))
                        }
                    }
                    is ExplicitGroup -> when {
                        sequenceItem.name.localPart == "sequence" -> {
                            elementsInThisGroup.add(SequenceOfElements(extractElementsFromGroup(actualEntry, thisSchemaTargetNamespace, knownBlocks)))
                        }
                        sequenceItem.name.localPart == "choice" -> {
                            elementsInThisGroup.add(ChoiceOfElements(extractElementsFromGroup(actualEntry, thisSchemaTargetNamespace, knownBlocks)))
                        }
                        else -> throw IllegalArgumentException("BOOM")
                    }
                    else -> throw IllegalArgumentException("I have no clue...")
                }
            } else {
                throw IllegalArgumentException("I have no clue...")
            }
        }

        return elementsInThisGroup
    }

    private fun minOccurs(minOccurs: BigInteger?): Int {
        return minOccurs?.intValueExact() ?: 1
    }

    private fun findElementTypeOrCreateDynamically(
            element: Element,
            thisSchemaTargetNamespace: String,
            knownBlocks: KnownBuildingBlocks
    ): QName {
        return if (element.type != null) {
            resolveQName(thisSchemaTargetNamespace, element.type)
        } else {
            val dynamicStructure = extractDynamicStructureFromInlineTypeOfElement(element, thisSchemaTargetNamespace, knownBlocks)
            knownBlocks.add(dynamicStructure)
            dynamicStructure.getQName()
        }
    }

    private fun findBaseType(item: org.w3._2001.xmlschema.ComplexType): QName? {
        val base = item.complexContent?.extension?.base ?: return null
        return QName(base.namespaceURI, base.localPart)
    }

    private fun resolveQName(thisSchemaTargetNamespace: String, originalQName: javax.xml.namespace.QName): QName {
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