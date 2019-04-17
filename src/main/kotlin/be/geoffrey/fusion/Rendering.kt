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
               renderingOptions: Map<QName, (QName) -> String> = hashMapOf()): String
}

class XmlRenderer(private val typeDb: TypeDb) : Renderer {

    override fun render(element: TopLevelElement,
                        renderingOptions: Map<QName, (QName) -> String>): String {

        val icFactory = DocumentBuilderFactory.newInstance()
        icFactory.isNamespaceAware = true
        val icBuilder: DocumentBuilder

        icBuilder = icFactory.newDocumentBuilder()
        val doc = icBuilder.newDocument()

        val renderedElement = renderSingleElement(doc, element, renderingOptions)
        doc.appendChild(renderedElement)

        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
        transformer.setOutputProperty(OutputKeys.INDENT, "yes")
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
        val source = DOMSource(doc)
        val sw = StringWriter()
        val output = StreamResult(sw)
        transformer.transform(source, output)

        return sw.toString()
    }

    private fun renderSingleElement(doc: Document, element: ElementBase,
                                    renderingOptions: Map<QName, (QName) -> String>): Element? {

        val renderedElement: Element = when (element) {
            is TopLevelElement -> doc.createElementNS(element.name.namespace, element.name.name)
            is be.geoffrey.fusion.Element -> doc.createElementNS("", element.name)
            else -> throw IllegalArgumentException("Could not render element")
        }

        val elementType = element.getType()
        val typeLookup = typeDb.getType(elementType)

        if (typeLookup != null && typeLookup is ComplexType) {
            // Add all the child elements to this element
            for (field in typeLookup.fields) {
                renderedElement.appendChild(renderSingleElement(doc, field, renderingOptions))
            }
        } else if (renderingOptions.containsKey(elementType)) {
            // Add a textnode with the rendered value
            val textNode = doc.createTextNode(renderingOptions.getValue(elementType).invoke(elementType))
            renderedElement.appendChild(textNode)
        }

        return renderedElement
    }

}