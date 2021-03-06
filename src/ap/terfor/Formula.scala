/**
 * This file is part of Princess, a theorem prover for Presburger
 * arithmetic with uninterpreted predicates.
 * <http://www.philipp.ruemmer.org/princess.shtml>
 *
 * Copyright (C) 2009-2011 Philipp Ruemmer <ph_r@gmx.net>
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

package ap.terfor;

import ap.terfor.preds.Atom

import scala.reflect.ClassTag

object Formula {
  
  /**
   * Check whether the given sequence of formulas contains unsatisfiable
   * elements (in this case, a singleton sequence containing only this element
   * is returned), and otherwise remove all tautologies from it.
   */
  def filterForConj[A <: Formula]
                   (fors : Iterator[A]) : IndexedSeq[A] = {
    if (!fors.hasNext) return IndexedSeq.empty
    
    val nonTrivialFors = Vector.newBuilder[A]
    while (fors.hasNext) {
      val c = fors.next
      if (c.isFalse) return Vector(c)
      if (!c.isTrue) nonTrivialFors += c
    }
    
    nonTrivialFors.result
  }
  
  def conj[A <: Formula : ClassTag]
          (fors : Iterator[A],
           trueFor : A, comb : (IndexedSeq[A]) => A) : A = {
    val nonTrivialFors = filterForConj(fors)
    
    nonTrivialFors.size match {
      case 0 => trueFor
      case 1 => nonTrivialFors(0)
      case _ => comb(nonTrivialFors)
    }
  }
}

abstract class Formula extends TerFor {
  
  /** Return <code>true</code> if this formula is obviously always true */
  def isTrue : Boolean
  
  /** Return <code>true</code> if this formula is obviously always false */
  def isFalse : Boolean
  
  def groundAtoms : Set[Atom]

}
