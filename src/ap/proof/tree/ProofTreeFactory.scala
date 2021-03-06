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

package ap.proof.tree;

import ap.proof._
import ap.proof.goal.{PrioritisedTask, Goal, TaskManager, CompoundFormulas}
import ap.proof.certificates.{BranchInferenceCollection, PartialCertificate}
import ap.terfor.{Formula, ConstantTerm, TermOrder}
import ap.terfor.arithconj.ModelElement
import ap.terfor.conjunctions.{Conjunction, NegatedConjunctions, Quantifier}
import ap.terfor.substitutions.Substitution

abstract class ProofTreeFactory {

  def and(subtrees : Seq[ProofTree], vocabulary : Vocabulary) : ProofTree =
    and(subtrees, null, vocabulary)

  def and(subtrees : Seq[ProofTree],
          partialCertificate : PartialCertificate,
          vocabulary : Vocabulary) : ProofTree
  
  def andInOrder(subtrees : Seq[ProofTree],
                 vocabulary : Vocabulary) : ProofTree =
    andInOrder(subtrees, null, vocabulary)

  def andInOrder(subtrees : Seq[ProofTree],
                 partialCertificate : PartialCertificate,
                 vocabulary : Vocabulary) : ProofTree
  
  def quantify(subtree : ProofTree,
               quan : Quantifier,
               quantifiedConstants : Seq[ConstantTerm],
               vocabulary : Vocabulary,
               subtreeOrder : TermOrder) : ProofTree
  
  def weaken(subtree : ProofTree,
             disjunct : Conjunction,
             vocabulary : Vocabulary) : ProofTree

  def strengthen(subtree : ProofTree,
                 conjunct : Conjunction,
                 vocabulary : Vocabulary) : ProofTree

  /**
   * A callback to tell that a constant has been eliminated. Upon elimination,
   * it must be possible to provide a witness, i.e., a solution
   * for the eliminated symbols must be computable.
   */
  def eliminatedConstant(subtree : ProofTree,
                         m : ModelElement,
                         vocabulary : Vocabulary) : ProofTree
             
  //////////////////////////////////////////////////////////////////////////////
  
  def updateGoal(updatedFacts : Conjunction,
                 updatedCompoundFormulas : CompoundFormulas,
                 updatedElimConstants : Set[ConstantTerm],
                 updatedVocabulary : Vocabulary,
                 updatedDefinedSyms : Substitution,
                 newTasks : Iterable[PrioritisedTask],
                 branchInferences : BranchInferenceCollection,
                 goal : Goal) : ProofTree

  def updateGoal(updatedFacts : Conjunction,
                 updatedCompoundFormulas : CompoundFormulas,
                 updatedElimConstants : Set[ConstantTerm],
                 updatedVocabulary : Vocabulary,
                 updatedDefinedSyms : Substitution,
                 updatedTasks : TaskManager,
                 branchInferences : BranchInferenceCollection,
                 goal : Goal) : ProofTree

  def updateGoal(updatedElimConstants : Set[ConstantTerm],
                 updatedVocabulary : Vocabulary,
                 newTasks : Iterable[PrioritisedTask],
                 branchInferences : BranchInferenceCollection,
                 goal : Goal) : ProofTree =
    updateGoal(goal.facts, goal.compoundFormulas, updatedElimConstants,
               updatedVocabulary, goal.definedSyms sortBy updatedVocabulary.order,
               newTasks, branchInferences, goal)

  def updateGoal(updatedElimConstants : Set[ConstantTerm],
                 updatedVocabulary : Vocabulary,
                 newTasks : Iterable[PrioritisedTask],
                 goal : Goal) : ProofTree =
    updateGoal(goal.facts, goal.compoundFormulas, updatedElimConstants,
               updatedVocabulary, goal.definedSyms sortBy updatedVocabulary.order,
               newTasks, goal.branchInferences, goal)

  def updateGoal(updatedFacts : Conjunction,
                 newTasks : Iterable[PrioritisedTask],
                 goal : Goal) : ProofTree =
    updateGoal(updatedFacts, goal.compoundFormulas, goal.eliminatedConstants,
               goal.vocabulary, goal.definedSyms, newTasks,
               goal.branchInferences, goal)

  def updateGoal(updatedFacts : Conjunction,
                 newTasks : Iterable[PrioritisedTask],
                 branchInferences : BranchInferenceCollection,
                 goal : Goal) : ProofTree =
    updateGoal(updatedFacts, goal.compoundFormulas, goal.eliminatedConstants,
               goal.vocabulary, goal.definedSyms, newTasks,
               branchInferences, goal)

  def updateGoal(updatedFacts : Conjunction,
                 newTasks : TaskManager,
                 branchInferences : BranchInferenceCollection,
                 goal : Goal) : ProofTree =
    updateGoal(updatedFacts, goal.compoundFormulas, goal.eliminatedConstants,
               goal.vocabulary, goal.definedSyms, newTasks,
               branchInferences, goal)

  def updateGoal(updatedFacts : Conjunction, goal : Goal) : ProofTree =
    updateGoal(updatedFacts, goal.compoundFormulas, goal.eliminatedConstants,
               goal.vocabulary, goal.definedSyms, List(),
               goal.branchInferences, goal)

  def updateGoal(updatedFacts : Conjunction,
                 branchInferences : BranchInferenceCollection,
                 goal : Goal) : ProofTree =
    updateGoal(updatedFacts, goal.compoundFormulas, goal.eliminatedConstants,
               goal.vocabulary, goal.definedSyms, List(),
               branchInferences, goal)

  def updateGoal(updatedFacts : Conjunction,
                 updatedVocabulary : Vocabulary,
                 branchInferences : BranchInferenceCollection,
                 goal : Goal) : ProofTree =
    updateGoal(updatedFacts, goal.compoundFormulas, goal.eliminatedConstants,
               updatedVocabulary, goal.definedSyms, List(),
               branchInferences, goal)

  def updateGoalAddQFClause(clause : Conjunction, goal : Goal) : ProofTree = {
    val newClauses = NegatedConjunctions(goal.compoundFormulas.qfClauses.iterator ++
                                         Iterator.single(clause),
                                         goal.order)
    updateGoal(goal.compoundFormulas updateQFClauses newClauses, goal)
  }
  
  def updateGoal(updatedCompoundFormulas : CompoundFormulas,
                 goal : Goal) : ProofTree =
    updateGoal(goal.facts, updatedCompoundFormulas, goal.eliminatedConstants,
               goal.vocabulary, goal.definedSyms, List(),
               goal.branchInferences, goal)

  def updateGoal(updatedFacts : Conjunction,
                 updatedCompoundFormulas : CompoundFormulas,
                 newTasks : Iterable[PrioritisedTask],
                 goal : Goal) : ProofTree =
    updateGoal(updatedFacts, updatedCompoundFormulas, goal.eliminatedConstants,
               goal.vocabulary, goal.definedSyms, newTasks,
               goal.branchInferences, goal)

  def updateGoal(updatedCompoundFormulas : CompoundFormulas,
                 newTasks : Iterable[PrioritisedTask],
                 goal : Goal) : ProofTree =
    updateGoal(goal.facts, updatedCompoundFormulas, goal.eliminatedConstants,
               goal.vocabulary, goal.definedSyms, newTasks,
               goal.branchInferences, goal)

  def updateGoal(updatedCompoundFormulas : CompoundFormulas,
                 newTasks : Iterable[PrioritisedTask],
                 branchInferences : BranchInferenceCollection,
                 goal : Goal) : ProofTree =
    updateGoal(goal.facts, updatedCompoundFormulas, goal.eliminatedConstants,
               goal.vocabulary, goal.definedSyms, newTasks,
               branchInferences, goal)

  def updateGoal(updatedCompoundFormulas : CompoundFormulas,
                 newTasks : Iterable[PrioritisedTask],
                 updatedVocabulary : Vocabulary,
                 goal : Goal) : ProofTree =
    updateGoal(goal.facts, updatedCompoundFormulas, goal.eliminatedConstants,
               updatedVocabulary, goal.definedSyms, newTasks,
               goal.branchInferences, goal)

  def updateGoal(newTasks : Iterable[PrioritisedTask], goal : Goal) : ProofTree =
    updateGoal(goal.facts, goal.compoundFormulas, goal.eliminatedConstants,
               goal.vocabulary, goal.definedSyms, newTasks,
               goal.branchInferences, goal)

  def updateGoal(newTasks : Iterable[PrioritisedTask],
                 branchInferences : BranchInferenceCollection,
                 goal : Goal) : ProofTree =
    updateGoal(goal.facts, goal.compoundFormulas, goal.eliminatedConstants,
               goal.vocabulary, goal.definedSyms, newTasks,
               branchInferences, goal)

  def updateGoal(goal : Goal) : ProofTree =
    updateGoal(goal.facts, goal.compoundFormulas, goal.eliminatedConstants,
               goal.vocabulary, goal.definedSyms, List(),
               goal.branchInferences, goal)

  def updateGoal(tasks : TaskManager, goal : Goal) : ProofTree =
    updateGoal(goal.facts, goal.compoundFormulas, goal.eliminatedConstants,
               goal.vocabulary, goal.definedSyms, tasks,
               goal.branchInferences, goal)

}
