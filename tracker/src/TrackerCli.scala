object TrackerCli {

  val errorMsg: String = s"""Command not recognized. Usage: 

  (1) [Aggregate to state level] agg <path/to/input/file> <output_filename.csv>
  (2) [Compare two aggregations] cmp <path/to/aggregate1> <path/to/aggregate2>"""

  def main(args: Array[String]): Unit = args match {
    case Array("agg", in, out) => SparkContext.runWithSpark(Aggregate(_).createAggregate(in, out))
    case Array("cmp", left, right) => SparkContext.runWithSpark(Compare(_).compare(left, right))
    case _ => println(errorMsg)
  }

}
