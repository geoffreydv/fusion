package be.geoffrey.fusion

data class QName(val namespace: String, val name: String)

interface PossiblePartOfGroup

interface ElementBase {

    fun getDisplayName(): String

    fun getStructureReference(): QName
}

data class TopLevelElement(val name: QName, val elementType: QName) : ElementBase {
    override fun getDisplayName(): String {
        return name.name
    }

    override fun getStructureReference(): QName {
        return elementType
    }
}

data class ComplexType(val name: QName,
                       val children: List<StructureElement> = listOf(),
                       val abstract: Boolean = false,
                       val extensionOf: QName? = null) : Structure {
    override fun getQName(): QName {
        return name
    }
}

interface StructureElement

interface FieldGroup : StructureElement {
    fun allElements(): List<StructureElement>
}

data class SequenceOfElements(private val elements: List<StructureElement> = listOf()) : FieldGroup {
    override fun allElements(): List<StructureElement> {
        return elements
    }
}

data class ChoiceOfElements(private val elements: List<StructureElement> = listOf()) : FieldGroup {
    override fun allElements(): List<StructureElement> {
        return elements
    }
}

abstract class SimpleField(private val name: QName) : Structure {
    override fun getQName(): QName {
        return name
    }
}

data class Element(val name: String,
                   val elementType: QName) : PossiblePartOfGroup, ElementBase, StructureElement {

    override fun getDisplayName(): String {
        return name
    }

    override fun getStructureReference(): QName {
        return elementType
    }
}

data class RegexField(private val name: QName,
                      val pattern: String) : SimpleField(name)

data class IntField(private val name: QName) : SimpleField(name)

data class DecimalField(private val name: QName) : SimpleField(name)

data class DateTimeField(private val name: QName) : SimpleField(name)

data class BooleanField(private val name: QName) : SimpleField(name)

data class StringField(private val name: QName) : SimpleField(name)

data class EnumField(private val name: QName, val possibleValues: List<String>) : SimpleField(name)

data class Base64Field(private val name: QName) : SimpleField(name)

interface Structure {
    fun getQName(): QName
}

class NoFields : StructureElement

class ElementStack {
    private val elements: MutableList<ElementBase> = mutableListOf()

    private fun isEmpty() = elements.isEmpty()

    fun size() = elements.size

    fun push(item: ElementBase) = elements.add(item)

    fun pop(): ElementBase? {
        val item = elements.lastOrNull()
        if (!isEmpty()) {
            elements.removeAt(elements.size - 1)
        }
        return item
    }

    fun visualizePath(): String {
        return "/" + elements.joinToString("/") {
            when (it) {
                is TopLevelElement -> it.name.name
                is Element -> it.name
                else -> throw IllegalArgumentException("Unknown type")
            }
        }
    }

    override fun toString(): String = elements.toString()

    fun recursionWillStartWhenAdding(element: Element,
                                     maxDepth: Int = 2): Boolean {

        val previousOccurrences = elements.count {
            it.getStructureReference() == element.elementType
        }

        return previousOccurrences >= maxDepth
    }
}

const val XMLNS = "http://www.w3.org/2001/XMLSchema"

class XmlBuildingBlocks : KnownBuildingBlocks(listOf(
        StringField(QName(XMLNS, "string")),
        BooleanField(QName(XMLNS, "boolean")),
        IntField(QName(XMLNS, "int")),
        IntField(QName(XMLNS, "integer")),
        DecimalField(QName(XMLNS, "decimal")),
        DecimalField(QName(XMLNS, "double")),
        DateTimeField(QName(XMLNS, "dateTime")),
        Base64Field(QName(XMLNS, "base64Binary")))
)

open class KnownBuildingBlocks(defaultStructures: Collection<Structure> = listOf()) {

    private val knownStructures = hashMapOf<QName, Structure>()
    private val knownElements = hashMapOf<QName, TopLevelElement>()

    init {
        for (defaultStructure in defaultStructures) {
            add(defaultStructure)
        }
    }

    fun add(structure: Structure) {
        knownStructures[structure.getQName()] = structure
    }

    fun add(element: TopLevelElement) {
        knownElements[element.name] = element
    }

    fun addAllOfOther(other: KnownBuildingBlocks) {
        for (structure in other.knownStructures.values) {
            add(structure)
        }

        for (element in other.knownElements.values) {
            add(element)
        }
    }

    fun getElement(name: QName): TopLevelElement? {
        return knownElements[name]
    }

    fun getStructure(name: QName): Structure? {
        return knownStructures[name]
    }

    fun getStructureByPartOfName(namespace: String, partOfName: String): Structure? {
        knownStructures.forEach { (qn, structure) ->
            if (qn.namespace == namespace && qn.name.contains(partOfName)) {
                return structure
            }
        }

        return null
    }

    fun getConcreteImplementationsFor(name: QName): List<ComplexType> {
        return knownStructures.values
                .filterIsInstance(ComplexType::class.java)
                .filter { it.extensionOf == name }
    }

    fun getParentTypesFor(structure: ComplexType): List<ComplexType> {

        var structToCheck = structure
        val results = mutableListOf<ComplexType>()

        while (structToCheck.extensionOf != null) {
            val base = getStructure(structToCheck.extensionOf!!) as ComplexType
            results.add(base)
            structToCheck = base
        }

        return results.reversed()
    }

    fun getKnownElements(): List<TopLevelElement> {
        return knownElements.values.toList()
    }
}