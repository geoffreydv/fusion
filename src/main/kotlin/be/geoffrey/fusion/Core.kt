package be.geoffrey.fusion

data class QName(val namespace: String, val name: String)

interface Trackable {
    fun shortName(): String
}

interface ElementBase : Trackable {
    fun getDisplayName(): String
    fun getStructureReference(): QName?
}

data class TopLevelElement(val name: QName, val elementType: QName) : ElementBase {

    override fun shortName(): String {
        return name.name
    }

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

interface StructureElement : Trackable {
    fun minOccurs(): Int
    fun maxOccurs(): Int
}

interface FieldGroup : StructureElement {
    fun allElements(): List<StructureElement>
}

data class SequenceOfElements(
        private val elements: List<StructureElement> = listOf(),
        val minOccurs: Int = 1,
        val maxOccurs: Int = 1) : FieldGroup {

    override fun minOccurs(): Int {
        return minOccurs
    }

    override fun maxOccurs(): Int {
        return maxOccurs
    }

    override fun shortName(): String = "Sequence"

    override fun allElements(): List<StructureElement> {
        return elements
    }
}

data class ChoiceOfElements(private val elements: List<StructureElement> = listOf(),
                            val minOccurs: Int = 1,
                            val maxOccurs: Int = 1) : FieldGroup {

    override fun minOccurs(): Int {
        return minOccurs
    }

    override fun maxOccurs(): Int {
        return maxOccurs
    }

    override fun shortName(): String = "Choice"

    override fun allElements(): List<StructureElement> {
        return elements
    }
}

data class ElementReference(val ref: QName,
                            val minOccurs: Int = 1,
                            val maxOccurs: Int = 1) : ElementBase, StructureElement {
    override fun shortName(): String {
        return ref.name
    }

    override fun getDisplayName(): String {
        return ref.name
    }

    override fun getStructureReference(): QName? {
        return null
    }

    override fun minOccurs(): Int {
        return minOccurs
    }

    override fun maxOccurs(): Int {
        return maxOccurs
    }
}

data class Element(val name: String,
                   val elementType: QName,
                   val minOccurs: Int = 1,
                   val maxOccurs: Int = 1) : ElementBase, StructureElement {

    override fun minOccurs(): Int {
        return minOccurs
    }

    override fun maxOccurs(): Int {
        return maxOccurs
    }

    override fun shortName(): String {
        return name
    }

    override fun getDisplayName(): String {
        return shortName()
    }

    override fun getStructureReference(): QName? {
        return elementType
    }
}

abstract class SimpleType(private val name: QName) : Structure {
    override fun getQName(): QName {
        return name
    }
}

data class RegexField(private val name: QName, val pattern: String) : SimpleType(name)

data class IntField(private val name: QName) : SimpleType(name)

data class DecimalField(private val name: QName) : SimpleType(name)

data class DateTimeField(private val name: QName) : SimpleType(name)

data class BooleanField(private val name: QName) : SimpleType(name)

data class StringField(private val name: QName) : SimpleType(name)

data class EnumField(private val name: QName, val possibleValues: List<String>) : SimpleType(name)

data class Base64Field(private val name: QName) : SimpleType(name)

data class NotSpecifiedSimpleType(private val name: QName,
                                  val extensionOf: QName,
                                  val restrictions: List<String>? = null) : SimpleType(name)

interface Structure {
    fun getQName(): QName
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

    private val knownStructures = linkedMapOf<QName, Structure>()
    private val knownElements = linkedMapOf<QName, TopLevelElement>()

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

    fun getStructureByNsAndPartOfName(namespace: String, partOfName: String): Structure? {
        knownStructures.forEach { (qn, structure) ->
            if (qn.namespace == namespace && qn.name.contains(partOfName)) {
                return structure
            }
        }

        return null
    }

    fun getElementByPartOfName(partOfName: String): TopLevelElement? {
        knownElements.forEach { (qn, structure) ->
            if (qn.name.contains(partOfName)) {
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