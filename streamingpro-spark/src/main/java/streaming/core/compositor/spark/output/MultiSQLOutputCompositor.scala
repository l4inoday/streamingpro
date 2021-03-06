package streaming.core.compositor.spark.output

import java.util
import java.util.Properties

import org.apache.log4j.Logger
import org.apache.spark.sql.{DataFrame, SaveMode}
import serviceframework.dispatcher.{Compositor, Processor, Strategy}
import streaming.common.SparkCompatibility
import streaming.core.compositor.spark.streaming.CompositorHelper
import streaming.core.strategy.ParamsValidator

import scala.collection.JavaConversions._

/**
  * 5/11/16 WilliamZhu(allwefantasy@gmail.com)
  */
class MultiSQLOutputCompositor[T] extends Compositor[T] with CompositorHelper with ParamsValidator {

  private var _configParams: util.List[util.Map[Any, Any]] = _
  val logger = Logger.getLogger(classOf[MultiSQLOutputCompositor[T]].getName)


  override def initialize(typeFilters: util.List[String], configParams: util.List[util.Map[Any, Any]]): Unit = {
    this._configParams = configParams
  }


  override def result(alg: util.List[Processor[T]], ref: util.List[Strategy[T]], middleResult: util.List[T], params: util.Map[Any, Any]): util.List[T] = {

    _configParams.foreach { config =>

      val name = config.getOrElse("name", "").toString
      val _cfg = config.map(f => (f._1.toString, f._2.toString)).map { f =>
        (f._1, params.getOrElse(s"streaming.sql.out.${name}.${f._1}", f._2).toString)
      }.toMap

      val tableName = _cfg("inputTableName")
      val options = _cfg - "path" - "mode" - "format"
      val _resource = _cfg("path")
      val mode = _cfg.getOrElse("mode", "ErrorIfExists")
      val format = _cfg("format")
      val outputFileNum = _cfg.getOrElse("outputFileNum", "-1").toInt

      val partitionBy = _cfg.getOrElse("partitionBy", "")

      val dbtable = if (options.containsKey("dbtable")) options("dbtable") else _resource

      var newTableDF = sqlContextHolder(params).table(tableName)

      if (outputFileNum != -1) {
        newTableDF = newTableDF.repartition(outputFileNum)
      }

      if (format == "console") {
        newTableDF.show(_cfg.getOrElse("showNum", "100").toInt)
      } else {

        var tempDf = if (!partitionBy.isEmpty) {
          newTableDF.write.partitionBy(partitionBy.split(","): _*)
        } else {
          newTableDF.write
        }

        tempDf = tempDf.options(options).mode(SaveMode.valueOf(mode)).format(format)

        if (SparkCompatibility.sparkVersion.startsWith("1.6") && format == "jdbc") {
          val properties = new Properties()
          options.foreach(kv => properties.put(kv._1, kv._2))
          tempDf.jdbc(options("url"), dbtable, properties)
        } else {
          if (_resource == "-" || _resource.isEmpty) {
            tempDf.save()
          } else tempDf.save(_resource)
        }
      }
    }

    new util.ArrayList[T]()
  }

  override def valid(params: util.Map[Any, Any]): (Boolean, String) = {
    (true, "")
  }
}
