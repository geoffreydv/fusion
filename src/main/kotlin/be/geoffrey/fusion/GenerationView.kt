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

data class Field(val name: String, val structure: Structure)

class KnowledgeBase {

    private val knownStructures = HashMap<String, Structure>()

    init {
        knownStructures["String"] = SimpleStructure("String")
    }

    fun addStructure(structure: Structure) {
        knownStructures[structure.name] = structure
    }

    fun get(key: String): Structure? {
        return knownStructures[key]
    }

    fun keys(): Set<String> {
        return knownStructures.keys
    }
}

class Java8Renderer {

    fun render(structure: Structure): String {
        return "${renderInitialization(structure)} ${renderBuildStructure(structure)}"
    }

    private fun renderInitialization(structure: Structure): String {
        return "${structure.name} tmp = "
    }

    private fun renderBuildStructure(structure: Structure): String {

        var output = ""
        if (structure is SimpleStructure) {
            output += renderSimpleStructure(structure) + ";"
        } else if (structure is ComplexStructure) {
            output += "new ${structure.name}();\n"

            for (field in structure.fields) {
                if (field.structure is SimpleStructure) {
                    output += "tmp.${field.name} = ${renderSimpleStructure(field.structure)};\n"
                }
            }
        }

        return output
    }

    private fun renderSimpleStructure(structure: SimpleStructure): String {

        if (structure.name == "String") {
            return "\"StringValue\""
        }

        throw IllegalArgumentException("Sorry, no clue")
    }
}

@Route("generation")
@StyleSheet("/style.css")
class GenerationView : VerticalLayout() {

    private val kb = KnowledgeBase()

    init {

        kb.addStructure(ComplexStructure("Person",
                listOf(
                        Field("firstName", kb.get("String")!!),
                        Field("lastName", kb.get("String")!!)
                )))

        val outputBox = TextArea()
        outputBox.isReadOnly = true

        val rootCombo = ComboBox<String>()
        rootCombo.setItems(kb.keys())
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
        val structure = kb.get(rootType)
        return Java8Renderer().render(structure!!)
    }
}