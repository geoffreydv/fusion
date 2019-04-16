package be.geoffrey.fusion

import be.geoffrey.fusion.ContentType.DEFINITION
import be.geoffrey.fusion.ContentType.ELEMENT
import java.util.*

private const val NAMESPACE_INDICATION = "namespace_definition"

data class QName(val namespace: String, val name: String)

enum class ContentType {
    ELEMENT,
    DEFINITION
}

interface Indexable {
    fun getQName(): QName

    fun getContentType(): ContentType
}

interface PossiblePartOfGroup

// Maybe add "top-level" element?

data class Element(val name: String,
                   val type: QName) : PossiblePartOfGroup

data class TopLevelElement(val name: QName, val type: QName) : Indexable {

    override fun getQName(): QName {
        return name
    }

    override fun getContentType(): ContentType {
        return ELEMENT
    }
}

interface Restriction

data class MinLengthRestriction(val minLength: Int) : Restriction

data class EnumRestriction(val value: String) : Restriction

data class SimpleType(private val name: QName,
                      private val baseType: QName,
                      val restrictions: List<Restriction>) : Indexable {

    override fun getContentType(): ContentType {
        return DEFINITION
    }

    override fun getQName(): QName {
        return name
    }
}

data class ComplexType(val name: QName,
                       val fields: List<Element>) : PossiblePartOfGroup, Indexable {

    override fun getContentType(): ContentType {
        return DEFINITION
    }

    override fun getQName(): QName {
        return name
    }
}

class TypeDb(entries: Collection<Indexable> = listOf()) {

    private val knownTypesAsMap = hashMapOf<ContentType, HashMap<QName, Indexable>>(
            Pair(ELEMENT, hashMapOf()),
            Pair(DEFINITION, hashMapOf()))

    init {
        addEntries(entries)
    }

    fun addEntries(entries: Collection<Indexable>) {
        for (entry in entries) {
            knownTypesAsMap[entry.getContentType()]!![entry.getQName()] = entry
        }
    }

    fun addEntries(other: TypeDb) {
        for (value in other.knownTypesAsMap.values) {
            addEntries(value.values)
        }
    }

    fun getElement(name: QName): TopLevelElement? {
        return getEntry(name, ELEMENT) as TopLevelElement
    }

    fun getType(name: QName): ComplexType? {
        return getEntry(name, ELEMENT) as ComplexType
    }

    fun getEntry(name: QName, type: ContentType = DEFINITION): Indexable? {
        return knownTypesAsMap[type]!![name]
    }

    fun getEntryByPartOfName(namespace: String,
                             partOfName: String,
                             contentType: ContentType): List<Indexable> {

        val results = mutableListOf<Indexable>()

        knownTypesAsMap[contentType]!!.forEach { (_, type) ->
            if (type.getQName().namespace == namespace && type.getQName().name.contains(partOfName)) {
                results.add(type)
            }
        }

        return results
    }
}