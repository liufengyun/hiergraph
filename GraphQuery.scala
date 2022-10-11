import scala.collection.mutable

import Util.*

/**
 * This script implements a simple query engine for hierarchical graphs.
 *
 * The input to the engine is a CSV file of the following format:
 *
 *     a.b.c,  x.y
 *
 * In the above, the dot-separated string `a.b.c` (similarly "x.y") represents
 * the hierarchy of the nodes "a", "b" and "c":
 *
 * - "c" belongs to "b"
 * - "b" belongs to "a"
 *
 * The node "c" is a leaf node. Each line in the CSV file represents a directed
 * edge (dependency) in the graph.
 *
 * Non-leaf nodes become dependent due to the depndencies of their leaf nodes.
 *
 * This query engine explains why two nodes in the HGDB are dependent.
 *
 */

// ---------------------------- Core Logic ---------------------------------

sealed abstract class Tree:
  val name: String
  val parent: Parent

  def contains(other: Tree): Boolean =
    this.eq(other) || other.parent != null && this.contains(other.parent)

  lazy val leaves: Iterable[Leaf] = this match
    case leaf: Leaf => Seq(leaf)
    case parent: Parent => parent.children.values.flatMap(_.leaves)

  override def toString(): String =
    if parent == null || parent.isRoot then name
    else parent.toString + "." + name

  def isRoot: Boolean = parent == null

final class Leaf(val name: String, val parent: Parent) extends Tree:
  var successors: List[Leaf] = Nil

  def addSuccessor(to: Leaf): Unit =
    successors = to :: successors

final class Parent(val name: String, val children: mutable.Map[String, Tree], val parent: Parent) extends Tree:
  def ensureNodeExists(parts: List[String]): Leaf =
    parts match
    case x :: Nil =>
      this.children.get(x) match
      case None =>
        val leaf = new Leaf(x, this)
        this.children(x) = leaf
        leaf

      case Some(node) =>
        node.asInstanceOf[Leaf]

    case x :: xs =>
      val next = this.children.getOrElseUpdate(x, Parent(x, mutable.Map.empty, this))

      next match
      case parent: Parent =>
        parent.ensureNodeExists(xs)

      case leaf: Leaf =>
        ??? // impossible

    case Nil =>
      ??? // impossible

  def findNode(parts: List[String]): Option[Tree] =
    parts match
    case x :: Nil =>
      this.children.get(x)

    case x :: xs =>
      val next = this.children.getOrElseUpdate(x, Parent(x, mutable.Map.empty, this))

      next match
      case parent: Parent =>
        parent.findNode(xs)

      case leaf: Leaf =>
        None

    case Nil =>
      ??? // impossible

class Graph(root: Parent):

  def addEdge(fromParts: List[String], toParts: List[String]) =
    val fromNode = root.ensureNodeExists(fromParts)
    val toNode = root.ensureNodeExists(toParts)

    fromNode.addSuccessor(toNode)

  def search(from: String, to: String, limit: Int)(using Context): List[List[Leaf]] =
    val fromParts = from.split('.').toList
    val toParts = to.split('.').toList

    val fromNode =
      root.findNode(fromParts) match
      case Some(node) => node
      case None =>
        error(s"Cannot find node $from")
        return Nil

    val toNode =
      root.findNode(toParts) match
      case Some(node) => node
      case None =>
        error(s"Cannot find node $to")
        return Nil

    var paths: List[List[Leaf]] = Nil
    var success = true
    while paths.size < limit && success do
      val res = doSearch(fromNode, toNode, excluded = paths.map(_.head).toSet)
      success = res.nonEmpty
      // shortest path first
      paths = paths ++ res
    end while

    paths

  /**
   * Find the shortest path that links `fromGroup` and `toGroup`.
   */
  def doSearch(fromGroup: Tree, toGroup: Tree, excluded: Set[Leaf])(using Context): List[List[Leaf]] =
    debug(fromGroup.toString + " -> " + toGroup.toString)

    val leafDistances = mutable.Map.empty[Leaf, Int]
    def currentDistance(leaf: Leaf) = leafDistances.getOrElseUpdate(leaf, Int.MaxValue)
    def setDistance(leaf: Leaf, dist: Int) = leafDistances(leaf) = dist

    // Use java priority queue as the Scala one does not support removing elements
    val ordering: Ordering[Leaf] = (x, y) => currentDistance(x) - currentDistance(y)
    val minPriorityQueue = new java.util.PriorityQueue[Leaf](ordering)

    val predecessors = mutable.Map.empty[Leaf, Leaf]

    // initialize
    for
      leaf <- fromGroup.leaves
    do
      setDistance(leaf, 0)
      if !excluded.contains(leaf) then
        minPriorityQueue.add(leaf)

    debug("source nodes = " + fromGroup.leaves.size)
    debug("dest nodes = " + toGroup.leaves.size)

    debug("propagating ...")

    var count = 0
    var reached = false

    // propagate distances
    while !minPriorityQueue.isEmpty && !reached do
      val current = minPriorityQueue.poll()
      val dist0 = currentDistance(current)
      for
        neigbhor <- current.successors
      do
        if currentDistance(neigbhor) > dist0 + 1 then
          predecessors(neigbhor) = current
          setDistance(neigbhor, dist0 + 1)
          // Remove is important to stop the iteration early
          minPriorityQueue.remove(neigbhor)
          minPriorityQueue.add(neigbhor)

        reached = toGroup.contains(neigbhor)
      end for

      count += 1
    end while

    debug("iteration count = " + count)
    debug(s"path found = $reached")
    debug("trace back")

    // trace back
    val reachedNodes = toGroup.leaves.filter(predecessors.contains)
    if reachedNodes.isEmpty then return Nil

    var current = reachedNodes.toSeq.min(using ordering)
    var path = current :: Nil
    while !fromGroup.contains(current) do
      current = predecessors(current)
      path = current :: path

    path :: Nil
  end doSearch
end Graph

// ---------------------------- User Interface ---------------------------------

case class Path(nodes: List[Leaf]):
  def show: String = nodes.map(_.toString).mkString("\n")

case class Context(silent: Boolean, defaultLimit: Int)

class Repl(graph: Graph):
  private val stdin = new java.io.BufferedReader(new java.io.InputStreamReader(System.in))

  val examples = List(
    "path -n 3 a.b.c p.q",
    "quit",
    "help",
  )

  def path(args: Array[String])(using ctx: Context): Unit =
    assert(args(0) == "path")
    var limit = ctx.defaultLimit
    val remaining = parseOptions(args.toList) { (option, nextArgs) =>
      option match
      case "-n" =>
        if nextArgs.isEmpty || !nextArgs(0).matches(raw"\d+") then
          warn("Incorrect option ignored, expect a number after `-n`.")
          nextArgs
        else
          limit = nextArgs(0).toInt
          nextArgs.tail
      case opt =>
        warn(s"Unknown option $opt ignored.")
        nextArgs
    }

    if remaining.size != 2 then
      error("Incorrect command, expect source and dest")
      return

    val from :: to :: Nil = remaining: @unchecked

    val paths = graph.search(from, to, limit)
    showResult(paths)

  def run()(using Context): Unit =
    print("\nrepl> ".dim)
    val inputs = stdin.readLine().trim.split(' ')

    if inputs.length == 0 then
      run()

    else
      inputs(0) match
      case "quit" | "exit" => // quit
      case "help" =>
        println("You can try the following commands:\n")
        examples.foreach(ex => println("- " + ex))
        run()

      case "path" =>
        path(inputs)
        run()

// -------------------------------- Utilities ----------------------------------


object Util:
  extension (text: String)
    def dim: String = "\u001B[2m" + text + "\u001B[22m"
    def red: String = Console.RED + text + Console.RESET
    def yellow: String = Console.YELLOW + text + Console.RESET

  def loadCSV(file: String): Graph =
    val graph = new Graph(new Parent("root", mutable.Map.empty, null))

    val source = scala.io.Source.fromFile(file)
    source.getLines().foreach { line =>
      val fields = line.split(',')
      assert(fields.size == 2, "line = " + line)

      val fromParts = fields(0).trim.split('.').toList
      val toParts = fields(1).trim.split('.').toList

      graph.addEdge(fromParts, toParts)
    }

    graph

  /** Parse options and return remaining non-option arguments */
  def parseOptions(args: List[String])(handler: (String, List[String]) => List[String]): List[String] =
    val remaining = new mutable.ArrayBuffer[String]
    var current = args
    while current.nonEmpty do
      if current.head.matches(raw"-\w+") then
        current = handler(current.head, current.tail)
      else
        remaining += current.head
        current = current.tail
    remaining.toList

  def debug(info: String)(using ctx: Context): Unit =
    if !ctx.silent then println(("[DEBUG] " + info).dim)

  def warn(message: String): Unit =
    println(message.yellow)

  def error(message: String): Unit =
    println(message.red)

  def showResult(paths: List[List[Leaf]]): Unit =
    paths match
    case Nil =>
      println("No path found")
    case _ =>
      for (nodes, index) <- paths.zipWithIndex do
        if index > 0 then println()
        println(s"Path ${index + 1} (length = ${nodes.length}):".yellow)
        println(Path(nodes).show)

  def runBatch(csv: String, from: String, to: String)(using ctx: Context): Unit =
    val graph = loadCSV(csv)

    println("Search path: " + (from + " -> " + to).yellow)
    val paths = graph.search(from, to, limit = ctx.defaultLimit)
    showResult(paths)

  def run(args: Array[String]): Unit =
    var limit = 1
    var silent = false
    val remaining = parseOptions(args.toList) { (option, nextArgs) =>
      option match
      case "-n" =>
        if nextArgs.isEmpty || !nextArgs(0).matches(raw"\d+") then
          warn("Incorrect option ignored, expect a number after `-n`.")
          nextArgs
        else
          limit = nextArgs(0).toInt
          nextArgs.tail

      case "-s" =>
        silent = true
        nextArgs

      case opt =>
        warn(s"Unknown option $opt ignored.")
        nextArgs
    }

    val context = Context(silent, limit)

    if remaining.length == 3 then
      runBatch(remaining(0), remaining(1), remaining(2))(using context)

    else if remaining.length == 1 then
      if remaining(0) == "help" then
        help()
      else
        val csv = remaining(0)
        val graph = loadCSV(csv)
        val repl = new Repl(graph)
        repl.run()(using context)

    else
      if remaining.nonEmpty then error("Incorrect command, unexpected parameters = " + remaining)
      help()

  end run

  val text =
    """|========================================================================
       |hiergraph -- a hierarchical graph query engine
       |Usage: hiergraph [-s] [-n N] deps.csv [<from> <to>]
       |------------------------------------------------------------------------
       |Examples:
       |
       |- Batch mode: hiergraph deps.csv -s -n 4 a.b.c p.q
       |
       |- REPL mode: `hiergraph deps.csv` and input `help`
       |------------------------------------------------------------------------
       |Options:
       |        -s        silent, do not print debug informaiton
       |
       |        -n N      limit the number of path to N, default is 1
       |------------------------------------------------------------------------
       |Bug report:  https://github.com/liufengyun/hiergraph
       |========================================================================""".stripMargin

  def help(): Unit =
    println(text)


// -------------------------------- Entry point --------------------------------

object Engine:
  def main(args: Array[String]) = run(args)
