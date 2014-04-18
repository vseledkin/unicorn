import com.adp.cdg._
import com.adp.cdg.DocumentImplicits._
import com.adp.cdg.store._
import com.adp.cdg.store.accumulo._
import com.adp.cdg.store.hbase._

// measure running time of a function/block 
def time[A](f: => A) = {
  val s = System.nanoTime
  val ret = f
  println("time: " + (System.nanoTime - s)/1e6 + " ms")
  ret
}

// connect to Accumulo server
val server = AccumuloServer("local-poc", "127.0.0.1:2181", "tester", "adpadp")
// Use table "small" 
val table = server.dataset("small", "public")

//val server = HBaseServer()
//val table = server.dataset("small")

// Read a non-existing row. It is the pure time of round trip.
val doc = time { "row1" of table }

// Create a document 
val person = Document("293050")
person("name") = "Haifeng"
person("gender") = "Male"
person("salary") = 1.0
person("zip") = 10011

// Create another document 
val address = Document("293050")
address.street = "135 W. 18th ST"
address.city = "New York"
address.state = "NY"
address.zip = person.zip

// add a doucment into another one
person.address = address
// add an array into a document
person.projects = Array("GHCM", "Analytics")

person("work with", "Jim") = true
person("work with", "Mike") = true
person("report to", "Jerome") = true

person.relationships("Jim")
person.neighbors("work with")
person.neighbors("work with", "report to")
person("report to", "Jim")
person("report to", "Jerome")

// save document into a dataset
time { person into table }

// save it again. should be in no time.
time { person into table }

// Read back the document
val haifeng = time { "293050" of table }

// Read partially a document
val partial = time { "293050".from(table).select("name", "gender") }

// Remove a field
partial remove "gender"
partial commit

// Let's check if "gender" was deleted
val onlyname = time { "293050".from(table).select("name", "gender") }

// Restore gender
onlyname.gender = "Male"
onlyname.commit

// Turn on the cache
table cacheOn

val once = time { "293050" of table }
haifeng.name = "Haifeng Li"
haifeng.gender = null
haifeng commit

val twice = time { "293050" of table }

// wiki
val cluster = AccumuloServer("poc", "cdldvtitavap015:2181,cdldvtitavap016:2181,cdldvtitavap017:2181", "tester", "adpadp")
val wiki = cluster.dataset("wiki", "public")
1 of wiki

// Google+
val gplus = cluster.dataset("gplus", "public")
gplus cacheOn
val dan = "111065108889012087599" of gplus

//val graph = DocumentGraph(dan, 2, "follows")

class SimpleDocumentVisitor(maxHops: Int, relationships: String*) extends AbstractDocumentVisitor(maxHops, relationships) {
  val graph = new GraphOps[Document, (String, JsonValue)]()
  var doc: Document = null

  def bfs(doc: Document) {
    this.doc = doc
    graph.bfs(doc, this)
  }

  def dfs(doc: Document) {
    this.doc = doc
    graph.dfs(doc, this)
  }

  def visit(node: Document, edge: Edge[Document, (String, JsonValue)], hops: Int) {
    node.refreshRelationships
    if (hops > 0) println(doc.id + "--" + hops + "-->" + node.id)
  }
}

val visitor = new SimpleDocumentVisitor(3, "follows")
visitor.dfs(dan)
visitor.bfs(dan)

val astroph = cluster.dataset("astroph", "public")
astroph cacheOn
val author = 63225 of astroph
val visitor = new SimpleDocumentVisitor(2, "works with")
visitor.dfs(author)
visitor.bfs(author)
val graph = DocumentGraph(author, 2, "works with")
graph.topologicalSort
graph.dijkstra


// Make a small org chart for A* search
val server = AccumuloServer("local-poc", "127.0.0.1:2181", "tester", "adpadp")
val table = server.dataset("small", "public")

val haifeng = Document("Haifeng")
haifeng.rank = 1
haifeng("works with", "Roberto") = true
haifeng("reports to", "Jerome") = true

val roberto = Document("Roberto")
roberto.rank = 3
roberto("works with", "Keith") = true
roberto("reports to", "Mike") = true

val jerome = Document("Jerome")
jerome.rank = 2
jerome("works with", "Roberto") = true
jerome("reports to", "Mike") = true

val keith = Document("Keith")
keith.rank = 3
keith("works with", "Roberto") = true
keith("reports to", "Mike") = true

val mike = Document("Mike")
mike.rank = 4
mike("works with", "Jim") = true
mike("reports to", "Jerome") = true

haifeng into table
roberto into table
jerome  into table
keith   into table
mike    into table

val graphOps = new GraphOps[Document, (String, JsonValue)]()
val path = graphOps.astar(haifeng, mike,
  (a: Document, b: Document, e: (String, JsonValue)) => e._1 match {
    case "works with" => 1.
    case "reports to" => 2.
    case _ => 3.
    },
  (a: Document, b: Document) => (a.rank, b.rank) match {
    case (ar: JsonIntValue, br: JsonIntValue) => math.abs(ar.value - br.value)
    case _ => 100
  },
  (doc: Document) => {
    val neighbors = doc.neighbors("works with", "reports to")
    neighbors.foreach { case (doc, _) => doc.refreshRelationships }
    neighbors.iterator
  }
)

path.map {
  case (doc, Some(edge)) => edge._1 + " --> " + doc.id
  case (doc, None) => doc.id
}.mkString(" -- ")

val path = graphOps.astar(haifeng, mike,
  (a: Document, b: Document, e: (String, JsonValue)) => 1.,
  (a: Document, b: Document) => (a.rank, b.rank) match {
    case (ar: JsonIntValue, br: JsonIntValue) => math.abs(ar.value - br.value)
    case _ => 100
  },
  (doc: Document) => {
    val neighbors = doc.neighbors("reports to")
    neighbors.foreach { case (doc, _) => doc.refreshRelationships }
    neighbors.iterator
  }
)

path.map {
  case (doc, Some(edge)) => edge._1 + " --> " + doc.id
  case (doc, None) => doc.id
}.mkString(" -- ")

val path = graphOps.astar(haifeng, roberto,
  (a: Document, b: Document, e: (String, JsonValue)) => 1.,
  (a: Document, b: Document) => (a.rank, b.rank) match {
    case (ar: JsonIntValue, br: JsonIntValue) => math.abs(ar.value - br.value)
    case _ => 100
  },
  (doc: Document) => {
    val neighbors = doc.neighbors("reports to")
    neighbors.foreach { case (doc, _) => doc.refreshRelationships }
    neighbors.iterator
  }
)

path.map {
  case (doc, Some(edge)) => edge._1 + " --> " + doc.id
  case (doc, None) => doc.id
}.mkString(" -- ")