/**
 * This file is part of Princess, a theorem prover for Presburger
 * arithmetic with uninterpreted predicates.
 * <http://www.philipp.ruemmer.org/princess.shtml>
 *
 * Copyright (C) 2013-2017 Philipp Ruemmer <ph_r@gmx.net>
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

package ap.proof.theoryPlugins;

import ap.basetypes.IdealInt
import ap.theories.Theory
import ap.proof.goal.{Goal, Task, EagerTask, PrioritisedTask}
import ap.proof.tree.{ProofTree, ProofTreeFactory}
import ap.proof.certificates._
import ap.terfor.Formula
import ap.terfor.conjunctions.{Conjunction, Quantifier}
import ap.terfor.linearcombination.LinearCombination
import ap.parameters.Param
import ap.util.Debug

import scala.collection.mutable.{Stack, ArrayBuffer}

object Plugin {
  protected[theoryPlugins] val AC = Debug.AC_PLUGIN

  abstract sealed class Action

  /**
   * Add a formula to the handled proof goal. This action does not
   * support generation of proof certificates.
   */
  case class AddFormula  (formula : Conjunction)    extends Action

  /**
   * Remove some facts from the handled proof goal.
   */
  case class RemoveFacts (facts : Conjunction)      extends Action
  
  /**
   * Split a proof goal into multiple sub-goals. This action does not
   * support generation of proof certificates.
   */
  case class SplitGoal   (cases : Seq[Seq[Action]]) extends Action
  
  /**
   * Split a proof goal into multiple sub-goals, and justify the split
   * through an explicit theory axiom. The action can specify a list of
   * assumptions that are antecedents of the axiom, but already assumed
   * to be present in a goal. Constants in the axiom will be replaced
   * with universally quantified variables.
   */
  case class AxiomSplit  (assumptions : Seq[Formula],
                          cases : Seq[(Conjunction, Seq[Action])],
                          theory : Theory)          extends Action
  
  /**
   * Schedule a task to be applied later on the goal.
   */
  case class ScheduleTask(proc : TheoryProcedure,
                          priority : Int)           extends Action

  object GoalState extends Enumeration {
    val Intermediate, Final = Value
  }
}

/**
 * General interface for a theory-specific procedure that
 * can be applied by a prover to reason about interpreted symbols.
 */
trait TheoryProcedure {

  import Plugin.GoalState

  /**
   * Apply this procedure to the given goal.
   */
  def handleGoal(goal : Goal) : Seq[Plugin.Action]

  /**
   * Try to determine in which state a given goal is.
   */
  def goalState(goal : Goal) : GoalState.Value =
    if (goal.tasks.finalEagerTask) GoalState.Final else GoalState.Intermediate

}

////////////////////////////////////////////////////////////////////////////////

/**
 * Interface for theory plugins that can be added to the
 * <code>EagerTaskManager</code>. At the moment, such plugins
 * can mainly observe which formulae are asserted on a branch,
 * and then generate/instantiate further axioms to add
 * theory knowledge.
 *
 * Plugin objects have to be immutable.
 */
trait Plugin extends TheoryProcedure {

  /**
   * Given the current goal, possible generate (local and global) axioms.
   */
  def generateAxioms(goal : Goal) : Option[(Conjunction, Conjunction)]

  /**
   * Apply this plugin to the given goal. The default procedure
   * is to call <code>generateAxioms</code>, and possibly add further
   * facts or axioms to the goal.
   */
  def handleGoal(goal : Goal) : Seq[Plugin.Action] =
    generateAxioms(goal) match {
      case Some((localAxioms, globalAxioms)) => {
        val allAxioms = Conjunction.conj(List(localAxioms, globalAxioms),
                                         goal.order).negate
        List(Plugin.AddFormula(allAxioms))
      }
      case None =>
        List()
    }

  /**
   * Check whether the formulas in the given goal are satisfiable,
   * and if yes generate a model. The returned
   * formula is meant to replace the goal facts in this case.
   */
  def generateModel(goal : Goal) : Option[Conjunction] = None

}

////////////////////////////////////////////////////////////////////////////////

object PluginSequence {
  def apply(plugins : Seq[Plugin]) : Option[Plugin] = plugins match {
    case Seq()       => None
    case Seq(plugin) => Some(plugin)
    case plugins     => {
      val flatPlugins =
        for (p <- plugins;
             q <- p match {
               case p : PluginSequence => p.plugins
               case p => List(p)
             })
        yield q
      Some(new PluginSequence(flatPlugins))
    }
  }
}

/**
 * Execution of a sequence of plugins.
 */
class PluginSequence private (val plugins : Seq[Plugin]) extends Plugin {

  // not used
  def generateAxioms(goal : Goal) : Option[(Conjunction, Conjunction)] =
    throw new UnsupportedOperationException

  override def handleGoal(goal : Goal) : Seq[Plugin.Action] = {
    val it = plugins.iterator
    var res : Seq[Plugin.Action] = List()
    while (res.isEmpty && it.hasNext)
      res = it.next handleGoal goal
    res
  }

  /**
   * Check whether the formulas in the given goal are satisfiable,
   * and if yes generate a model. The returned
   * formula is meant to replace the goal facts in this case.
   */
  override def generateModel(goal : Goal) : Option[Conjunction] = {
    val it = plugins.iterator
    var res : Option[Conjunction] = None
    while (!res.isDefined && it.hasNext)
      res = it.next generateModel goal
    res
  }

}

////////////////////////////////////////////////////////////////////////////////

/**
 * Task integrating a <code>Plugin</code> (or <code>TheoryProcedure</code>)
 * into a prover
 */
abstract class PluginTask(plugin : TheoryProcedure) extends Task {
  import Plugin._

  def apply(goal : Goal, ptf : ProofTreeFactory) : ProofTree = {
    val actions = plugin handleGoal goal

    if (actions.isEmpty)
      ptf.updateGoal(goal)
    else if (Param.PROOF_CONSTRUCTION(goal.settings))
      handleActionsRec(actions.toList, List(), goal, goal.branchInferences, ptf)
    else
      handleActionsNonCert(actions, goal, ptf)
  }

  //////////////////////////////////////////////////////////////////////////////

  private def handleActionsRec(actions : List[Action],
                               linearActions : List[Action],
                               goal : Goal,
                               branchInferences : BranchInferenceCollection,
                               ptf : ProofTreeFactory) : ProofTree =
    actions match {

      case List() =>
        applyActions(linearActions, goal, branchInferences, ptf)

      case List(AxiomSplit(assumptions, cases, theory)) => {
        val order = goal.order

        // TODO: avoid conversion to conjunction
        val certAssumptions =
          for (a <- assumptions) yield CertFormula(Conjunction.conj(a, order))
        val (compoundAssumptions, simpleAssumptions) =
          certAssumptions partition (_.isInstanceOf[CertCompoundFormula])

        val subTrees =
          (for (a <- compoundAssumptions) yield {
             applyActions(AddFormula(a.toConj) :: linearActions,
                          goal,
                          goal startNewInferenceCollectionCert List(!a),
                          ptf)
           }) ++
          (for ((axiomCase, rest) <- cases) yield {
             handleActionsRec(rest.toList,
                              AddFormula(!axiomCase) :: linearActions,
                              goal,
                              goal startNewInferenceCollection List(axiomCase),
                              ptf)
           })

        val providedFormulas =
          (for (a <- compoundAssumptions) yield Set(!a)) ++
          (for ((axiomCase, _) <- cases) yield Set(CertFormula(axiomCase)))

        assert(subTrees.size >= 2)

        // certificate constructor, to be applied once all sub-goals have been
        // closed
        def comb(certs : Seq[Certificate]) : Certificate = {
          // add proofs for the simple assumptions
          val simpleCerts =
            for (a <- simpleAssumptions) yield {
              val inf = a match {
                // TODO: further cases
                case eq : CertEquation =>
                  ReduceInference(List((IdealInt.MINUS_ONE, eq)), !eq, order)
              }
              
              val ccert =
                CloseCertificate(Set(inf.providedFormulas.head), order)
              (!a, BranchInferenceCertificate.prepend(List(inf), ccert, order))
            }

          val allCerts =
            simpleCerts ++ ((providedFormulas map (_.head)) zip certs)
          val betaCert = BetaCertificate(allCerts, order)

          val instAxiom = betaCert.localAssumedFormulas.head

          val consts = order sort instAxiom.constants
            
          val (axiom, instInf) =
            if (consts.isEmpty) {
              (instAxiom, List())
            } else {
              val axiomConj =
                Conjunction.quantify(Quantifier.ALL, consts,
                                     instAxiom.toConj, order)
              val axiom =
                CertFormula(axiomConj).asInstanceOf[CertCompoundFormula]
              val instanceTerms =
                for (c <- consts) yield LinearCombination(c, order)
              (axiom,
               List(GroundInstInference(axiom, instanceTerms, instAxiom,
                                        List(), instAxiom, order)))
            }
                                   
          BranchInferenceCertificate.prepend(
              List(TheoryAxiomInference(axiom, theory)) ++ instInf,
              betaCert, order)
        }

        val pCert =
          PartialCertificate(comb _, providedFormulas, branchInferences, order)
        ptf.andInOrder(subTrees, pCert, goal.vocabulary)
      }
      
      case (a@(_ : RemoveFacts | _ : ScheduleTask)) :: rest =>
        handleActionsRec(rest, a :: linearActions, goal, branchInferences, ptf)

      case actions =>
        throw new IllegalArgumentException("cannot execute actions " + actions)
    }

  //////////////////////////////////////////////////////////////////////////////
  
  private def handleActionsNonCert(actions : Seq[Action],
                                   goal : Goal,
                                   ptf : ProofTreeFactory) : ProofTree =
    actions.last match {
      case _ : SplitGoal | _ : AxiomSplit => {
        val actionStack    = new Stack[Seq[Action]]
        val resultingTrees = new ArrayBuffer[ProofTree]

        actionStack push actions

        while (!actionStack.isEmpty) {
          val actions = actionStack.pop
          if (actions.isEmpty) {
            resultingTrees += ptf.updateGoal(goal)
          } else actions.last match {
            case SplitGoal(subActions) => {
              val otherActions = actions.init
              for (b <- subActions.reverseIterator)
                actionStack push (otherActions ++ b)
            }
            case AxiomSplit(_, cases, _) => {
              val otherActions = actions.init
              for ((axiomCase, rest) <- cases.reverseIterator)
                actionStack push (otherActions ++
                                  List(AddFormula(!axiomCase)) ++
                                  rest)
            }
            case _ => {
              resultingTrees += applyActions(actions, goal, null, ptf)
            }
          }
        }

        ptf.andInOrder(resultingTrees, goal.vocabulary)
      }

      case _ =>
        applyActions(actions, goal, null, ptf)
    }

  //////////////////////////////////////////////////////////////////////////////
  
  private def applyActions(actions : Seq[Action],
                           goal : Goal,
                           branchInferences : BranchInferenceCollection,
                           ptf : ProofTreeFactory) : ProofTree = {
    //-BEGIN-ASSERTION-/////////////////////////////////////////////////////////
    Debug.assertInt(Plugin.AC, !(actions exists (_.isInstanceOf[SplitGoal])))
    //-END-ASSERTION-///////////////////////////////////////////////////////////

    val factsToRemove =
      Conjunction.conj(for (RemoveFacts(f) <- actions.iterator) yield f,
                       goal.order)

    val tasksToSchedule =
      (for (ScheduleTask(proc, priority) <- actions.iterator)
       yield new PrioritisedPluginTask(proc, priority, goal.age)).toList

    val formulaTasks =
      // if no inferences are given, we assume that proof generation is
      // disabled, so we are free to simplify
      if (branchInferences == null) {
        val factsToAdd =
          goal.reduceWithFacts(
            Conjunction.disj(for (AddFormula(f) <- actions.iterator) yield f,
                             goal.order))
        goal formulaTasks factsToAdd
      } else {
        for (AddFormula(f) <- actions; t <- goal formulaTasks f)
        yield t
      }

    val newFacts =
      if (factsToRemove.isTrue)
        goal.facts
      else
        goal.facts -- factsToRemove

    val allFormulaTasks =
      if (formulaTasks.isEmpty &&
          (actions exists {
             case AddFormula(_) | RemoveFacts(_) => true
             case _ => false
           }) &&
          !newFacts.isTrue) {
        // we have to make sure that the plugin is called a a further time,
        // otherwise we get very confusing semantics
        // just add a formula that we already know about
        goal formulaTasks !newFacts.iterator.next
      } else {
        formulaTasks
      }

    if (branchInferences == null)
      ptf.updateGoal(newFacts,
                     tasksToSchedule ++ allFormulaTasks,
                     goal)
    else
      ptf.updateGoal(newFacts,
                     tasksToSchedule ++ allFormulaTasks,
                     branchInferences,
                     goal)
  }
}

////////////////////////////////////////////////////////////////////////////////

class EagerPluginTask(plugin : TheoryProcedure)
      extends PluginTask(plugin) with EagerTask {
  override def toString = "EagerPluginTask(" + plugin + ")"
}

////////////////////////////////////////////////////////////////////////////////

class PrioritisedPluginTask(plugin : TheoryProcedure,
                            basePriority : Int,
                            age : Int)
      extends PluginTask(plugin) with PrioritisedTask {

  val priority : Int = basePriority + age
 
  /**
   * Update the task with possibly new information from the goal.
   * Currently, this does not modify the theory procedure.
   */
  def updateTask(goal : Goal, factCollector : Conjunction => Unit)
                                                   : Seq[PrioritisedTask] =
    List(this)

  override def toString = "PrioritisedPluginTask(" + plugin + ")"
}
