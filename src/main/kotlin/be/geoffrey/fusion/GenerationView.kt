package be.geoffrey.fusion

import com.vaadin.flow.component.combobox.ComboBox
import com.vaadin.flow.component.dependency.StyleSheet
import com.vaadin.flow.component.html.H1
import com.vaadin.flow.component.html.H2
import com.vaadin.flow.component.html.Hr
import com.vaadin.flow.component.orderedlayout.VerticalLayout
import com.vaadin.flow.component.textfield.TextArea
import com.vaadin.flow.router.Route

// Manage both Java example and XML example at the same time
// Just to try to improve the overall design

interface Structure {
    val name: String
}

data class ComplexStructure(override val name: String, val fields: List<Field>) : Structure

data class SimpleStructure(override val name: String) : Structure

data class Field(val name: String, val type: Structure)

@Route("generation")
@StyleSheet("/style.css")
class GenerationView : VerticalLayout() {

    private val knownStructures = HashMap<String, Structure>()

    init {
        knownStructures["String"] = SimpleStructure("String")
        knownStructures["Person"] = ComplexStructure("Person",
                listOf(
                        Field("firstName", knownStructures["String"]!!),
                        Field("lastName", knownStructures["String"]!!)
                ))

        val outputBox = TextArea()
        outputBox.isReadOnly = true

        val rootCombo = ComboBox<String>()
        rootCombo.setItems(knownStructures.keys)
        rootCombo.addValueChangeListener { outputBox.value = generateOutput(it.value) }

        outputBox.width = "800px"
        outputBox.height = "400px"

        add(
                H1("Codename: Fusion"),
                H2("Generate Example"),
                rootCombo,
                Hr(),
                outputBox
        )
    }

    private fun generateOutput(rootType: String): String {
        val type = knownStructures[rootType]

        // First write a variable assignment, this is specific to the language
        // If Java

        var output = ""
        output += "${type!!.name} tmp = "

        if (type is SimpleStructure) {
            if (type.name == "String") {
                output += "\"StringValue\";"
            }
        } else if (type is ComplexStructure) {
            output += "new ${type.name}();"
        }

        return output;
    }
}