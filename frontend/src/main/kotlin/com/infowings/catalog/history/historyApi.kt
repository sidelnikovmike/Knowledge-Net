package com.infowings.catalog.history

import com.infowings.catalog.common.*
import com.infowings.catalog.utils.get
import kotlinx.serialization.json.JSON
import kotlin.js.Date

suspend fun getAllEvents(): List<HistoryData<*>> {
    println("get all events")
    val t1 = Date.now()
    val aspectEvents = getAllAspectEvents()
    val t2 = Date.now()
    val refBookEvents = getAllRefBookEvents()
    val t3 = Date.now()
    val objectEvents = getAllObjectEvents()
    val t4 = Date.now()
    val subjectEvents = getAllSubjectEvents()
    val t5 = Date.now()


    /* Здесь нам надо смреджить два сортированных списка. Полагаемся на то, что используется реализация merge sort,
     * умеющая распознавать отсортированные подсписки. Кажется в JDK так и есть, а котлиновские коллекции - обертки
      * над JDK. Если это вдруг не так, поменяем */
    val res = (aspectEvents + refBookEvents + objectEvents + subjectEvents).sortedByDescending { it.event.timestamp }
    val t6 = Date.now()

    val times = listOf(t1, t2, t3, t4, t5, t6)
    println("times: " + times)

    println("time ranges: " + times.zipWithNext().map { it.second - it.first })
    println("total: " + (t6 - t1))

    return res
}

suspend fun getAllAspectEvents(): List<AspectHistory> =
    JSON.nonstrict.parse<AspectHistoryList>(get("/api/history/aspects")).history

suspend fun getAllRefBookEvents(): List<RefBookHistory> =
    JSON.nonstrict.parse<RefBookHistoryList>(get("/api/history/refbook")).history

suspend fun getAllObjectEvents(): List<ObjectHistory> =
    JSON.nonstrict.parse<ObjectHistoryList>(get("/api/history/objects")).history

suspend fun getAllSubjectEvents(): List<SubjectHistory> =
    JSON.nonstrict.parse<SubjectHistoryList>(get("/api/history/subjects")).history
