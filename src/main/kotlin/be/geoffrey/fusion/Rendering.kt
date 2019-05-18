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

    fun render(element: TopLevelElement,
               renderingConfig: RenderingConfig = RenderingConfig(),
               decisions: Decisions = Decisions()): String

}

// Traverse down the tree
//  - Don't really care when we hit a sequence, choice or whatever
//  - We only care about: A simple tag (simple type element)
//  - When an element is another class we know so we can go one level deeper and start over
//  - We probably need some namespace shizzle because this is xml...

class XmlRenderer2(private val typeDb: KnownBuildingBlocks) : Renderer {

    override fun render(element: TopLevelElement, renderingConfig: RenderingConfig, decisions: Decisions): String {
//         First run through the element to check for the available decisions
        val options = PossibleOptions(typeDb).getAvailablePathForksThroughElement(element, decisions)
//        val decisions = Decisions()

        val document = prepareDocumentForAppendingElementsTo()

        var elementToAddTo: Element? = null

        val traverser = ElementTraverser(typeDb, decisions, TraverseHooks(
                simpleElementHit = fun(element: ElementBase, type: SimpleType) {

                    val domElement = simpleTypeToDomElement(element, document, type, renderingConfig)

                    if (elementToAddTo == null) {
                        document.appendChild(domElement)
                        elementToAddTo = domElement // Hoeft niet, dit betekent dat het hoogste type een simpletype is (denk ik... to test w/ coverage)
                    } else {
                        elementToAddTo!!.appendChild(domElement)
                    }
                },
                startRenderingComplexElement = fun(element: ElementBase, chosenPaths: ReadOnlyChosenPaths) {

                    val domElement = complexTypeToDomElement(element, document, chosenPaths) // TODO: ChosenPaths is er mss wat over.. gewoon concrete type meegeven van bovenaf

                    if (elementToAddTo == null) {
                        document.appendChild(domElement)
                        elementToAddTo = domElement
                    } else {
                        elementToAddTo!!.appendChild(domElement)
                        elementToAddTo = domElement
                    }
                },
                finishedRenderingComplexElement = { stack ->

                    if (elementToAddTo != null) {
                        if (elementToAddTo!!.parentNode is Element) {
                            elementToAddTo = elementToAddTo!!.parentNode as Element
                        } else {
                            elementToAddTo = null // To reference "Document" again later
                        }
                    }
                })
        )

        traverser.traverseElement(element)

        return renderDocument(document)
    }

    private fun prepareDocumentForAppendingElementsTo(): Document {
        val builderFactory = DocumentBuilderFactory.newInstance()
        builderFactory.isNamespaceAware = true

        val builder: DocumentBuilder = builderFactory.newDocumentBuilder()
        return builder.newDocument()
    }

    private fun renderDocument(doc: Document): String {

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

    private fun simpleTypeToDomElement(element: ElementBase, doc: Document, structure: SimpleType, renderingConfig: RenderingConfig): Element {
        val domElement: Element = createDomElement(element, doc)
        domElement.appendChild(doc.createTextNode(generateExampleValueForSimpleType(structure, renderingConfig)))
        return domElement
    }

    private fun complexTypeToDomElement(element: ElementBase, doc: Document, chosenPaths: ReadOnlyChosenPaths): Element {
        val domElement = createDomElement(element, doc)

        val md = chosenPaths.getCurrentElementMetadata()
        if (md.concreteImplementationMarker != null) {
            modifyDomElementToMatchConcreteImplementationType(domElement, md.concreteImplementationMarker!!)
        }
        return domElement
    }

    private fun modifyDomElementToMatchConcreteImplementationType(domElement: Element, implementationName: QName) {
        if (implementationName.namespace != "") {
            domElement.setAttribute("xmlns:impl", implementationName.namespace)
            domElement.setAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "xsi:type", "impl:${implementationName.name}")
        } else {
            domElement.setAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "xsi:type", implementationName.name)
        }
    }

    private fun createDomElement(element: ElementBase, doc: Document): Element {
        return when (element) {
            is TopLevelElement -> doc.createElementNS(element.name.namespace, element.name.name)
            is be.geoffrey.fusion.Element -> doc.createElementNS("", element.name)
            else -> throw IllegalArgumentException("Could not create dom element")
        }
    }

    private fun generateExampleValueForSimpleType(type: SimpleType, renderingConfig: RenderingConfig): String {
        return when (type) {
            is StringField -> "string"
            is RegexField -> renderingConfig.getRegexValueForType(type.getQName())
                    ?: ("Regex for type " + type.getQName() + ", pattern: " + type.pattern)
            is IntField -> "1"
            is DecimalField -> "123.456"
            is EnumField -> type.possibleValues[0]
            is BooleanField -> "true"
            else -> throw IllegalArgumentException("This type is known but I have no clue how to render it. The field is a ${type.javaClass}, the original type is: ${type.getQName()}")
        }
    }
}

class XmlRenderer(private val typeDb: KnownBuildingBlocks) : Renderer {

    override fun render(element: TopLevelElement, renderingConfig: RenderingConfig, decisions: Decisions): String {

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
            is SimpleType -> simpleTypeToDomElement(element, doc, structure, renderingConfig)
            is ComplexType -> complexTypeToDomElement(element, doc, structure, renderingConfig, stack)
            else -> throw IllegalArgumentException("How did I even get here?")
        }

        stack.pop()
        return domElement
    }

    private fun simpleTypeToDomElement(element: ElementBase, doc: Document, structure: SimpleType, renderingConfig: RenderingConfig): Element {
        val domElement: Element = createDomElement(element, doc)
        domElement.appendChild(doc.createTextNode(generateExampleValueForSimpleType(structure, renderingConfig)))
        return domElement
    }

    private fun complexTypeToDomElement(
            element: ElementBase,
            doc: Document,
            structure: ComplexType,
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

    private fun modifyDomElementToMatchConcreteImplementationType(concreteImplementation: ComplexType, domElement: Element) {
        if (concreteImplementation.name.namespace != "") {
            domElement.setAttribute("xmlns:impl", concreteImplementation.name.namespace)
            domElement.setAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "xsi:type", "impl:${concreteImplementation.name.name}")
        } else {
            domElement.setAttributeNS("http://www.w3.org/2001/XMLSchema-instance", "xsi:type", concreteImplementation.name.name)
        }
    }

    private fun findConcreteImplementation(structure: ComplexType): ComplexType {
        // Find the first available concrete type
        val concreteImplementations: List<ComplexType> = typeDb.getConcreteImplementationsFor(structure.name)

        if (concreteImplementations.isEmpty()) {
            throw IllegalArgumentException("No concrete implementation was found for abstract type " + structure.name)
        }

        return concreteImplementations[0]
    }

    private fun appendAllChildrenToElement(
            structure: ComplexType,
            domElement: Element,
            doc: Document,
            renderingConfig: RenderingConfig,
            stack: ElementStack) {

        val parts = prependAllParentElements(structure)
        val fields = breakTreeStructureIntoFlatListOfFields(parts)

        for (field in fields) {

            if (stack.recursionWillStartWhenAdding(field)) {
                return
            }

            for (i in 0 until decideTimesToRenderField(field)) {
                domElement.appendChild(createDomElementForElement(doc, field, renderingConfig, stack))
            }
        }
    }

    private fun decideTimesToRenderField(field: be.geoffrey.fusion.Element): Int {
        return if (field.minOccurs > 1) {
            field.minOccurs
        } else 1
    }

    private fun breakTreeStructureIntoFlatListOfFields(parts: MutableList<StructureElement>): List<be.geoffrey.fusion.Element> {

        // Get one or more elements to render and render them

        // If it's a choice, pick the first one (and keep recursing until finished)
        // If it's a sequence, add all the elements and keep recursing until finished
        // If it's an element, create a dom element and render it! (and check the recursing shizzle)

        val flat = mutableListOf<be.geoffrey.fusion.Element>()

        for (part in parts) {
            when (part) {
                is be.geoffrey.fusion.Element -> {
                    flat.add(part)
                }
                is SequenceOfElements -> {
                    for (partInSequence in part.allElements()) {
                        flat.addAll(breakTreeStructureIntoFlatListOfFields(mutableListOf(partInSequence)))
                    }
                }
                is ChoiceOfElements -> {
                    val elementToRender = decideChoiceElementToRender(part)
                    if (elementToRender != null) {
                        flat.addAll(breakTreeStructureIntoFlatListOfFields(mutableListOf(elementToRender)))
                    }
                }
            }
        }

        return flat
    }

    private fun decideChoiceElementToRender(part: ChoiceOfElements): StructureElement? {
        if (part.allElements().isEmpty()) {
            return null
        }

        return part.allElements()[0]
    }

    private fun prependAllParentElements(structure: ComplexType): MutableList<StructureElement> {
        val parents = typeDb.getParentTypesFor(structure)

        val elements = mutableListOf<StructureElement>()

        for (parent in parents) {
            elements.addAll(parent.children)
        }

        elements.addAll(structure.children)
        return elements
    }

    private fun createDomElement(element: ElementBase, doc: Document): Element {
        return when (element) {
            is TopLevelElement -> doc.createElementNS(element.name.namespace, element.name.name)
            is be.geoffrey.fusion.Element -> doc.createElementNS("", element.name)
            else -> throw IllegalArgumentException("Could not create dom element")
        }
    }

    private fun generateExampleValueForSimpleType(type: SimpleType, renderingConfig: RenderingConfig): String {
        return when (type) {
            is StringField -> "string"
            is RegexField -> renderingConfig.getRegexValueForType(type.getQName())
                    ?: ("Regex for type " + type.getQName() + ", pattern: " + type.pattern)
            is IntField -> "1"
            is DecimalField -> "123.456"
            is EnumField -> type.possibleValues[0]
            is BooleanField -> "true"
            else -> throw IllegalArgumentException("This type is known but I have no clue how to render it. The field is a ${type.javaClass}, the original type is: ${type.getQName()}")
        }
    }
}