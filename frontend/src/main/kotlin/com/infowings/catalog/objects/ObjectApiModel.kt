package com.infowings.catalog.objects

import com.infowings.catalog.aspects.getAllAspects
import com.infowings.catalog.common.*
import com.infowings.catalog.common.objekt.ObjectCreateRequest
import com.infowings.catalog.common.objekt.PropertyCreateRequest
import com.infowings.catalog.common.objekt.ValueCreateRequest
import com.infowings.catalog.common.objekt.ValueCreateResponse
import com.infowings.catalog.utils.get
import com.infowings.catalog.utils.post
import kotlinx.coroutines.experimental.launch
import kotlinx.serialization.json.JSON
import react.*

suspend fun getAllObjects(): List<ObjectData> = JSON.parse(get("/api/objects/all"))

suspend fun saveObject(objData: ObjectData): ObjectData = JSON.parse(post("/api/object/save", JSON.stringify(objData)))

suspend fun createObject(request: ObjectCreateRequest): ObjectData {
    console.log(request)
    val value = post("/api/object/create", JSON.stringify(request))
    return JSON.parse(post("/api/object/create", JSON.stringify(request)))
}

suspend fun createObject(data: ObjectData): ObjectData {
    val name = data.name ?: throw IllegalStateException("name is not defined")
    val subjectId = data.subject.id ?: throw IllegalStateException("subject id is not defined")

    return createObject(ObjectCreateRequest(name, data.description, subjectId, data.subject.version))
}


suspend fun createProperty(request: PropertyCreateRequest): ObjectPropertyData =
    JSON.parse(post("/api/object/createProperty", JSON.stringify(request)))

suspend fun createProperty(objectId: String, data: ObjectPropertyData): ObjectPropertyData {
    val name = data.name ?: throw IllegalStateException("name is not defined")
    val aspectId = data.aspect.id ?: throw IllegalStateException("aspect id is not defined")

    return createProperty(PropertyCreateRequest(objectId, name, data.cardinality, aspectId))
}

suspend fun createValue(request: ValueCreateRequest): ValueCreateResponse =
    JSON.parse(post("/api/object/createValue", JSON.stringify(request.toDTO())))


interface ObjectApiModel {
    suspend fun submitObj(objData: ObjectData)
}

interface ObjectApiConsumerProps : RProps {
    var objList: List<ObjectData>
    var objMap: Map<String, ObjectData>
    var aspectMap: Map<String, AspectData>
    var objectApiModel: ObjectApiModel
}

class ObjectApiModelComponent : RComponent<RProps, ObjectApiModelComponent.State>(), ObjectApiModel {

    override fun componentDidMount() {
//        fetchObjects()
        launch {
            val aspects = getAllAspects(emptyList())
            setState {
                objList = emptyList()
                objMap = HashMap()
                aspectMap = aspects.aspects.associateBy { it.id ?: error("Client received aspects with id == null") }
            }
        }
    }

//    private fun fetchObjects() {
//        launch {
//            val response = getAllObjects()
//            setState {
//                objList = response
//            }
//        }
//    }

    private suspend fun processChild(current: AspectPropertyValueData, parentId: String, property: ObjectPropertyData) {
        val scalarText = current.scalarValue
        val propertyId = property.id

        if (scalarText == null) {
            throw IllegalStateException("scalar value is not defined")
        }

        if (propertyId == null) {
            throw IllegalStateException("property id is not defined")
        }

        val scalarValue = convertValue(current.aspectProperty, scalarText)
                ?: throw IllegalStateException("cound not process scala value")

        val vcr = ValueCreateRequest(
            value = scalarValue, objectPropertyId = propertyId, parentValueId = parentId,
            aspectPropertyId = current.aspectProperty.id, measureId = null
        )
        val valueResponse: ValueCreateResponse = createValue(vcr)

        current.children.forEach { processChild(it, valueResponse.id, property) }
    }

    private fun convertValue(objectProperty: ObjectPropertyData, aspect: AspectData, value: String?): ObjectValueData? =
        when {
            value == null -> if (objectProperty.cardinality == PropertyCardinality.ZERO.name) ObjectValueData.NullValue else null
            aspect.baseType == BaseType.Text.name -> ObjectValueData.StringValue(value)
            aspect.baseType == BaseType.Integer.name ->
                ObjectValueData.IntegerValue(value.toInt(), 0)
            aspect.baseType == BaseType.Decimal.name ->
                ObjectValueData.DecimalValue(value)
            else -> null
        }


    private fun convertValue(property: AspectPropertyDataExtended, value: String): ObjectValueData? {
        return when {
            property.aspectBaseType == BaseType.Text.name -> ObjectValueData.StringValue(value)
            property.aspectBaseType == BaseType.Integer.name -> ObjectValueData.IntegerValue(value.toInt(), 0)
            property.aspectBaseType == BaseType.Decimal.name -> ObjectValueData.DecimalValue(value)
            else -> null
        }
    }

    override suspend fun submitObj(objData: ObjectData) {
        val response = createObject(objData)
        val createdObjectId = response.id
        createdObjectId ?: throw IllegalStateException("id of created object is not specified")

        objData.properties.forEach {
            val prop = it
            val createdProperty = createProperty(createdObjectId, prop)
            val createdPropertyId =
                createdProperty.id ?: throw IllegalStateException("id of created property is not specified")

            prop.values.forEach {
                val valueText = it.scalarValue ?: throw IllegalStateException("scalar value is not specified: $it")
                val valueData = convertValue(createdProperty, createdProperty.aspect, valueText)
                        ?: throw IllegalStateException("could not create value data")

                val createRequest = ValueCreateRequest(value = valueData, objectPropertyId = createdPropertyId)

                val valueResponse: ValueCreateResponse = createValue(createRequest)

                it.children.forEach {
                    processChild(it, valueResponse.id, createdProperty)
                }
            }
        }

//        setState {
//            objList += response
//        }
        objData.id?.let {
            TODO("UPDATING DOES NOT WORK YET")
        } ?: setState {
            val newObjDataId = objList.size.toString()
            val newObjData = objData.copy(id = newObjDataId)
            objList += newObjData
            objMap[newObjDataId] = newObjData
        }
    }

    override fun RBuilder.render() {
        objectTreeViewModel {
            attrs {
                objList = state.objList
                objMap = state.objMap
                aspectMap = state.aspectMap
                objectApiModel = this@ObjectApiModelComponent
            }
        }
    }

    interface State : RState {
        var objList: List<ObjectData>
        var objMap: MutableMap<String, ObjectData>
        var aspectMap: Map<String, AspectData>
    }
}

val RBuilder.objectApiModel
    get() = child(ObjectApiModelComponent::class) {}