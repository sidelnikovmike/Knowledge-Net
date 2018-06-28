package com.infowings.catalog.common

import kotlinx.serialization.Serializable

@Serializable
data class ObjectsResponse(
    val objects: List<ObjectGetResponse>
)

@Serializable
data class ObjectGetResponse(
    val id: String,
    val name: String,
    val description: String?,
    val subjectId: String,
    val subjectName: String,
    val subjectDescription: String?,
    val propertiesCount: Int
)

@Serializable
data class DetailedObjectResponse(
    val id: String,
    val name: String,
    val description: String?,
    val subjectId: String,
    val subjectName: String,
    val subjectDescription: String?,
    val propertiesCount: Int,
    val objectProperties: List<DetailedObjectPropertyResponse>
)

@Serializable
data class DetailedObjectPropertyResponse(
    val id: String,
    val name: String?,
    val description: String?,
    val aspect: AspectData,
    val cardinality: String,
    val values: List<RootValueResponse>
)

@Serializable
data class RootValueResponse(
    val id: String,
    val value: ValueDTO,
    val children: List<ValueResponse>
)

@Serializable
data class ValueResponse(
    val id: String,
    val value: ValueDTO,
    val aspectProperty: AspectPropertyDataExtended,
    val children: List<ValueResponse>
)

@Serializable
data class ObjectData(
    val id: String? = null,
    val name: String? = null,
    val description: String?,
    val subject: SubjectData,
    val properties: List<ObjectPropertyData> = emptyList()
)

@Serializable
data class ObjectPropertyData(
    val id: String? = null,
    val name: String? = null,
    val cardinality: String,
    val aspect: AspectData,
    val values: List<ObjectPropertyValueData>
)

@Serializable
data class ObjectPropertyValueData(
    val id: String? = null,
    val scalarValue: String?,
    val children: List<AspectPropertyValueData>
)

@Serializable
data class AspectPropertyValueData(
    val id: String? = null,
    val scalarValue: String?,
    val aspectProperty: AspectPropertyDataExtended,
    val children: List<AspectPropertyValueData>
) {

    fun forEach(f: (AspectPropertyValueData, AspectPropertyValueData?) -> Unit, parent: AspectPropertyValueData?) {
        f(this, parent)
        children.forEach { it.forEach(f, this) }
    }
}