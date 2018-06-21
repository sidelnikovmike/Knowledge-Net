package com.infowings.catalog.common

import kotlinx.serialization.Serializable

@Serializable
data class AspectsList(
    val aspects: List<AspectData> = emptyList()
)

enum class PropertyCardinality {
    ZERO {
        override val label = "Group"
    },
    ONE {
        override val label = "0..1"
    },
    INFINITY {
        override val label = "0..∞"
    };

    abstract val label: String
}

@Serializable
data class AspectData(
    val id: String? = null,
    val name: String? = null,
    val measure: String? = null,
    val domain: String? = null,
    val baseType: String? = null,
    val properties: List<AspectPropertyData> = emptyList(),
    val version: Int = 0,
    val subject: SubjectData? = null,
    val deleted: Boolean = false,
    val description: String? = null,
    val lastChangeTimestamp: Long? = null,
    val refBookName: String? = null
) {
    operator fun get(id: String): AspectPropertyData? = properties.find { it.id == id }
}

/* AspectData для работы с аспектами, прочитанными из базы кажется недобной тем, что
 id и name там - nullable, хотя по факту они всегда обязаны быть
 */

@Serializable
data class AspectReadData(
    val id: String,
    val name: String,
    val measure: String? = null,
    val domain: String? = null,
    val baseType: String? = null,
    val properties: List<AspectPropertyData> = emptyList(),
    val version: Int = 0,
    val subject: SubjectData? = null,
    val deleted: Boolean = false,
    val description: String? = null,
    val lastChangeTimestamp: Long?,
    val refBookName: String? = null
) {
    operator fun get(id: String): AspectPropertyData? = properties.find { it.id == id }

    fun toAspectData() = AspectData(
        id = id, name = name, measure = measure, domain = domain, baseType = baseType,
        properties = properties, version = version, subject = subject, deleted = deleted, description = description,
        lastChangeTimestamp = lastChangeTimestamp, refBookName = refBookName
    )
}


@Serializable
data class AspectPropertyData(
    val id: String,
    val name: String,
    val aspectId: String,
    val cardinality: String,
    val description: String?,
    val version: Int = 0,
    val deleted: Boolean = false
)

/** Data about AspectProperty together with data about relevant aspect */
@Serializable
data class AspectPropertyDataExtended(
    val id: String,
    val name: String,
    val aspectId: String,
    val cardinality: String,
    val aspectName: String,
    val aspectMeasure: String?,
    val aspectDomain: String,
    val aspectBaseType: String,
    val refBookName: String?
)

/** Helpful extensions */
val emptyAspectData: AspectData
    get() = AspectData(null, "", null, null, null)

val emptyAspectPropertyData: AspectPropertyData
    get() = AspectPropertyData("", "", "", "", null)

fun AspectData.actualData() = copy(properties = properties.filter { !it.deleted })