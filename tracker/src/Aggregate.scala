import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.Dataset
import org.apache.spark.sql.functions._
import org.apache.hadoop.fs.{FileSystem, Path}

case class Aggregate(spark: SparkSession) {

  def createAggregate(filePath: String, fileName: String) = {
    
    val data = DataLoaders.LoadCovidData(spark).fromCsv(filePath)

    data.createOrReplaceTempView("CovidDataByCity")
    
    val dataByState = spark.sql("""
        SELECT date, state, SUM(cases) as cases, SUM(deaths) as deaths 
        FROM CovidDataByCity 
        GROUP BY date, state 
        ORDER BY date, state DESC
        """)  

    DataWriters.FileWriter(spark).writeSingleFile(dataByState, fileName)
  }

}