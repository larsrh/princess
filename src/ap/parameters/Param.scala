/**
 * This file is part of Princess, a theorem prover for Presburger
 * arithmetic with uninterpreted predicates.
 * <http://www.philipp.ruemmer.org/princess.shtml>
 *
 * Copyright (C) 2009 Philipp Ruemmer <ph_r@gmx.net>
 *
 * Princess is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Princess is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Princess.  If not, see <http://www.gnu.org/licenses/>.
 */

package ap.parameters;

object Param {
  
  case object LOGO extends Param {
    type Value = Boolean
    val defau : Boolean = true
  }
  
  case object ASSERTIONS extends Param {
    type Value = Boolean
    val defau : Boolean = true
  }
  
  case object PRINT_TREE extends Param {
    type Value = Boolean
    val defau : Boolean = false
  }

  case object PRINT_SMT_FILE extends Param {
    type Value = String
    val defau : String = ""
  }
  
  object ConstraintSimplifierOptions extends Enumeration {
    val None, Fair, Lemmas = Value
  }
  case object SIMPLIFY_CONSTRAINTS extends Param {
    type Value = ConstraintSimplifierOptions.Value
    val defau : ConstraintSimplifierOptions.Value = ConstraintSimplifierOptions.Fair
  }
  
  case object TRACE_CONSTRAINT_SIMPLIFIER extends Param {
    type Value = Boolean
    val defau : Boolean = false
  }
  
  case object MOST_GENERAL_CONSTRAINT extends Param {
    type Value = Boolean
    val defau : Boolean = false
  }
  
  // turn ground constraints into disjunctive normal form
  case object DNF_CONSTRAINTS extends Param {
    type Value = Boolean
    val defau : Boolean = true
  }
  
  case object TIMEOUT extends Param {
    type Value = Int
    val defau : Int = Math.MAX_INT
  }

  // resolve negative predicate literals in clauses with positive facts
  case object POS_UNIT_RESOLUTION extends Param {
    type Value = Boolean
    val defau : Boolean = true
  }
  
  object ClausifierOptions extends Enumeration {
    val None, Simple = Value
  }
  case object CLAUSIFIER extends Param {
    type Value = ClausifierOptions.Value
    val defau : ClausifierOptions.Value = ClausifierOptions.None
  }

  object TriggerGenerationOptions extends Enumeration {
    val None, Total, All = Value
  }
  case object TRIGGER_GENERATION extends Param {
    type Value = TriggerGenerationOptions.Value
    val defau : TriggerGenerationOptions.Value = TriggerGenerationOptions.Total
  }
  
  case object TRIGGER_GENERATOR_CONSIDERED_FUNCTIONS extends Param {
    type Value = Set[ap.parser.IFunction]
    val defau : Set[ap.parser.IFunction] = Set()
  }
  
  case object SYMBOL_WEIGHTS extends Param {
    type Value = ap.proof.goal.SymbolWeights
    val defau : ap.proof.goal.SymbolWeights = ap.proof.goal.SymbolWeights.DEFAULT
  }

  // even split propositional formulae that do not contain quantifiers or
  // eliminated constants
  case object FULL_SPLITTING extends Param {
    type Value = Boolean
    val defau : Boolean = false
  }
  
  case object CONSTRAINT_SIMPLIFIER extends Param {
    type Value = ap.proof.ConstraintSimplifier
    val defau : ap.proof.ConstraintSimplifier =
      ap.proof.ConstraintSimplifier.FAIR_SIMPLIFIER
  }
  
  case object PROOF_CONSTRUCTION extends Param {
    type Value = Boolean
    val defau : Boolean = false
  }
}

abstract class Param {
  
  type Value
  
  val defau : Value
  
  def apply[A <: Settings[A]](settings : Settings[A]) : Value =
    settings(this).asInstanceOf[Value]
  
  def set[A <: Settings[A]](settings : Settings[A], v : Value) : A =
    settings + (this, v)
    
}
