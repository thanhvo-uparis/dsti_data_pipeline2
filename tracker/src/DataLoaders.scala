import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.Dataset
import org.apache.spark.sql.functions._

object DataLoaders {

	case class LoadCovidData(spark: SparkSession) {
		
		import spark.implicits._

		def fromCsv(fileToLoad: String): DataFrame = {
			val colsToKeep = List("date", "state", "cases", "deaths")
			val data = spark.read.option("header", true).csv(fileToLoad)
			val cols = data.columns.toList
			val colsToRemove = cols.diff(colsToKeep)
			data.drop(colsToRemove : _*)
		}

	}

}