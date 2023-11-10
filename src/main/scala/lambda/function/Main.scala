package lambda.function

//import java.util.{ Collections, Map => JavaMap }

import scala.jdk.CollectionConverters._

import com.amazonaws.services.lambda.runtime.events.SNSEvent
import com.amazonaws.services.lambda.runtime.{ Context, RequestHandler }
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder
import com.amazonaws.services.simplesystemsmanagement.model.GetParameterRequest
import com.amazonaws.services.simplesystemsmanagement.model.GetParameterResult

import com.typesafe.config._

object Main extends RequestHandler[SNSEvent, Unit] {

  private val ssmClient: AWSSimpleSystemsManagement = AWSSimpleSystemsManagementClientBuilder.defaultClient()
  private def buildRequest(key: String) =
    new GetParameterRequest().withName(key).withWithDecryption(true)

  override def handleRequest(input: SNSEvent, context: Context): Unit = {
    val rows = input.getRecords.asScala
    val sns = rows.headOption.map(_.getSNS)
    sns match {
      case Some(v) =>
        val key = System.getenv(v.getMessage)
        val result: GetParameterResult = ssmClient.getParameter(buildRequest(key))
        println(s"config get: ${configValue(ConfigFactory.load(), v.getMessage)}")
        println(s"key: $key")
        updateEnvironmentVariable(key, result.getParameter.getValue)
        //setEnv(Map(key -> result.getParameter.getValue, v.getMessage -> result.getParameter.getValue))
        println(s"System get: ${System.getenv(key)}")
        println(s"set env config get: ${configValue(ConfigFactory.load(), v.getMessage)}")
        println(result.getParameter.getValue)
      case None => println("No Data")
    }
  }

  private def configValue(config: Config, key: String): Option[String] = if (config.hasPath(key) && !config.getIsNull(key)) {
    Some(config.getString(key))
  } else {
    None
  }

  private def updateEnvironmentVariable(key: String, value: String): Unit = {
    // 現在の環境変数を取得
    val env = System.getenv()
    // 環境変数を更新
    env.put(key, value)
    // 更新後の環境変数を設定
    updateEnvironment(env.asScala.toMap)
  }

  private def updateEnvironment(newEnv: Map[String, String]): Unit = {
    try {
      // Java 9以降では、ProcessBuilder#environmentメソッドを使用して環境変数を更新
      val processBuilder: ProcessBuilder = new ProcessBuilder()
      val env = processBuilder.environment()
      env.clear()
      env.putAll(newEnv.asJava)
    } catch {
      case ex: Exception => ex.printStackTrace()
    }
  }

  /*
  private def setEnv(newEnv: Map[String, String]): Unit = {
    try {
      val oldEnv = System.getenv()
      val processEnvironmentClass = Class.forName("java.lang.ProcessEnvironment")
      val theEnvironmentField = processEnvironmentClass.getDeclaredField("theEnvironment")
      theEnvironmentField.setAccessible(true)
      val env = theEnvironmentField.get(null).asInstanceOf[JavaMap[String, String]]
      val test = oldEnv.putAll(newEnv.asJava)
      env.putAll(newEnv.asJava)
      val theCaseInsensitiveEnvironmentField = processEnvironmentClass.getDeclaredField("theCaseInsensitiveEnvironment")
      theCaseInsensitiveEnvironmentField.setAccessible(true)
      val cienv = theCaseInsensitiveEnvironmentField.get(null).asInstanceOf[JavaMap[String, String]]
      cienv.putAll(newEnv.asJava)
    } catch {
      case _: NoSuchFieldException =>
        try {
          val classes = classOf[Collections].getDeclaredClasses
          val env = System.getenv()
          for (cl <- classes) {
            if (cl.getName == "java.util.Collections$UnmodifiableMap") {
              val field = cl.getDeclaredField("m")
              field.setAccessible(true)
              val obj = field.get(env)
              val map = obj.asInstanceOf[JavaMap[String, String]]
              map.clear()
              map.putAll(newEnv.asJava)
            }
          }
        } catch {
          case ex: Exception => ex.printStackTrace()
        }
      case ex: Exception => ex.printStackTrace()
    }
  }
   */
}
