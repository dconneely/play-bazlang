package com.davidconneely.bazlang.exec.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.davidconneely.bazlang.antlr.AntlrParser;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Component tests for {@link AstLowering}'s statement-lowering half (Phase 2 of {@code
 * localonly-plan-CUSTOM-AST.md}): the flat-list/{@code IfStmt} inlining quirk, the disambiguated
 * {@link LineRange} lowering, {@code printList} interleaving, and representative structural checks
 * across statement kinds. Not behaviour tests - see {@code AstStatementExecutorTest} for those.
 */
class AstLoweringStatementTest {

  private static final AntlrParser PARSER = new AntlrParser();

  private static List<Stmt> lower(String source) {
    return AstLowering.lowerStatements(PARSER.parseStatementsContext(source), 10);
  }

  @Nested
  class FlatSkipScan {
    @Test
    void ifBodyIsInlinedRightAfterTheIfStmt() {
      final var flat = lower("IF 1 THEN LET A=1 : LET B=2");
      assertEquals(3, flat.size());
      assertInstanceOf(Stmt.IfStmt.class, flat.get(0));
      final var ifStmt = (Stmt.IfStmt) flat.get(0);
      assertEquals(2, ifStmt.body().size());
      // The flat list holds the identical (not just equal) Stmt instances from IfStmt.body().
      assertSame(ifStmt.body().get(0), flat.get(1));
      assertSame(ifStmt.body().get(1), flat.get(2));
    }

    @Test
    void nestedIfBodiesFlattenRecursively() {
      final var flat = lower("IF 1 THEN IF 2 THEN LET A=1");
      assertEquals(3, flat.size());
      assertInstanceOf(Stmt.IfStmt.class, flat.get(0));
      assertInstanceOf(Stmt.IfStmt.class, flat.get(1));
      assertInstanceOf(Stmt.LetStmt.class, flat.get(2));
      final var outer = (Stmt.IfStmt) flat.get(0);
      final var inner = (Stmt.IfStmt) outer.body().get(0);
      assertSame(inner, flat.get(1));
      assertSame(inner.body().get(0), flat.get(2));
    }

    @Test
    void statementsWithNoIfAreNotAffected() {
      final var flat = lower("LET A=1 : LET B=2 : STOP");
      assertEquals(3, flat.size());
    }
  }

  @Nested
  class ForStep {
    @Test
    void defaultsToOneWhenAbsent() {
      final var stmt = (Stmt.ForStmt) lower("FOR I=1 TO 10").getFirst();
      assertEquals(new NumExpr.NumLiteral(1.0), stmt.step());
    }

    @Test
    void usesExplicitStepWhenPresent() {
      final var stmt = (Stmt.ForStmt) lower("FOR I=1 TO 10 STEP 2").getFirst();
      assertEquals(new NumExpr.NumLiteral(2.0), stmt.step());
    }
  }

  @Nested
  class LineRangeLowering {
    private LineRange range(String listSource) {
      return ((Stmt.ListStmt) lower(listSource).getFirst()).range();
    }

    @Test
    void noRangeAtAllIsNull() {
      assertNull(range("LIST"));
    }

    @Test
    void singleNumberWithNoToMeansFromNToEnd() {
      final var r = range("LIST 10");
      assertEquals(new NumExpr.NumLiteral(10.0), r.from());
      assertNull(r.to());
    }

    @Test
    void twoNumbersWithTo() {
      final var r = range("LIST 10 TO 20");
      assertEquals(new NumExpr.NumLiteral(10.0), r.from());
      assertEquals(new NumExpr.NumLiteral(20.0), r.to());
    }

    @Test
    void numberThenToMeansFromNToEnd() {
      final var r = range("LIST 10 TO");
      assertEquals(new NumExpr.NumLiteral(10.0), r.from());
      assertNull(r.to());
    }

    @Test
    void bareToBeforeNumberMeansFromStartToN() {
      final var r = range("LIST TO 20");
      assertNull(r.from());
      assertEquals(new NumExpr.NumLiteral(20.0), r.to());
    }

    @Test
    void bareToMeansWholeProgram() {
      final var r = range("LIST TO");
      assertNull(r.from());
      assertNull(r.to());
    }
  }

  @Nested
  class PrintListLowering {
    @Test
    void semicolonSeparatedValues() {
      final var items = ((Stmt.PrintStmt) lower("PRINT \"A\"; \"B\"").getFirst()).items();
      assertEquals(3, items.size());
      assertInstanceOf(PrintElement.ValueItem.class, items.get(0));
      assertEquals(new PrintElement.Sep(';'), items.get(1));
      assertInstanceOf(PrintElement.ValueItem.class, items.get(2));
    }

    @Test
    void atAndTabItems() {
      final var items = ((Stmt.PrintStmt) lower("PRINT AT 1, 2; TAB 5; \"X\"").getFirst()).items();
      assertInstanceOf(PrintElement.AtItem.class, items.get(0));
      assertInstanceOf(PrintElement.TabItem.class, items.get(2));
      assertInstanceOf(PrintElement.ValueItem.class, items.get(4));
    }

    @Test
    void barePrintHasNoItems() {
      assertTrue(((Stmt.PrintStmt) lower("PRINT").getFirst()).items().isEmpty());
    }
  }

  @Nested
  class AssignTargetLowering {
    @Test
    void numericScalar() {
      final var target = ((Stmt.LetStmt) lower("LET A=5").getFirst()).target();
      assertInstanceOf(AssignTarget.NumScalarTarget.class, target);
      assertEquals("A", ((AssignTarget.NumScalarTarget) target).name);
    }

    @Test
    void numericArray() {
      final var target = ((Stmt.LetStmt) lower("LET A(1)=5").getFirst()).target();
      assertInstanceOf(AssignTarget.NumArrayTarget.class, target);
      assertEquals(1, ((AssignTarget.NumArrayTarget) target).indices.size());
    }

    @Test
    void plainStringScalarHasNullSubscript() {
      final var target = ((Stmt.LetStmt) lower("LET A$=\"X\"").getFirst()).target();
      assertInstanceOf(AssignTarget.StrTarget.class, target);
      assertNull(((AssignTarget.StrTarget) target).subscript);
    }

    @Test
    void stringSliceHasSubscript() {
      final var target = ((Stmt.LetStmt) lower("LET A$(1 TO 3)=\"X\"").getFirst()).target();
      assertInstanceOf(AssignTarget.StrTarget.class, target);
      assertNotNullSubscript((AssignTarget.StrTarget) target);
    }

    private void assertNotNullSubscript(AssignTarget.StrTarget target) {
      org.junit.jupiter.api.Assertions.assertNotNull(target.subscript);
    }
  }

  @Nested
  class DimLowering {
    @Test
    void numericArray() {
      final var stmt = (Stmt.DimStmt) lower("DIM A(3, 4)").getFirst();
      assertEquals("A", stmt.name());
      assertEquals(false, stmt.isString());
      assertEquals(2, stmt.dims().size());
    }

    @Test
    void stringArray() {
      final var stmt = (Stmt.DimStmt) lower("DIM A$(3, 10)").getFirst();
      assertEquals("A$", stmt.name());
      assertTrue(stmt.isString());
      assertEquals(2, stmt.dims().size());
    }
  }

  @Nested
  class MiscStatements {
    @Test
    void defFnStructural() {
      final var stmt = (Stmt.DefFnStmt) lower("DEF FN F(X)=X*2").getFirst();
      assertEquals("F", stmt.name());
      assertEquals(List.of("X"), stmt.params());
      assertInstanceOf(NumExpr.NumBinaryOp.class, stmt.body());
    }

    @Test
    void randWithNoArgIsNullSeed() {
      assertNull(((Stmt.RandStmt) lower("RANDOMIZE").getFirst()).seed());
    }

    @Test
    void randWithArgHasSeed() {
      assertEquals(
          new NumExpr.NumLiteral(42.0), ((Stmt.RandStmt) lower("RANDOMIZE 42").getFirst()).seed());
    }

    @Test
    void bareRestoreAndRunHaveNullTarget() {
      assertNull(((Stmt.RestoreStmt) lower("RESTORE").getFirst()).target());
      assertNull(((Stmt.RunStmt) lower("RUN").getFirst()).target());
    }

    @Test
    void plotAlwaysHasBothCoordinates() {
      final var stmt = (Stmt.PlotStmt) lower("PLOT 10, 20").getFirst();
      assertEquals(new NumExpr.NumLiteral(10.0), stmt.x());
      assertEquals(new NumExpr.NumLiteral(20.0), stmt.y());
    }
  }
}
