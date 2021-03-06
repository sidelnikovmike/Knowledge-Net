package com.infowings.catalog.data.history

import com.infowings.catalog.auth.user.UserVertex
import com.infowings.catalog.common.*
import com.infowings.catalog.common.EventType
import com.infowings.catalog.common.SnapshotData
import com.infowings.catalog.common.history.refbook.RefBookHistoryData
import com.infowings.catalog.common.Range
import com.infowings.catalog.common.history.objekt.ObjectHistoryData
import com.infowings.catalog.data.objekt.ScalarTypeTag
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.id.ORecordId
import com.orientechnologies.orient.core.record.OVertex
import java.util.*

data class Split(val added: Set<String>, val removed: Set<String>, val changed: Set<String>)

data class DiffPayload(
    val data: Map<String, String>,
    val addedLinks: Map<String, List<ORID>>,
    val removedLinks: Map<String, List<ORID>>
) : DataAware {
    private fun <T> valueOrEmptyList(map: Map<String, List<T>>, key: String): List<T> = map[key] ?: emptyList()

    private fun <T> singleOf(map: Map<String, List<T>>, key: String): T? {
        val links = valueOrEmptyList(map, key)
        if (links.size > 1) {
            throw IllegalStateException("too many elements for key $key: $links")
        }

        return links.firstOrNull()
    }

    override fun dataItem(key: String): String? = data[key]

    fun isEmpty() = data.isEmpty() && addedLinks.isEmpty() && removedLinks.isEmpty()

    fun classifyLinks(): Split {
        val added = addedLinks.keys
        val removed = removedLinks.keys
        val changed = added.intersect(removed)
        return Split(added = added.minus(changed), removed = removed.minus(changed), changed = changed)
    }

    fun addedFor(target: String): List<ORID> = valueOrEmptyList(addedLinks, target)
    fun removedFor(target: String): List<ORID> = valueOrEmptyList(removedLinks, target)

    fun addedSingleFor(target: String): ORID? = singleOf(addedLinks, target)
    fun removedSingleFor(target: String): ORID? = singleOf(removedLinks, target)

    constructor() : this(emptyMap(), emptyMap(), emptyMap())

    fun toData(): DiffPayloadData = DiffPayloadData(data = data,
        addedLinks = addedLinks.mapValues { it.value.map { it.toString() } },
        removedLinks = removedLinks.mapValues { it.value.map { it.toString() } }
    )

    fun linksOfType(type: String): Set<ORID> {
        val added = addedLinks[type] ?: emptyList()
        val removed = removedLinks[type] ?: emptyList()

        return (added + removed).toSet()
    }
}

interface DataAware {
    fun dataItem(key: String): String?

    fun dataOrEmpty(key: String) = dataItem(key) ?: ""
}

data class MutableSnapshot(
    val data: MutableMap<String, String>,
    val links: MutableMap<String, MutableSet<ORID>>
) : DataAware {
    fun apply(diff: DiffPayload) {
        diff.data.forEach { updateField(it.key, it.value) }
        diff.addedLinks.forEach { addLinks(it.key, it.value) }
        diff.removedLinks.forEach { removeLinks(it.key, it.value) }
    }

    fun <T> resolvedLink(key: String, resolver: (String) -> T): T? {
        return links[key]?.firstOrNull()?.let { resolver(it.toString()) }
    }

    override fun dataItem(key: String): String? = data[key]

    private fun updateField(key: String, value: String) {
        data[key] = value
    }

    private fun addLink(target: String, link: ORID) {
        links.computeIfAbsent(target) { mutableSetOf() }.add(link)
    }

    private fun removeLink(target: String, link: ORID) {
        if (target in links) {
            links[target]?.remove(link)
            if (links[target]?.size == 0) links.remove(target)
        }
    }

    private fun addLinks(target: String, toAdd: List<ORID>) {
        links.computeIfAbsent(target, { mutableSetOf() }).addAll(toAdd)
    }

    private fun removeLinks(target: String, toRemove: List<ORID>) {
        if (target in links) {
            links[target]?.removeAll(toRemove)
            if (links[target]?.size == 0) links.remove(target)
        }
    }

    fun immutable() = Snapshot(data.toMap(), links.mapValues { it.value.toList() }.toMap())

    constructor() : this(mutableMapOf<String, String>(), mutableMapOf<String, MutableSet<ORID>>())
}

data class Snapshot(
    val data: Map<String, String>,
    val links: Map<String, List<ORID>>
) : DataAware {
    constructor() : this(emptyMap(), emptyMap())

    constructor(data: SnapshotData) : this(data.data, data.links.mapValues { it.value.map{ ORecordId(it) } })

    override fun dataItem(key: String): String? = data[key]

    fun toMutable() = MutableSnapshot(data.toMutableMap(), links.mapValues { it.value.toMutableSet() }.toMutableMap())

    fun toSnapshotData() = SnapshotData(data, links.mapValues { it.value.map { it.toString() } })
}

/* Метаданные о событии в жизни сущности. Ккаждый экземпляр соответствует одной высокоуровневой операции
 на уровне сервиса
  Вариант для записи события - некоторые поля представлены как vertex
  */
data class HistoryEventWrite(
    val userVertex: UserVertex,
    val timestamp: Long,
    val version: Int,
    val type: EventType,
    val entityVertex: OVertex,
    val entityClass: String,
    val sessionId: UUID
)

/* Исторический факт. Состоит из метаданных (event) и данных о внесенных изменениях (payload)
 */
data class HistoryFactWrite(
    val event: HistoryEventWrite,
    val payload: DiffPayload
)

data class HistoryFact(
    val event: HistoryEventData,
    val payload: DiffPayload
) {
    companion object {
        val empty = HistoryFact(HistoryEventData.empty, DiffPayload())
    }
}

fun List<HistoryFact>.linksOfType(type: String): Set<ORID> {
    return this.flatMap { fact ->
        fact.payload.linksOfType(type)
    }.toSet()
}

data class HistorySnapshot(
    val event: HistoryEventData,
    val before: Snapshot,
    val after: Snapshot,
    val diff: DiffPayload
) {
    fun toData(): HistorySnapshotData = HistorySnapshotData(
        event = event,
        before = before.toSnapshotData(),
        after = after.toSnapshotData(),
        diff = diff.toData()
    )
}

fun toHistoryFact(event: HistoryEventWrite, base: Snapshot, other: Snapshot) =
    HistoryFactWrite(event, diffSnapshots(base, other))

fun diffSnapshots(base: Snapshot, other: Snapshot): DiffPayload {
    // Предполагаем, что поля не выкидываются, но могут добавляться
    // выкинутое поле отследить не сложно, но его надо как-то особо в базе представить.
    // Без явной необходимости не хочется

    val updatedData = other.data
        .filter {
            it.value != base.data[it.key]
        }

    val addedLinks = other.links
        .mapValues { (linkType, links) ->
            links - (base.links.getOrElse(linkType, { emptyList() }))
        }.filterValues {
            it.isNotEmpty()
        }

    val removedLinks = base.links
        .mapValues {
            it.value - (other.links.getOrElse(it.key, { emptyList() }))
        }
        .filterValues {
            it.isNotEmpty()
        }

    return DiffPayload(updatedData, addedLinks = addedLinks, removedLinks = removedLinks)
}

fun <T> asStringOrEmpty(v: T?) = v?.toString().orEmpty()

class RefBookHistoryInfo {
    companion object {
        data class Header(
            val id: String,
            val snapshot: MutableSnapshot,
            val aspectName: String
        ) {
            fun toData() = RefBookHistoryData.Companion.Header(
                id = id,
                name = snapshot.data.getValue("value"),
                description = snapshot.data["description"],
                aspectId = snapshot.links.getValue("aspect").first().toString(),
                aspectName = aspectName
            )

        }

        data class Item(val id: String, val snapshot: MutableSnapshot) {
            fun toData() = RefBookHistoryData.Companion.Item(
                id = id,
                name = snapshot.data.getValue("value"),
                description = snapshot.data["description"]
            )
        }

        data class BriefState(val header: Header, val item: Item?) {
            fun toData() = RefBookHistoryData.Companion.BriefState(header.toData(), item?.toData())
        }
    }
}

class ObjectHistoryInfo {
    companion object {
        data class Objekt(
            val id: String,
            val snapshot: MutableSnapshot,
            val subjectName: String
        ) {
            fun toData() = ObjectHistoryData.Companion.Objekt(
                id = id,
                name = snapshot.data.getValue("name"),
                description = snapshot.data["description"],
                subjectId = snapshot.links.getValue("subject").first().toString(),
                subjectName = subjectName
            )

        }

        data class Property(val id: String, val snapshot: MutableSnapshot, val aspectName: String) {
            fun toData() = ObjectHistoryData.Companion.Property(
                id = id,
                name = snapshot.data["name"] ?: "",
                cardinality = snapshot.data.getValue("cardinality"),
                aspectId = snapshot.links.getValue("aspect").first().toString(),
                aspectName = aspectName
            )
        }

        data class Value(
            val id: String,
            val snapshot: MutableSnapshot,
            val subjectName: String?,
            val objectName: String?,
            val objectPropertyRefName: String?,
            val objectValueRefName: String?,
            val domainElement: String?,
            val measureName: String?,
            val aspectRefName: String?,
            val aspectPropertyRefName: String?,
            val aspectPropertyName: String?
        ) {
            fun toData(): ObjectHistoryData.Companion.Value {
                val typeTag: String? = snapshot.data["typeTag"]
                val repr = when (typeTag) {
                    ScalarTypeTag.STRING.name -> snapshot.data.getValue("strValue")
                    ScalarTypeTag.DECIMAL.name -> snapshot.data.getValue("decimalValue")
                    ScalarTypeTag.RANGE.name -> snapshot.data.getValue("range")
                    ScalarTypeTag.INTEGER.name -> snapshot.data.getValue("intValue")
                    ScalarTypeTag.SUBJECT.name -> {
                        subjectName ?: throw IllegalStateException("subject name is unknown")
                    }
                    ScalarTypeTag.OBJECT.name -> {
                        objectName ?: throw IllegalStateException("object name is unknown")
                    }
                    ScalarTypeTag.OBJECT_PROPERTY.name -> {
                        objectPropertyRefName ?: throw IllegalStateException("object property name is unknown")
                    }
                    ScalarTypeTag.OBJECT_VALUE.name -> {
                        objectValueRefName ?: throw IllegalStateException("object value name is unknown")
                    }
                    ScalarTypeTag.DOMAIN_ELEMENT.name -> {
                        domainElement ?: throw IllegalStateException("domain element is unknown")
                    }
                    ScalarTypeTag.ASPECT.name -> {
                        aspectRefName ?: throw IllegalStateException("aspect name is unknown")
                    }
                    ScalarTypeTag.ASPECT_PROPERTY.name -> {
                        aspectPropertyRefName ?: throw IllegalStateException("aspect property name is unknown")
                    }
                    else -> "???"
                }
                return ObjectHistoryData.Companion.Value(
                    id = id,
                    typeTag = snapshot.data.getValue("typeTag"),
                    repr = repr,
                    precision = snapshot.data["precision"],
                    measureName = measureName,
                    aspectPropertyId = snapshot.links["aspectProperty"]?.first()?.toString(),
                    aspectPropertyName = aspectPropertyName
                )
            }
        }

        data class BriefState(val objekt: Objekt, val property: Property?, val value: Value?) {
            fun toData() = ObjectHistoryData.Companion.BriefState(objekt.toData(), property?.toData(), value?.toData())
        }
    }
}

fun Range.asString() = "$left:$right"
