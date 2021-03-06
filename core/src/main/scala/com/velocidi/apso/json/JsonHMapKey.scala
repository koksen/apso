package com.velocidi.apso.json

import spray.json._

import com.velocidi.apso.collection.HMapKey

/**
 * A key of a `JsonHMap`.
 * @constructor creates a new key and adds it to a registry.
 * @param sym the JSON key associated with this map key
 * @param reg the registry with which this key is to be associated
 * @param jsonFormat a `JsonFormat` which enables a key to serialize and
 *        deserialize its associated value
 * @tparam V the type of the value associated with this key
 */
@deprecated("This will be removed in a future version", "2017/07/13")
abstract class JsonHMapKey[V](val sym: Symbol)(implicit reg: JsonKeyRegistry, jsonFormat: JsonFormat[V]) extends HMapKey[V] {
  reg.keys += (sym -> this)

  override def toKey: JsonHMapKey[Value] = this

  /**
   * Returns a stable copy of this `HMapKey` without the JSON support. Useful for creating serializable equivalent
   * keys.
   *
   * @return a stable copy of this `HMapKey` without the JSON support.
   */
  lazy val toHMapKey: HMapKey[Value] = new HMapKey[Value] {}

  /**
   * Converts an object of this key's value type to JSON.
   * @param v the object to convert
   * @return the given object as a JSON value.
   */
  def toJson(v: Value): JsValue = jsonFormat.write(v)

  /**
   * Converts a JSON value to an object of this key's value type.
   * @param v the JSON value to convert
   * @return the given JSON value as an object.
   */
  def toValue(v: JsValue): Value = jsonFormat.read(v)

  override def toString: String = sym.toString()
}

/**
 * Companion object for `JsonHMapKey`.
 */
object JsonHMapKey {

  /**
   * Creates a new `JsonHMapKey` by wrapping a normal `HMapKey`.
   *
   * @param key the key to wrap
   * @param sym the JSON key associated with this map key
   * @tparam V the type of the value associated with this key
   * @return a new `JsonHMapKey` that wraps the given `HMapKey`.
   */
  def wrap[V](key: HMapKey[V], sym: Symbol)(implicit reg: JsonKeyRegistry, jsonFormat: JsonFormat[V]) =
    new JsonHMapKey[V](sym) { override lazy val toHMapKey = key }
}
