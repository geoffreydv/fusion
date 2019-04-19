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
    fun render(element: TopLevelElement): String
}

class XmlRenderer(private val typeDb: KnownBuildingBlocks) : Renderer {

    override fun render(element: TopLevelElement): String {

        val icFactory = DocumentBuilderFactory.newInstance()
        icFactory.isNamespaceAware = true
        val icBuilder: DocumentBuilder

        icBuilder = icFactory.newDocumentBuilder()
        val doc = icBuilder.newDocument()

        val renderedElement = renderSingleElement(doc, element)
        doc.appendChild(renderedElement)

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

    private fun renderSingleElement(doc: Document, element: ElementBase): Element? {

        val renderedElement: Element = when (element) {
            is TopLevelElement -> doc.createElementNS(element.name.namespace, element.name.name)
            is be.geoffrey.fusion.Element -> doc.createElementNS("", element.name)
            else -> throw IllegalArgumentException("Could not render element")
        }

        val elementType = element.getType()
        val typeLookup = typeDb.getStructure(elementType)

        if (typeLookup != null && typeLookup is GroupOfSimpleFields) {
            // Add all the child elements to this element
            for (field in typeLookup.fields) {
                renderedElement.appendChild(renderSingleElement(doc, field))
            }
        } else if (typeLookup != null && typeLookup is SimpleField) {
            if (typeLookup is StringField) {
                val textNode = doc.createTextNode("string")
                renderedElement.appendChild(textNode)
            }
        }

        return renderedElement
    }

}