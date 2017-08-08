/**
 * This file is part of Princess, a theorem prover for Presburger
 * arithmetic with uninterpreted predicates.
 * <http://www.philipp.ruemmer.org/princess.shtml>
 *
 * Copyright (C) 2009-2017 Philipp Ruemmer <ph_r@gmx.net>
 *
 * Princess is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * Princess is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Princess.  If not, see <http://www.gnu.org/licenses/>.
 */

package ap.util;

import scala.collection.concurrent.TrieMap

/**
 * Naive implementation of a thread-safe LRU cache.
 */
class LRUCache[K, V] (maxEntries : Int) {

  private val backend = new TrieMap[K, V]
  
  def get(k : K) : Option[V] = backend get k
  
  def apply(k : K)(otherwise : => V) : V = (this get k) match {
    case None => {
      val res = otherwise
      put(k, res)
      res
    }
    case Some(res) => res
  }
  
  def cached(k : K)(otherwise : => V)(cachePostProcessing : V => V) : V =
    (this get k) match {
      case None => {
        val res = otherwise
        put(k, res)
        res
      }
      case Some(res) =>
        cachePostProcessing(res)
    }
  
  def +=(pair : (K, V)) : Unit = {
    if (backend.size >= maxEntries) backend.clear
    backend += pair
  }

  def put(k : K, v : V) : Unit = {
    if (backend.size >= maxEntries) backend.clear
    backend.put(k, v)
  }

}
