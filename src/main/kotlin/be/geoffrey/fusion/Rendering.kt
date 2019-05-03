package be.geoffrey.fusion

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult


interface Renderer {
    fun render(element: TopLevelElement, renderingConfig: RenderingConfig = RenderingConfig()): String
}

class XmlRenderer(private val typeDb: KnownBuildingBlocks) : Renderer {

    override fun render(element: TopLevelElement, renderingConfig: RenderingConfig): String {

        val icFactory = DocumentBuilderFactory.newInstance()
        icFactory.isNamespaceAware = true
        val icBuilder: DocumentBuilder

        icBuilder = icFactory.newDocumentBuilder()
        val doc = icBuilder.newDocument()

        doc.appendChild(createDomElementForElement(doc, element, renderingConfig, ElementStack()))

        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
        transformer.setOutputProperty(OutputKeys.INDENT, "yes")
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
        val source = DOMSource(doc)
        val sw = StringWriter()
        val output = StreamResult(sw)
        transformer.transform(source, output)

        return sw.toString().trim()
    }

    private fun createDomElementForElement(doc: Document,
                                           element: ElementBase,
                                           renderingConfig: RenderingConfig,
                                           stack: ElementStack): Element {

        stack.push(element)

        val domElement = when (val structure = typeDb.getStructure(element.getStructureReference())
                ?: throw IllegalArgumentException("The required type for element ${element.getDisplayName()} was not found: ${element.getStructureReference()}")) {
            is GroupOfSimpleFields -> createDomElementForGroupOfSimpleFields(element, doc, structure, renderingConfig, stack)
            is SimpleField -> createDomElementForSimpleField(element, doc, structure, renderingConfig)
            else -> throw IllegalArgumentException("How did I even get here?")
        }

        stack.pop()
        return domElement
    }

    private fun createDomElementForSimpleField(element: ElementBase, doc: Document, structure: SimpleField, renderingConfig: RenderingConfig): Element {
        val domElement: Element = createDomElement(element, doc)
        domElement.appendChild(doc.createTextNode(generateExampleValueForSimpleType(structure, renderingConfig)))
        return domElement
    }

    private fun createDomElementForGroupOfSimpleFields(
            element: ElementBase,
            doc: Document,
            structure: GroupOfSimpleFields,
            renderingConfig: RenderingConfig,
            stack: ElementStack
    ): Element {
        val domElement: Element = createDomElement(element, doc)

        if (!structure.abstract) {
            appendAllChildrenToElement(structure, domElement, doc, renderingConfig, stack)
            return domElement
        }

        val concreteImplementation = findConcreteImplementation(structure)
        modifyDomElementToMatchConcreteImplementationType(concreteImplementation, domElement)
        appendAllChildrenToElement(concreteImplementation, domElement, doc, renderingConfig, stack)

        return domElement
    }

    private fun modifyDomElementToMatchConcreteImplementationType(concreteImplementation: GroupOfSimpleFields, domElement: Element) {
        if (concreteImplementation.name.namespace != "") {
            domElement.setAttribute("xmlns:impl", concreteImplementation.name.namespace)
            domElement.setAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "xsi:type", "impl:${concreteImplementation.name.name}")
        } else {
            domElement.setAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "xsi:type", concreteImplementation.name.name)
        }
    }

    private fun findConcreteImplementation(structure: GroupOfSimpleFields): GroupOfSimpleFields {
        // Find the first available concrete type
        val concreteImplementations: List<GroupOfSimpleFields> = typeDb.getConcreteImplementationsFor(structure.name)

        if (concreteImplementations.isEmpty()) {
            throw IllegalArgumentException("No concrete implementation was found for abstract type " + structure.name)
        }

        return concreteImplementations[0]
    }

    private fun appendAllChildrenToElement(structure: GroupOfSimpleFields, domElement: Element, doc: Document, renderingConfig: RenderingConfig, stack: ElementStack) {
        for (field in allFieldsInStructure(structure)) {
            if (!stack.recursionWillStartWhenAdding(field)) {
                domElement.appendChild(createDomElementForElement(doc, field, renderingConfig, stack))
            }
        }
    }

    private fun allFieldsInStructure(structure: GroupOfSimpleFields): List<be.geoffrey.fusion.Element> {
        val parents = typeDb.getParentTypesFor(structure)

        val elements = mutableListOf<be.geoffrey.fusion.Element>()

        for (parent in parents) {
            elements.addAll(parent.fields)
        }

        elements.addAll(structure.fields)
        return elements
    }

    private fun createDomElement(element: ElementBase, doc: Document): Element {
        return when (element) {
            is TopLevelElement -> doc.createElementNS(element.name.namespace, element.name.name)
            is be.geoffrey.fusion.Element -> doc.createElementNS("", element.name)
            else -> throw IllegalArgumentException("Could not create dom element")
        }
    }

    private fun generateExampleValueForSimpleType(field: SimpleField, renderingConfig: RenderingConfig): String {
        return when (field) {
            is StringField -> "string"
            is RegexField -> renderingConfig.getRegexValueForType(field.getQName())
                    ?: ("Regex for type " + field.getQName() + ", pattern: " + field.pattern)
            is IntField -> "1"
            is DecimalField -> "123.456"
            is EnumField -> field.possibleValues[0]
            is BooleanField -> "true"
            else -> throw IllegalArgumentException("This type is known but I have no clue how to render it. The field is a ${field.javaClass}, the original type is: ${field.getQName()}")
        }
    }
}