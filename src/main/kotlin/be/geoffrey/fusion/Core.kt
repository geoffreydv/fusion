package be.geoffrey.fusion

data class QName(val namespace: String, val name: String)

interface PossiblePartOfGroup

interface ElementBase {

    fun getDisplayName(): String

    fun getStructureReference(): QName
}

data class Element(val name: String,
                   val elementType: QName) : PossiblePartOfGroup, ElementBase {

    override fun getDisplayName(): String {
        return name
    }

    override fun getStructureReference(): QName {
        return elementType
    }
}

data class TopLevelElement(val name: QName, val elementType: QName) : ElementBase {
    override fun getDisplayName(): String {
        return name.name
    }

    override fun getStructureReference(): QName {
        return elementType
    }
}

data class RegexField(private val name: QName,
                      val pattern: String) : SimpleField(name) {
}

data class NumberField(private val name: QName) : SimpleField(name)

data class BooleanField(private val name: QName) : SimpleField(name)

data class StringField(private val name: QName) : SimpleField(name)

data class UnknownField(private val name: QName, private val type: QName) : SimpleField(name)

data class EnumField(private val name: QName, val possibleValues: List<String>) : SimpleField(name)

interface Structure {
    fun getQName(): QName
}

abstract class SimpleField(private val name: QName) : Structure {
    override fun getQName(): QName {
        return name
    }
}

data class GroupOfSimpleFields(val name: QName,
                               val fields: List<Element>,
                               val abstract: Boolean = false,
                               val extensionOf: QName? = null) : Structure {
    override fun getQName(): QName {
        return name
    }
}

class ElementStack {
    private val elements: MutableList<ElementBase> = mutableListOf()

    fun isEmpty() = elements.isEmpty()

    fun size() = elements.size

    fun push(item: ElementBase) = elements.add(item)

    fun pop(): ElementBase? {
        val item = elements.lastOrNull()
        if (!isEmpty()) {
            elements.removeAt(elements.size - 1)
        }
        return item
    }

    fun peek(): ElementBase? = elements.lastOrNull()

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
        NumberField(QName(XMLNS, "int")))
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

    fun getConcreteImplementationsFor(name: QName): List<GroupOfSimpleFields> {
        return knownStructures.values
                .filterIsInstance(GroupOfSimpleFields::class.java)
                .filter { it.extensionOf == name }
    }
}