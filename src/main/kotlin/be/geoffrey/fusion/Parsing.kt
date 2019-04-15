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
                                      targetNamespaceOverride: String? = null): TypeDb {

        val knownTypes = TypeDb()

        val asString = File(schemaFile).readText()
//        var replacedForInterpreting = asString.replace(Regex("xmlns(:?)(.*?)=\"(.*?)\"")) {
//            "${it.groupValues[0]} xmlns_hack${it.groupValues[1]}${it.groupValues[2]}=\"${it.groupValues[3]}\""
//        }
//        replacedForInterpreting = replacedForInterpreting.replaceFirst("schema", "schema xmlns:xmlns_hack=\"$NAMESPACE_INDICATION\"")
        val sw = StringReader(asString)
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
            if (item is TopLevelComplexType) {
                entriesInThisFile.add(parseComplexType(item, thisSchemaTargetNamespace))
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

            } else if (item is org.w3._2001.xmlschema.TopLevelElement) {

                // IF the item has no type then it defines its type inside this element... Handle that!

                val elementName = QName(thisSchemaTargetNamespace, item.name)

                if (item.type != null) {
                    entriesInThisFile.add(TopLevelElement(elementName, determineNamespace(thisSchemaTargetNamespace, item.type)))
                } else {
                    // Look inside this element for a complexType
                    if (item.complexType != null) {

                        // Give it a random name
                        val randomName = item.name + UUID.randomUUID().toString()
                        val generatedType = parseComplexType(item.complexType, thisSchemaTargetNamespace, randomName)
                        entriesInThisFile.add(generatedType)

                        // Save the element with that type...
                        entriesInThisFile.add(TopLevelElement(elementName, generatedType.name))
                    }
                }
            }
        }

        knownTypes.addEntries(entriesInThisFile)
        return knownTypes
    }

    private fun parseComplexType(item: org.w3._2001.xmlschema.ComplexType,
                                 thisSchemaTargetNamespace: String,
                                 customName: String? = null): ComplexType {

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

        return ComplexType(QName(thisSchemaTargetNamespace, customName ?: item.name!!), elementsInComplexType)
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

//    private fun extractNamespaces(schema: Schema): HashMap<String, String> {
//        val foundNamespaces = hashMapOf<String, String>()
//        schema.otherAttributes.forEach { name, value ->
//            if (name.namespaceURI == NAMESPACE_INDICATION) {
//                foundNamespaces[name!!.localPart] = value
//            } else if (name.localPart == "xmlns_hack") {
//                foundNamespaces[""] = value
//            }
//        }
//
//        foundNamespaces.putIfAbsent("", "")
//        return foundNamespaces
//    }
}

