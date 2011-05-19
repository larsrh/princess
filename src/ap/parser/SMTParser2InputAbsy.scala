/**
 * This file is part of Princess, a theorem prover for Presburger
 * arithmetic with uninterpreted predicates.
 * <http://www.philipp.ruemmer.org/princess.shtml>
 *
 * Copyright (C) 2011 Philipp Ruemmer <ph_r@gmx.net>
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

package ap.parser;

import ap._
import ap.terfor.{ConstantTerm, OneTerm}
import ap.terfor.conjunctions.{Conjunction, Quantifier}
import ap.terfor.linearcombination.LinearCombination
import ap.terfor.equations.{EquationConj, NegEquationConj}
import ap.terfor.inequalities.InEqConj
import ap.terfor.preds.{Predicate, Atom}
import ap.util.{Debug, Logic, PlainRange}
import ap.basetypes.IdealInt
import smtlib._
import smtlib.Absyn._

import scala.collection.mutable.{Stack, ArrayBuffer}

object SMTParser2InputAbsy {

  private val AC = Debug.AC_PARSER
  
  import Parser2InputAbsy._
  
  def warn(msg : String) : Unit = Console.withOut(Console.err) {
    println("Warning: " + msg)
  }
  
  /**
   * Parse starting at an arbitrarily specified entry point
   */
  private def parseWithEntry[T](input : java.io.Reader,
                                env : Environment,
                                entry : (parser) => T) : T = {
    val l = new Yylex(new CRRemover2 (input))
    val p = new parser(l)
    
    try { entry(p) } catch {
      case e : Exception =>
        throw new ParseException(
             "At line " + String.valueOf(l.line_num()) +
             ", near \"" + l.buff() + "\" :" +
             "     " + e.getMessage())
    }
  }

  /**
   * We currently only support the sorts bool and int
   * everything else is considered as integers
   */
  object Type extends Enumeration {
    val Bool, Integer = Value
  }
  
  //////////////////////////////////////////////////////////////////////////////
  
  private val badStringChar = """[^a-zA-Z_0-9']""".r
  
  private def sanitise(s : String) : String =
    badStringChar.replaceAllIn(s, (m : scala.util.matching.Regex.Match) =>
                                       ('a' + (m.toString()(0) % 26)).toChar.toString)

  //////////////////////////////////////////////////////////////////////////////

  /** Implicit conversion so that we can get a Scala-like iterator from a
   * a Java list */
  import scala.collection.JavaConversions.{asScalaBuffer, asScalaIterator}

  private def asString(s : SymbolRef) : String = s match {
    case s : IdentifierRef     => asString(s.identifier_)
    case s : CastIdentifierRef => asString(s.identifier_)
  }
  
  private def asString(id : Identifier) : String = id match {
    case id : SymbolIdent =>
      asString(id.symbol_)
    case id : IndexIdent =>
      asString(id.symbol_) + "_" +
      ((id.listindexc_ map (_.asInstanceOf[Index].numeral_)) mkString "_")
  }
  
  private def asString(s : Symbol) : String = s match {
    case s : NormalSymbol => sanitise(s.normalsymbolt_)
    case s : QuotedSymbol => sanitise(s.quotedsymbolt_)
  }
  
  private def translateSort(s : Sort) : Type.Value = s match {
    case s : IdentSort => asString(s.identifier_) match {
      case "Int" => Type.Integer
      case "Bool" => Type.Bool
      case id => {
        warn("treating sort " + (PrettyPrinter print s) + " as Int")
        Type.Integer
      }
    }
    case s : CompositeSort => {
      warn("treating sort " + (PrettyPrinter print s) + " as Int")
      Type.Integer
    }
  }
  
  private object PlainSymbol {
    def unapply(s : SymbolRef) : scala.Option[String] = s match {
      case s : IdentifierRef => s.identifier_ match {
        case id : SymbolIdent => id.symbol_ match {
          case s : NormalSymbol => Some(s.normalsymbolt_)
          case _ => None
        }
        case _ => None
      }
      case _ => None
    }
  }
}


class SMTParser2InputAbsy (_env : Environment) extends Parser2InputAbsy(_env) {
  
  import IExpression._
  import Parser2InputAbsy._
  import SMTParser2InputAbsy._
  
  /** Implicit conversion so that we can get a Scala-like iterator from a
    * a Java list */
  import scala.collection.JavaConversions.{asScalaBuffer, asScalaIterator}

  type GrammarExpression = Term

  //////////////////////////////////////////////////////////////////////////////

  def apply(input : java.io.Reader)
           : (IFormula, List[IInterpolantSpec], Signature) = {
    def entry(parser : smtlib.parser) = {
      val parseTree = parser.pScriptC
      parseTree match {
        case parseTree : Script => parseTree
        case _ => throw new ParseException("Input is not an SMT-LIB 2 file")
      }
    }
    
    apply(parseWithEntry(input, env, entry _))
  }

  private def apply(script : Script)
                   : (IFormula, List[IInterpolantSpec], Signature) = {
    var assumptions : IFormula = true
    
    for (cmd <- script.listcommand_) cmd match {
//      case cmd : SetLogicCommand =>
//      case cmd : SetOptionCommand =>
//      case cmd : SetInfoCommand =>
//      case cmd : SortDeclCommand =>
//      case cmd : SortDefCommand =>
//      case cmd : FunctionDefCommand =>
//      case cmd : PushCommand =>
//      case cmd : PopCommand =>
//      case cmd : CheckSatCommand =>
//      case cmd : ExitCommand =>

      case cmd : FunctionDeclCommand => {
        // Functions are always declared to have integer inputs and outputs
        val name = asString(cmd.symbol_)
        val args : Seq[Type.Value] = cmd.mesorts_ match {
          case sorts : SomeSorts =>
            for (s <- sorts.listsort_) yield translateSort(s)
          case _ : NoSorts =>
            List()
        }
        val res = translateSort(cmd.sort_)
        if (args.length > 0)
          // use a real function
          env.addFunction(new IFunction(name, args.length, false, false),
                          res == Type.Bool)
        else if (res == Type.Integer)
          // use a constant
          env.addConstant(new ConstantTerm(name), Environment.NullaryFunction)
        else
          // use a nullary predicate (propositional variable)
          env.addPredicate(new Predicate(name, 0))
      }

      case cmd : AssertCommand => {
        val f = asFormula(translateTerm(cmd.term_, -1))
        assumptions = assumptions &&& f
      }

      case _ =>
        warn("ignoring " + (PrettyPrinter print cmd))
    }

    (!assumptions, List(), env.toSignature)
  }

  //////////////////////////////////////////////////////////////////////////////

  private def translateTerm(t : Term, polarity : Int)
                           : (IExpression, Type.Value) = t match {
    case t : smtlib.Absyn.ConstantTerm =>
      (translateSpecConstant(t.specconstant_), Type.Integer)
      
    case t : NullaryTerm =>
      symApp(t.symbolref_, List(), polarity)
    case t : FunctionTerm =>
      symApp(t.symbolref_, t.listterm_, polarity)

    case t : QuantifierTerm => {
      val quant : Quantifier = t.quantifier_ match {
        case _ : AllQuantifier => Quantifier.ALL
        case _ : ExQuantifier => Quantifier.EX
      }

      // add the bound variables to the environment and record their number
      var quantNum : Int = 0
      for (binder <- t.listsortedvariablec_) binder match {
        case binder : SortedVariable => {
          val sort = translateSort(binder.sort_)
          if (sort != Type.Integer)
            throw new Parser2InputAbsy.TranslationException(
                 "Quantification of variables of type " +
                 (PrettyPrinter print binder.sort_) +
                 " is currently not supported")
          env pushVar asString(binder.symbol_)
          quantNum = quantNum + 1
        }
      }
    
      val res = quan(Array.fill(quantNum){quant},
                     asFormula(translateTerm(t.term_, polarity)))

      // pop the variables from the environment
      for (_ <- PlainRange(quantNum)) env.popVar
    
      (res, Type.Bool)
    }
    
    case t : AnnotationTerm => {
      val triggers = for (annot <- t.listannotation_;
                          val a = annot.asInstanceOf[AttrAnnotation];
                          if (a.annotattribute_ == ":pattern")) yield {
        a.attrparam_ match {
          case p : SomeAttrParam => p.sexpr_ match {
            case e : ParenSExpr => 
              for (expr <- e.listsexpr_.toList) yield translateTrigger(expr)
            case _ =>
              throw new Parser2InputAbsy.TranslationException(
                 "Expected list of patterns after \":pattern\"")
          }
          case _ : NoAttrParam =>
            throw new Parser2InputAbsy.TranslationException(
               "Expected trigger patterns after \":pattern\"")
        }
      }
      
      if (triggers.isEmpty)
        translateTerm(t.term_, polarity)
      else
        ((asFormula(translateTerm(t.term_, polarity)) /: triggers) {
           case (res, trigger) => ITrigger(trigger, res)
         }, Type.Bool)
    }
    
    /**
     * If t is an integer term, let expression in positive position:
     *   (let ((v t)) s)
     *   ->
     *   \forall int v; (v=t -> s)
     * 
     * If t is a formula, let expression in positive position:
     *   (let ((v t)) s)
     *   ->
     *   \forall int v; ((t <-> v=0) -> s)
     *   
     * TODO: possible optimisation: use implications instead of <->, depending
     * on the polarity of occurrences of v
     */
    case t : LetTerm => {
      val bindings = for (b <- t.listbindingc_) yield {
        val binding = b.asInstanceOf[Binding]
        val (boundTerm, boundType) = translateTerm(binding.term_, 0)
        (asString(binding.symbol_), boundType, boundTerm)
      }
      
      for ((v, t, _) <- bindings) env.pushVar(v, t == Type.Bool)

      val definingEqs =
        connect(for (((_, t, s), i) <- bindings.iterator.zipWithIndex) yield {
          val bv = v(bindings.length - i - 1)
          t match {        
            case Type.Integer =>
              asTerm((s, t)) === bv
            case Type.Bool    =>
              asFormula((s, t)) <=> IIntFormula(IIntRelation.EqZero, bv)
          }}, IBinJunctor.And)
      val wholeBody@(body, bodyType) = translateTerm(t.term_, polarity)
      
      for (_ <- bindings) env.popVar

      bodyType match {
        case Type.Bool =>
          (if (polarity > 0)
            quan(Array.fill(bindings.length){Quantifier.ALL},
                 definingEqs ==> asFormula(wholeBody))
           else
             quan(Array.fill(bindings.length){Quantifier.EX},
                  definingEqs &&& asFormula(wholeBody)),
           Type.Bool)
      }
    }
  }

  //////////////////////////////////////////////////////////////////////////////
  
  private def symApp(sym : SymbolRef, args : Seq[Term], polarity : Int)
                    : (IExpression, Type.Value) = sym match {
    ////////////////////////////////////////////////////////////////////////////
    // Hardcoded connectives of formulae
    
    case PlainSymbol("true") => {
      checkArgNum("true", 0, args)
      (i(true), Type.Bool)
    }
    case PlainSymbol("false") => {
      checkArgNum("false", 0, args)
      (i(false), Type.Bool)
    }

    case PlainSymbol("not") => {
      checkArgNum("not", 1, args)
      (!asFormula(translateTerm(args.head, -polarity)), Type.Bool)
    }
    
    case PlainSymbol("and") =>
      (connect(for (s <- flatten("and", args))
                 yield asFormula(translateTerm(s, polarity)),
               IBinJunctor.And),
       Type.Bool)
    
    case PlainSymbol("or") =>
      (connect(for (s <- flatten("or", args))
                 yield asFormula(translateTerm(s, polarity)),
               IBinJunctor.Or),
       Type.Bool)
    
    case PlainSymbol("=>") => {
      if (args.size == 0)
        throw new Parser2InputAbsy.TranslationException(
          "Operator \"=>\" has to be applied to at least one argument")

      (connect((for (a <- args.init) yield
                 asFormula(translateTerm(a, -polarity))) ++
               List(asFormula(translateTerm(args.last, polarity))),
               IBinJunctor.Or),
       Type.Bool)
    }
    
    ////////////////////////////////////////////////////////////////////////////
    // Hardcoded predicates (which might also operate on booleans)
    
    case PlainSymbol("=") => {
      val transArgs = for (a <- args) yield translateTerm(a, 0)
      (if (transArgs forall (_._2 == Type.Bool))
         connect(for (a <- transArgs) yield asFormula(a),
                 IBinJunctor.Eqv)
       else
         connect(for (Seq(a, b) <- (transArgs map (asTerm(_, Type.Integer))) sliding 2)
                   yield (a === b),
                 IBinJunctor.And),
       Type.Bool)
    }
    
    case PlainSymbol("distinct") => {
      val transArgs = for (a <- args) yield translateTerm(a, 0)
      (if (transArgs forall (_._2 == Type.Bool)) transArgs.length match {
         case 0 | 1 => true
         case 2 => asTerm(transArgs(0)) =/= asTerm(transArgs(1))
         case _ => false
       } else {
         connect(for (firstIndex <- 1 until transArgs.length;
                      val firstTerm = asTerm(transArgs(firstIndex), Type.Integer);
                      secondIndex <- 0 until firstIndex) yield {
           firstTerm =/= asTerm(transArgs(secondIndex), Type.Integer)
         }, IBinJunctor.And)
       }, Type.Bool)
    }
    
    case PlainSymbol("<=") =>
      (translateChainablePred(args, _ <= _), Type.Bool)
    case PlainSymbol("<") =>
      (translateChainablePred(args, _ < _), Type.Bool)
    case PlainSymbol(">=") =>
      (translateChainablePred(args, _ >= _), Type.Bool)
    case PlainSymbol(">") =>
      (translateChainablePred(args, _ > _), Type.Bool)
    
    ////////////////////////////////////////////////////////////////////////////
    // Hardcoded integer operations

    case PlainSymbol("+") =>
      (sum(for (s <- flatten("+", args))
             yield asTerm(translateTerm(s, 0), Type.Integer)),
       Type.Integer)

    case PlainSymbol("-") if (args.length == 1) =>
      (-asTerm(translateTerm(args.head, 0), Type.Integer), Type.Integer)

    case PlainSymbol("-") => {
      if (args.size != 2)
        throw new Parser2InputAbsy.TranslationException(
          "Operator \"-\" has to be applied to one or two arguments")
      (asTerm(translateTerm(args.head, 0), Type.Integer) -
          asTerm(translateTerm(args.last, 0), Type.Integer),
       Type.Integer)
    }

    case PlainSymbol("*") =>
      ((for (s <- flatten("*", args))
          yield asTerm(translateTerm(s, 0), Type.Integer))
          reduceLeft (mult _),
       Type.Integer)

    ////////////////////////////////////////////////////////////////////////////
    // Declared symbols from the environment
    case id => (env lookupSym asString(id)) match {
      case Environment.Predicate(pred) => {
        checkArgNum(PrettyPrinter print sym, pred.arity, args)
        (IAtom(pred, for (a <- args) yield asTerm(translateTerm(a, 0))),
         Type.Bool)
      }
      
      case Environment.Function(fun, encodesBool) => {
        checkArgNum(PrettyPrinter print sym, fun.arity, args)
        (IFunApp(fun, for (a <- args) yield asTerm(translateTerm(a, 0))),
         if (encodesBool) Type.Bool else Type.Integer)
      }

      case Environment.Constant(c, _) =>
        (c, Type.Integer)
      
      case Environment.Variable(i, encodesBool) =>
        (v(i), if (encodesBool) Type.Bool else Type.Integer)
    }
        
  }
  
  //////////////////////////////////////////////////////////////////////////////
  
  private def translateTrigger(expr : SExpr) : ITerm = expr match {
    
    case expr : ConstantSExpr => translateSpecConstant(expr.specconstant_)
    
    case expr : SymbolSExpr => (env lookupSym asString(expr.symbol_)) match {
      case Environment.Function(fun, _) => {
        checkArgNumSExpr(PrettyPrinter print expr.symbol_,
                         fun.arity, List[SExpr]())
        IFunApp(fun, List())
      }
      case Environment.Constant(c, _) => c
      case Environment.Variable(i, false) => v(i)
      case _ =>
        throw new Parser2InputAbsy.TranslationException(
          "Unexpected symbol in a trigger: " +
          (PrettyPrinter print expr.symbol_))
    }
    
    case expr : ParenSExpr => {
      if (expr.listsexpr_.isEmpty)
        throw new Parser2InputAbsy.TranslationException(
          "Expected a function application, not " + (PrettyPrinter print expr))
      
      expr.listsexpr_.head match {
        case funExpr : SymbolSExpr => (env lookupSym asString(funExpr.symbol_)) match {
          case Environment.Function(fun, _) => {
            val args = expr.listsexpr_.tail.toList
            checkArgNumSExpr(PrettyPrinter print funExpr.symbol_, fun.arity, args)
            IFunApp(fun, for (e <- args) yield translateTrigger(e))
          }
          case Environment.Constant(c, _) => {
            checkArgNumSExpr(PrettyPrinter print funExpr.symbol_,
                             0, expr.listsexpr_.tail)
            c
          }
          case Environment.Variable(i, false) => {
            checkArgNumSExpr(PrettyPrinter print funExpr.symbol_,
                             0, expr.listsexpr_.tail)
            v(i)
          }
          case _ =>
            throw new Parser2InputAbsy.TranslationException(
              "Unexpected symbol in a trigger: " +
              (PrettyPrinter print funExpr.symbol_))
        }
      }
    }
  }
  
  //////////////////////////////////////////////////////////////////////////////
  
  private def translateSpecConstant(c : SpecConstant) = c match {
    case c : NumConstant => i(IdealInt(c.numeral_))
  }
  
  private def translateChainablePred(args : Seq[Term],
                                     op : (ITerm, ITerm) => IFormula) : IFormula = {
    val transArgs = for (a <- args) yield asTerm(translateTerm(a, 0))
    connect(for (Seq(a, b) <- transArgs sliding 2) yield op(a, b), IBinJunctor.And)
  }
  
  private def flatten(op : String, args : Seq[Term]) : Seq[Term] =
    for (a <- args;
         b <- collectSubExpressions(a, {
                case PlainSymbol(`op`) => true
                case _ => false
              }, SMTConnective))
    yield b

  private def checkArgNum(op : => String, expected : Int, args : Seq[Term]) : Unit =
    if (expected != args.size)
      throw new Parser2InputAbsy.TranslationException(
          "Operator \"" + op +
          "\" is applied to a wrong number of arguments: " +
          ((for (a <- args) yield (PrettyPrinter print a)) mkString ", "))
  
  private def checkArgNumSExpr(op : => String, expected : Int, args : Seq[SExpr]) : Unit =
    if (expected != args.size)
      throw new Parser2InputAbsy.TranslationException(
          "Operator \"" + op +
          "\" is applied to a wrong number of arguments: " +
          ((for (a <- args) yield (PrettyPrinter print a)) mkString ", "))
  
  private object SMTConnective extends ASTConnective {
    def unapplySeq(t : Term) : scala.Option[Seq[Term]] = t match {
      case t : NullaryTerm => Some(List())
      case t : FunctionTerm => Some(t.listterm_.toList)
    }
  }

  //////////////////////////////////////////////////////////////////////////////
  
  private def asFormula(expr : (IExpression, Type.Value)) : IFormula = expr match {
    case (expr : IFormula, Type.Bool) =>
      expr
    case (expr : ITerm, Type.Bool) =>
      // then we assume that an integer encoding of boolean values was chosen
      IIntFormula(IIntRelation.EqZero, expr)
    case (expr, _) =>
      throw new Parser2InputAbsy.TranslationException(
                   "Expected a formula, not " + expr)
  }

  private def asTerm(expr : (IExpression, Type.Value)) : ITerm = expr match {
    case (expr : ITerm, _) =>
      expr
    case (expr, _) =>
      throw new Parser2InputAbsy.TranslationException(
                   "Expected a term, not " + expr)
  }

  private def asTerm(expr : (IExpression, Type.Value),
                     expectedSort : Type.Value) : ITerm = expr match {
    case (expr : ITerm, `expectedSort`) =>
      expr
    case (expr, _) =>
      throw new Parser2InputAbsy.TranslationException(
                   "Expected a term of type " + expectedSort + ", not " + expr)
  }
}