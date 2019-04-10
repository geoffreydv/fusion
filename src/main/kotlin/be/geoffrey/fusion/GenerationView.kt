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

    private val renderers = HashMap<String, Renderer>()
    private val knownStructures = HashMap<String, Structure>()

    init {
        knownStructures["String"] = SimpleStructure("String")
        renderers["Java8"] = Java8Renderer()
    }

    fun getRenderer(name: String): Renderer? {
        return renderers[name]
    }

    fun addStructure(structure: Structure) {
        knownStructures[structure.name] = structure
    }

    fun getStructure(key: String): Structure? {
        return knownStructures[key]
    }

    fun structureKeys(): Set<String> {
        return knownStructures.keys
    }
}

interface Renderer {
    fun render(structure: Structure): String
}

class Java8Renderer(private val stringTemplate: String = "StringValue",
                    private val variableName: String = "tmp") : Renderer {

    override fun render(structure: Structure): String {
        return "${renderInitialization(structure)}${renderBuildStructure(structure)}"
    }

    private fun renderInitialization(structure: Structure): String {
        return "${structure.name} $variableName = "
    }

    private fun renderBuildStructure(structure: Structure): String {

        var output = ""
        if (structure is SimpleStructure) {
            output += renderSimpleStructure(structure) + ";"
        } else if (structure is ComplexStructure) {
            output += "new ${structure.name}();\n"

            for (field in structure.fields) {
                if (field.structure is SimpleStructure) {
                    output += "$variableName.${field.name} = ${renderSimpleStructure(field.structure)};\n"
                }
            }
        }

        return output
    }

    private fun renderSimpleStructure(structure: SimpleStructure): String {

        if (structure.name == "String") {
            return "\"$stringTemplate\""
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
                        Field("firstName", kb.getStructure("String")!!),
                        Field("lastName", kb.getStructure("String")!!)
                )))

        val outputBox = TextArea()
        outputBox.isReadOnly = true

        val rootCombo = ComboBox<String>()
        rootCombo.setItems(kb.structureKeys())
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
        val structure = kb.getStructure(rootType)
        return Java8Renderer().render(structure!!)
    }
}