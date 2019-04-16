package be.geoffrey.fusion

import java.io.StringWriter
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult


interface Renderer {
    fun render(element: TopLevelElement,
               renderingOptions: Map<String, Any> = hashMapOf()): String
}

class XmlRenderer(val typeDb: TypeDb) : Renderer {

    override fun render(element: TopLevelElement,
                        renderingOptions: Map<String, Any>): String {

        val icFactory = DocumentBuilderFactory.newInstance()
        val icBuilder: DocumentBuilder

        icBuilder = icFactory.newDocumentBuilder()
        val doc = icBuilder.newDocument()

        val rootElement = doc.createElementNS(element.name.namespace, element.name.name)
        doc.appendChild(rootElement)

        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
        transformer.setOutputProperty(OutputKeys.INDENT, "yes")
        val source = DOMSource(doc)
        val sw = StringWriter()
        val console = StreamResult(sw)
        transformer.transform(source, console)

        return sw.toString().trimIndent()
    }

}