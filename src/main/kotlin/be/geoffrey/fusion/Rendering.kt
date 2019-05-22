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

data class RegexValueForType(val type: QName, val valueToRender: String)

data class RenderingConfig(private val regexValueForTypes: List<RegexValueForType> = listOf()) {
    fun getRegexValueForType(type: QName): String? {
        return regexValueForTypes.filter { it.type == type }.map { it.valueToRender }.firstOrNull()
    }
}

interface Renderer {

    fun render(element: TopLevelElement,
               renderingConfig: RenderingConfig = RenderingConfig(),
               decisions: Decisions = Decisions()): String

}

interface DecisionStrategy {
    fun getPossibilitiesToRender(element: TopLevelElement, db: KnownBuildingBlocks): List<Decisions>
}

class FollowValidRulesAndAddAsMuchVarietyAsPossibleInOneGo : DecisionStrategy {
    override fun getPossibilitiesToRender(element: TopLevelElement, db: KnownBuildingBlocks): List<Decisions> {

        val options = AllPossibleOptions(db).getAvailablePathForksThroughElement(element)

        val decisions = mutableListOf<Decision>()

        for (option in options) {
            if (option is ImplementationChoice) {
                decisions.add(ImplementationDecision(option.path, option.choices[0]))
            }
        }

        return listOf(Decisions(decisions))
    }
}

class XmlRenderer2(private val typeDb: KnownBuildingBlocks) : Renderer {

    override fun render(element: TopLevelElement, renderingConfig: RenderingConfig, decisions: Decisions): String {
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