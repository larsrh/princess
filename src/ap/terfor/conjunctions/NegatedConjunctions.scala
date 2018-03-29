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

package ap.terfor.conjunctions;

import ap.terfor._
import ap.terfor.linearcombination.LinearCombination
import ap.terfor.arithconj.ArithConj
import ap.terfor.preds.{Atom, Predicate, PredConj}
import ap.util.{Debug, Logic, Seqs, Timeout}

object NegatedConjunctions {
  
  val AC = Debug.AC_PROP_CONNECTIVES

  def apply(conj : Conjunction, order : TermOrder) : NegatedConjunctions =
    apply(Iterator.single(conj), order)
  
  def apply(conjs : Iterable[Conjunction], order : TermOrder)
                                                : NegatedConjunctions =
    apply(conjs.iterator, order)

  def apply(conjs : Iterator[Conjunction], order : TermOrder)
                                                : NegatedConjunctions = {
    var compareCnt = 0
    def compareConjs(c1 : Conjunction, c2 : Conjunction) = {
      compareCnt = compareCnt + 1
      if (compareCnt % 100 == 0)
        Timeout.check
      Conjunction.compare(c1, c2, order) > 0
    }

    Seqs.filterAndSort[Conjunction](conjs, c => c.isFalse, c => c.isTrue,
                                    c => c, compareConjs _) match {
      case Seqs.FilteredSorted(sortedConjs) => {
        val contractedConjs = Seqs.removeDuplicates(sortedConjs).toArray
        new NegatedConjunctions (contractedConjs, order)      
      }
      case Seqs.FoundBadElement(_) => FALSE
    }
  }

  val TRUE : NegatedConjunctions =
    new NegatedConjunctions (Array(), TermOrder.EMPTY)

  lazy val FALSE : NegatedConjunctions =
    new NegatedConjunctions (Array(Conjunction.TRUE), TermOrder.EMPTY)
  
}

/**
 * Class for representing a conjunction of negated <code>Conjunction</code>s.
 */
class NegatedConjunctions private (private val conjs : Array[Conjunction],
                                   val order : TermOrder) 
                          extends Formula with SortedWithOrder[NegatedConjunctions]
                                          with IndexedSeq[Conjunction] {

  //-BEGIN-ASSERTION-///////////////////////////////////////////////////////////
  Debug.assertCtor(NegatedConjunctions.AC,
                   Logic.forall(for (conj <- this.iterator)
                                yield ((conj isSortedBy order) && !conj.isFalse))
                   &&
                   (
                     // to be able to represent <code>NegatedConjunctions.FALSE</code>,
                     // we allow singleton conjunctions whose only conjunct is true
                     this.size == 1 && this(0) == Conjunction.TRUE
                     ||
                     Logic.forall(for (conj <- this.iterator) yield !conj.isTrue)
                   )
                   &&
                   Logic.forall(0, conjs.size - 1,
                                (i) => Conjunction.compare
                                        (conjs(i), conjs(i+1), order) > 0)
                 )
  //-END-ASSERTION-/////////////////////////////////////////////////////////////

  def sortBy(newOrder : TermOrder) : NegatedConjunctions = {
    if (isSortedBy(newOrder)) {
      this
    } else {
      NegatedConjunctions(for (conj <- this.iterator) yield (conj sortBy newOrder),
                          newOrder)
    }
  }

  //////////////////////////////////////////////////////////////////////////////

  def length : Int = conjs.length
   
  def apply(i : Int) : Conjunction = conjs(i)
   
  def elements = conjs.iterator

  //////////////////////////////////////////////////////////////////////////////

  def update(newConjs : Iterable[Conjunction],
             newOrder : TermOrder) : NegatedConjunctions =
    if (Seqs.identicalSeqs(this, newConjs))
      this
    else
      NegatedConjunctions(newConjs, newOrder)
  
  def updateSubset(newConjs : Iterable[Conjunction],
                   newOrder : TermOrder) : NegatedConjunctions =
    if (this.size == newConjs.size)
      this
    else
      new NegatedConjunctions(newConjs.toArray, newOrder)

  def &(that : NegatedConjunctions)
       (implicit newOrder : TermOrder) : NegatedConjunctions =
    if (that.isEmpty)
      this
    else
      NegatedConjunctions.apply(this.iterator ++ that.iterator, newOrder)
  
  //////////////////////////////////////////////////////////////////////////////

  def implies(that : NegatedConjunctions) : Boolean =
    // TODO: make this more efficient
    ((that diff this) _2).isEmpty
    
  //////////////////////////////////////////////////////////////////////////////

  lazy val variables : Set[VariableTerm] =
    Set.empty ++ (for (conj <- conjs.iterator; v <- conj.variables.iterator) yield v)

  lazy val constants : Set[ConstantTerm] =
    Set.empty ++ (for (conj <- conjs.iterator; c <- conj.constants.iterator) yield c)

  lazy val predicates : Set[Predicate] =
    Set.empty ++ (for (conj <- conjs.iterator; p <- conj.predicates.iterator) yield p)

  lazy val groundAtoms : Set[Atom] =
    Set.empty ++ (for (conj <- conjs.iterator; g <- conj.groundAtoms.iterator) yield g)

  //////////////////////////////////////////////////////////////////////////////

  def isTrue : Boolean = (this.isEmpty)

  def isFalse : Boolean = (!this.isEmpty && this(0).isTrue)

  //////////////////////////////////////////////////////////////////////////////

  /**
   * Find the subset of conjuncts in this conjunction that also occur in
   * <code>oldConj</code>, as well as the subset of conjuncts that do not occur
   * in <code>oldConj</code>.
   */
  def diff(oldConj : NegatedConjunctions)
                        : (NegatedConjunctions, NegatedConjunctions) = {
    //-BEGIN-ASSERTION-/////////////////////////////////////////////////////////
    Debug.assertPre(NegatedConjunctions.AC, oldConj isSortedBy order)
    //-END-ASSERTION-///////////////////////////////////////////////////////////

    implicit val orderConjunctions = Conjunction.conjOrdering(order)

    val (unchanged, changed) = Seqs.diff(this, oldConj)
    (this.updateSubset(unchanged, order), this.updateSubset(changed, order))
  }
  
  //////////////////////////////////////////////////////////////////////////////

  def --(that : NegatedConjunctions) : NegatedConjunctions =
    if (that.isTrue)
      this
    else
      (this diff that)._2

  //////////////////////////////////////////////////////////////////////////////

  def containsLiteral : Boolean =
    Logic.exists(for (conj <- this.iterator) yield conj.isLiteral)

  def containsNegatedConjunction : Boolean =
    Logic.exists(for (conj <- this.iterator) yield conj.isNegatedConjunction)
    
  def isNegatedQuantifiedConjunction : Boolean =
    (this.size == 1 && !this(0).quans.isEmpty)
    
  //////////////////////////////////////////////////////////////////////////////

  override def toString : String = {
    if (isTrue) {
      "true"
    } else if (isFalse) {
      "false"
    } else {
      val strings = for (conj <- this.iterator) yield ("! " + conj)
      if (strings.hasNext)
        strings.reduceLeft((s1 : String, s2 : String) =>
                           s1 + " & " + s2)
      else
        throw new Error // should never be reached
    }
  }

  override def equals(that : Any) : Boolean = that match {
    case that : NegatedConjunctions => this.conjs sameElements that.conjs
    case _ => false
  }
  
  private lazy val hashCodeVal = Seqs.computeHashCode(this, 9871621, 5)

  override def hashCode = hashCodeVal

}
