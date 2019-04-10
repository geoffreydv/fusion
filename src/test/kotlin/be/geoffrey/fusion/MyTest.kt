package be.geoffrey.fusion

import com.sun.org.apache.xerces.internal.impl.xs.XSImplementationImpl
import org.junit.Test
import org.w3c.dom.bootstrap.DOMImplementationRegistry


class Testing {

    @Test
    fun test() {
        System.setProperty(DOMImplementationRegistry.PROPERTY, "com.sun.org.apache.xerces.internal.dom.DOMXSImplementationSourceImpl")
        val registry = DOMImplementationRegistry.newInstance()
        val impl = registry.getDOMImplementation("XS-Loader") as XSImplementationImpl
        val schemaLoader = impl.createXSLoader(null)
        val model = schemaLoader.loadURI("src/test/resources/simple_types/parse_known_simples.xsd")

        print("JOW")
    }

}