package parser.statements

import ast.Basic.Text
import ast.Statements._
import fastparse.noApi._
import parser.Basic.{echoStartTag, qualifiedName, semicolonFactory}
import parser.ExpressionParser.expression
import parser.PHPParser._
import parser.literals.KeywordConversions._
import parser.literals.Keywords._
import parser.literals.Lexical.ws
import parser.literals.Literals._
import parser.literals.WsAPI._
import parser.statements.ControlFlowParser._
import parser.statements.DeclarationParser._

object StatementParser {

  def statement : P[Statement] = if(isTagProcessed) possibleStatements else echoTagStmnt

  def echoTagStmnt : P[EchoTagStmnt] = {
    isTagProcessed = true
    P(echoStartTag ~ expression.rep(min=1, sep=",") ~ semicolonFactory).map(t => EchoTagStmnt(t._2, t._3))
  }

  val emptyStmnt : P[EmptyStmnt] = P(semicolonFactory).map(EmptyStmnt)
  val compoundStmnt : P[CompoundStmnt] = P("{" ~/ statement.rep ~ "}").map(CompoundStmnt)
  val namedLabelStmnt : P[NamedLabelStmnt] = P(name ~ ":" ~ statement).map(t => NamedLabelStmnt(t._1, t._2))
  val expStmnt : P[ExpressionStmnt] = P(expression ~ semicolonFactory).map(t => ExpressionStmnt(t._1, t._2))


  private val catchClause : P[CatchClause] = P(CATCH ~/ "(" ~ qualifiedName ~ variableName ~ ")" ~ compoundStmnt)
    .map(t => CatchClause(t._1, t._2, t._3))

  val tryStmnt : P[TryStmnt] =
    P(TRY ~ compoundStmnt ~ catchClause.rep ~ (FINALLY ~/ compoundStmnt).?)
      .map(t => TryStmnt(t._1, t._2, t._3))


  private val declareDeclarative : P[DeclareDeclarative.Value] =
    P(TICKS.!.map(_ => DeclareDeclarative.TICKS ) |
    ENCODING.!.map(_ => DeclareDeclarative.ENCODING) |
    STRICT_TYPES.!.map(_ => DeclareDeclarative.STRICT_TYPES))

  private val declareBody : P[(Seq[Statement], Option[Text])] =
    P(":" ~ statement.rep ~ ENDDECLARE ~ semicolonFactory | statement.map(t => (Seq(t), None)))

  val declareStmnt : P[DeclareStmnt] =
    P(DECLARE ~/ "(" ~ declareDeclarative ~ "=" ~ literal ~ ")" ~/ declareBody)
      .map(t => DeclareStmnt(t._1, t._2, t._3._1, t._3._2))


  private val typeDecl : P[TypeDecl] = P(arrayType | callableType | iterableType |
    boolType | floatType | intType | stringType | qualifiedName.map(QualifiedType))

  private val possibleFunctionType : P[PossibleTypes] = P(voidType | typeDecl)

  private val paramType : P[(Option[TypeDecl], Boolean)] = P(typeDecl.? ~ "&".!.?)
    .map(t => (t._1, t._2.isDefined))

  private val parameterDecl : P[(Option[TypeDecl], Boolean) => ParameterDecl] =
    P(("..." ~ variableName).map(name => (a: Option[TypeDecl], b: Boolean) =>  VariadicParam(a, b, name)) |
    (variableName ~ ("=" ~ expression).?).map(t => (a: Option[TypeDecl], b: Boolean) => SimpleParam(a, b, t._1, t._2)))

  private[statements] val funcHeader : P[FuncHeader] =
    P(FUNCTION ~/ "&".!.? ~ name ~ "(" ~ (paramType ~ parameterDecl).rep(sep=",") ~ ")" ~ (":" ~ possibleFunctionType).?)
      .map(t => FuncHeader(t._1.isDefined, t._2, t._3.map(g => g._3(g._1, g._2)), t._4))

  val functionDefStmnt : P[FuncDef] = P(funcHeader ~ compoundStmnt)
    .map(t => FuncDef(t._1, t._2))


  val namespaceDefStmnt : P[NamespaceDef] = P((NAMESPACE ~ name ~ ";").map(t => NamespaceDef(Some(t), None)) |
    (NAMESPACE ~ name.? ~ compoundStmnt).map(t => NamespaceDef(t._1, Some(t._2))))


  private val possibleStatements : P[Statement] = P(compoundStmnt | namedLabelStmnt | selectionStmnt | iterationStmnt | jumpStmnt | tryStmnt |
    declareStmnt | constDeclStmnt | functionDefStmnt | classDeclStmnt | interfaceDeclStmnt | traitDeclStmnt |
    namespaceDefStmnt | namespaceUseDeclStmnt | globalDeclStmnt | functionStaticDeclStmnt | emptyStmnt | expStmnt)
}
