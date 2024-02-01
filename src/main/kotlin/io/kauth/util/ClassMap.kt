package io.kauth.util

import kotlin.reflect.KClass

interface ClassMap {

    fun <T : Any> get(key: KClass<T>): IO<T?>

    companion object {
        val new = IO { ClassMap() }
    }

}

interface MutableClassMap : ClassMap {

    fun <T : Any> set(key: KClass<out T>, value: T): IO<Unit>

    companion object {
        val new = IO { MutableClassMap() }
    }

}

fun ClassMap(
    map: Map<KClass<*>, Any> = mapOf()
): ClassMap =
    object : ClassMap {
        override fun <T : Any> get(key: KClass<T>): IO<T?> = IO {
            map[key] as T?
        }
    }

fun MutableClassMap(
    map: MutableMap<KClass<*>, Any> = mutableMapOf(),
    classMap: ClassMap = ClassMap(map)
): MutableClassMap =
    object : MutableClassMap, ClassMap by classMap {
        override fun <T : Any> set(key: KClass<out T>, value: T): IO<Unit> = IO {
            map[key] = value
        }
    }