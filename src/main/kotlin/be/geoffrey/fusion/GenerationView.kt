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

interface Structure

data class ComplexStructure(val name: String,
                            val fields: List<Field>) : Structure

data class SimpleStructure(val name: String) : Structure

data class Field(val name: String, val type: Structure)

@Route("generation")
@StyleSheet("/style.css")
class GenerationView : VerticalLayout() {

    init {
        val knownStructures = HashMap<String, Structure>()

        knownStructures["String"] = SimpleStructure("String")
        knownStructures["Person"] = ComplexStructure("Person", listOf(
                Field("firstName", knownStructures["String"]!!),
                Field("lastName", knownStructures["String"]!!)
        ))

        val outputBox = TextArea()
        outputBox.isReadOnly = true

        val rootCombo = ComboBox<String>()
        rootCombo.setItems(knownStructures.keys)
        rootCombo.addValueChangeListener { outputBox.value = generateOutput(it.value) }

        add(
                H1("Codename: Fusion"),
                H2("Generate Example"),
                rootCombo,
                Hr(),
                outputBox
        )
    }

    private fun generateOutput(rootType: String) = "My Output..."
}