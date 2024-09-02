import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.Dataset
import org.apache.spark.sql.functions._

case class Compare(spark: SparkSession) {

  def compare(left: String, right: String) = {
    
    def joiner(ds1: DataFrame, ds2: DataFrame): DataFrame = {

      val ds_left = ds1
          .withColumnRenamed("cases", s"cases_src")
          .withColumnRenamed("deaths", s"deaths_src")
      
      val ds_right = ds2
          .withColumnRenamed("cases", s"cases_cmp")
          .withColumnRenamed("deaths", s"deaths_cmp")
      
      ds_left.join(ds_right, Seq("date", "state"), "full_outer")
    }

    val data1 = DataLoaders.LoadCovidData(spark).fromCsv(left)
    val data2 = DataLoaders.LoadCovidData(spark).fromCsv(right)

    val data1Name = left.split("/").last.replace(".", "_")
    val data2Name = right.split("/").last.replace(".", "_")

    data1.createOrReplaceTempView("SourceFile")
    data2.createOrReplaceTempView("CompareFile")
    joiner(data1, data2).createOrReplaceTempView("FileCompare")

    val summaryCompare = spark.sql(s"""
      SELECT DISTINCT
        "$data1Name" file_source
        , "$data2Name" file_compare
        , (SELECT COUNT(*) FROM SourceFile) source_rows
        , (SELECT COUNT(*) FROM CompareFile) compare_rows
        , (SELECT COUNT(*) FROM SourceFile WHERE CONCAT(date, state) NOT IN (SELECT CONCAT(date, state) FROM CompareFile)) source_rows_unmatched
        , (SELECT COUNT(*) FROM CompareFile WHERE CONCAT(date, state) NOT IN (SELECT CONCAT(date, state) FROM SourceFile)) compare_rows_unmatched
        , (SELECT COUNT(*) FROM SourceFile WHERE CONCAT(date, state) IN (SELECT CONCAT(date, state) FROM CompareFile)) rows_in_both
        , (SELECT COUNT(*) FROM FileCompare WHERE cases_src - cases_cmp = 0 AND deaths_src - deaths_src = 0) equal_rows
        , (SELECT COUNT(*) FROM FileCompare WHERE cases_src - cases_cmp != 0 OR deaths_src - deaths_cmp != 0) diff_rows 
        FROM FileCompare
      """)

    DataWriters.FileWriter(spark).writeSingleFile(summaryCompare, "diff_report.json")
  }

}