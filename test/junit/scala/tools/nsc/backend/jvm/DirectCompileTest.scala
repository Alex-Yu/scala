package scala.tools.nsc.backend.jvm

import org.junit.Assert._
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import scala.tools.asm.Opcodes._
import scala.tools.partest.ASMConverters._
import scala.tools.testing.BytecodeTesting
import scala.tools.testing.BytecodeTesting._

@RunWith(classOf[JUnit4])
class DirectCompileTest extends BytecodeTesting {
  override def compilerArgs = "-opt:l:method"
  import compiler._

  @Test
  def testCompile(): Unit = {
    val List(("C.class", bytes)) = compileToBytes(
      """class C {
        |  def f = 1
        |}
      """.stripMargin)
    def s(i: Int, n: Int) = (bytes(i) & 0xff) << n
    assertTrue((s(0, 24) | s(1, 16) | s(2, 8) | s(3, 0)) == 0xcafebabe) // mocha java latte macchiato surpreme dark roasted espresso
  }

  @Test
  def testCompileClasses(): Unit = {
    val List(cClass, cModuleClass) = compileClasses("class C; object C")

    assertTrue(cClass.name == "C")
    assertTrue(cModuleClass.name == "C$")

    val List(dMirror, dModuleClass) = compileClasses("object D")

    assertTrue(dMirror.name == "D")
    assertTrue(dModuleClass.name == "D$")
  }

  @Test
  def testCompileMethods(): Unit = {
    val List(f, g) = compileMethods(
      """def f = 10
        |def g = f
      """.stripMargin)

    assertSameCode(f.instructions.dropNonOp,
      List(IntOp(BIPUSH, 10), Op(IRETURN)))

    assertSameCode(g.instructions.dropNonOp,
      List(VarOp(ALOAD, 0), Invoke(INVOKEVIRTUAL, "C", "f", "()I", itf = false), Op(IRETURN)))
  }

  @Test
  def testDropNonOpAliveLabels(): Unit = {
    // makes sure that dropNoOp doesn't drop labels that are being used
    val is = compileInstructions("""def f(x: Int) = if (x == 0) "a" else "b"""")
    assertSameCode(is.dropLinesFrames, List(
      Label(0),
      VarOp(ILOAD, 1),
      Op(ICONST_0),
      Jump(IF_ICMPNE,
      Label(7)),
      Ldc(LDC, "a"),
      Op(ARETURN),
      Label(7),
      Ldc(LDC, "b"),
      Op(ARETURN),
      Label(11)
    ))
  }

  @Test
  def testSeparateCompilation(): Unit = {
    val codeA = "class A { def f = 1 }"
    val codeB = "class B extends A { def g = f }"
    val List(a, b) = compileClassesSeparately(List(codeA, codeB))
    val ins = getInstructions(b, "g")
    assert(ins exists {
      case Invoke(_, "B", "f", _, _) => true
      case _ => false
    }, ins)
  }

  @Test
  def compileErroneous(): Unit = {
    compileToBytes("class C { def f: String = 1 }", allowMessage = _.msg contains "type mismatch")
  }
}
