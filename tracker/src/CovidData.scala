case class CovidData(
  date: String,
  state: String, 
  cases: String, 
  deaths: String
)

object CovidData {

  val R = """^(?<date>[-0-9]*),(?<state>[A-Z]*),(?<cases>[.0-9]*),(?<deaths>[.0-9]*)$""".r

  def fromString(s: String): TraversableOnce[CovidData] = s match {
    case R(date:String, state:String, cases:String, deaths:String) => 
      Some(CovidData(date, state, cases, deaths))
    case _ => None
  }

}

