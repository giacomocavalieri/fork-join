import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration.Duration

/* Location where the code will be executed just some mocks we really
   don't care about locations for now */
enum Location:
  case Here()
  case Remote()

object Location:
  def here = Location.Here()

/* A Fiber is a computation _taking place_ in a certain Location */
trait Fiber[A]:
  def getResult(): A

/* The DSL one can use to define remote programs that can spawn and join fibers */
enum RemoteDSL[A]:
  /* Forks a program and returns a reference to the forked fiber */
  case ForkAt[A](location: Location, action: () => A) extends RemoteDSL[Fiber[A]]
  /* Awaits for a fiber completion returning its result */
  case Await[A](who: Fiber[A]) extends RemoteDSL[A]

object Fiber:
  /* Smart constructor to await a fiber */
  extension [A](fiber: Fiber[A]) def await: Program[RemoteDSL, A] = Program.fromInstruction(RemoteDSL.Await(fiber))

  /* A mock Fiber that executes locally */
  def mock[A](a: () => A): Fiber[A] =
    new Fiber:
      /* Just a dumb implementation the recomputes `a` every time */
      override def getResult() = a()

  /* A Fiber that wraps a Future in the default ExecutionContext */
  def backedByFuture[A](a: () => A): Fiber[A] =
    new Fiber:
      import concurrent.ExecutionContext.Implicits.global
      val future = Future(a())
      override def getResult() = Await.result(future, Duration.Inf)

object Remote:
  /* Smart constructor to fork a remote program */
  def forkAt[A](location: Location, action: () => A): Program[RemoteDSL, Fiber[A]] =
    Program.fromInstruction(RemoteDSL.ForkAt(location, action))

/* Description of the essence of a program. This is an Operational Free Monad
   but we don't use the M word here ;) */
enum Program[Instructions[_], A]:
  /* A program that simply returns a single value */
  case Return[I[_], A](result: A) extends Program[I, A]
  /* A program that simply executes an action that returns a value */
  case Instruction[I[_], A](instruction: I[A]) extends Program[I, A]
  /* Sequential composition of two programs: a first program that returns A
     and a continuation that, given A, produces the second program to run */
  case AndThen[I[_], A, B](program: Program[I, A], continuation: A => Program[I, B]) extends Program[I, B]

object Program:
  /* Smart constructor to simply return a single value */
  def fromValue[I[_], A](value: A): Program[I, A] = Program.Return(value)
  /* Smart cosntructor to simply execute a single action */
  def fromInstruction[I[_], A](instruction: I[A]): Program[I, A] = Program.Instruction(instruction)

  extension [I[_], A](program: Program[I, A])
    /* Smart constructor to simply sequence two programs */
    def andThen[B](continuation: A => Program[I, B]): Program[I, B] =
      Program.AndThen(program, continuation)

    /* Boilerplate required by scala to use for comprehension */
    def flatMap[B](f: A => Program[I, B]): Program[I, B] = program.andThen(f)
    def map[B](f: A => B): Program[I, B] = program.andThen { a =>
      val b = f(a)
      Program.fromValue(b)
    }

object Interpretation:
  /* Mock interpreter that never spawn remotely and uses local Futures as Fibers */
  def interpretAndLog[A](program: Program[RemoteDSL, A]): A = program match
    /* If the program returns a value, return it */
    case Program.Return(value) =>
      println("Program is over")
      value

    /* If the program is two composed programs, interpret the first one
       and then interpret the second */
    case Program.AndThen(program, continuation) =>
      val a1 = interpretAndLog(program) // Beware! Not stack safe for deeply nested programs
      val programB = continuation(a1)
      interpretAndLog(programB)

    /* If the program is an istruction, inspect the instruction */
    case Program.Instruction(instruction) =>
      instruction match
        case RemoteDSL.ForkAt(_location, action) =>
          println("Fork!")
          Fiber.backedByFuture(action)

        case RemoteDSL.Await(who) =>
          println("Await")
          who.getResult()

/* Example program */
val remoteProgram: Program[RemoteDSL, Int] = for
  ref1 <- Remote.forkAt(Location.here, () => { Thread.sleep(5000); 1 + 1 })
  ref2 <- Remote.forkAt(Location.here, () => { Thread.sleep(5000); 2 + 2 })
  res1 <- ref1.await
  res2 <- ref2.await
yield (res1 + res2)

@main def main =
  println(Interpretation.interpretAndLog(remoteProgram))
