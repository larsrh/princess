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

package ap.terfor.substitutions;

import ap.terfor._
import ap.basetypes.IdealInt
import ap.terfor.linearcombination.LinearCombination
import ap.terfor.equations.{EquationConj, NegEquationConj}
import ap.terfor.inequalities.InEqConj
import ap.util.Debug

object PseudoDivSubstitution {

  private val AC = Debug.AC_SUBSTITUTIONS
  
}

/**
 * Trait for substitutions that can also replace constants or variables with
 * coefficient, like <code>n * c</code>. This is done through
 * pseudo-division if necessary (and possible)
 */
abstract class PseudoDivSubstitution extends Substitution {

  /**
   * The subclasses can specify both the coefficient of the variable or constant
   * that is supposed to be replaced and the actual replacement. I.e.,
   * for <code>applyToVariable(v)==(n, t)</code> the described substitution is
   * <code> n * v |-> t </code> 
   */
  protected[substitutions] def applyToVariable(v : VariableTerm) : (IdealInt, Term)

  protected[substitutions] def applyToConstant(c : ConstantTerm) : (IdealInt, Term)

  /**
   * Application to terms is not supported, because it would not be possible to
   * do pseudo-division
   */
  def apply(t : Term) : Term =
    throw new UnsupportedOperationException
        
  def apply(lc : LinearCombination) : LinearCombination =
    throw new UnsupportedOperationException

  protected[substitutions] def pseudoApply(lc : LinearCombination) : LinearCombination = {
    // for each term of the linear combination, compute the replacing term and
    // the new rational coefficient in form of two <code>IdealInt</code>s
    val repls = for ((coeff, ter) <- lc.pairSeq) yield {
                  val (replDenom, repl) = ter match {
                    case c : ConstantTerm => applyToConstant(c)
                    case v : VariableTerm => applyToVariable(v)
                    case OneTerm => (IdealInt.ONE, OneTerm)
                  }
       
                  //-BEGIN-ASSERTION-///////////////////////////////////////////
                  Debug.assertInt(PseudoDivSubstitution.AC, !replDenom.isZero)
                  //-END-ASSERTION-/////////////////////////////////////////////
                         
                  if (replDenom.isOne) {
                    (coeff, IdealInt.ONE, repl)
                  } else {
                    // we return the numerator and the denominator of the
                    // coefficient of the replacing term
                    val gcd = coeff gcd replDenom
                    (coeff / gcd, replDenom / gcd, repl)
                  }
                }
    
    val denomLcm = IdealInt.lcm(for (r <- repls.iterator) yield (r _2))

    //-BEGIN-ASSERTION-/////////////////////////////////////////////////////////
    Debug.assertInt(PseudoDivSubstitution.AC, denomLcm.signum > 0)
    //-END-ASSERTION-///////////////////////////////////////////////////////////

    LinearCombination(for ((num, denom, repl) <- repls.iterator)
                      yield (num * (denomLcm / denom), repl),
                      order)
  }

}
