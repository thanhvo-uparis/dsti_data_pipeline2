import org.apache.spark.sql.SparkSession

object SparkContext {

  private def createSession = {
    val builder = SparkSession.builder.appName("BRA Covid19 Aggregator")
    builder.getOrCreate()
  }

  def runWithSpark(f: SparkSession => Unit): Unit = {
    val spark = createSession
    f(spark)
    spark.close
  }

}