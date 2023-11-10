package lambda.function

import java.util.{ Collections, Map => JavaMap }

import scala.jdk.CollectionConverters._

import com.amazonaws.services.lambda.runtime.events.SNSEvent
import com.amazonaws.services.lambda.runtime.{ Context, RequestHandler }
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder
import com.amazonaws.services.simplesystemsmanagement.model.GetParameterRequest
import com.amazonaws.services.simplesystemsmanagement.model.GetParameterResult

import com.typesafe.config._

object Main extends RequestHandler[SNSEvent, Unit] {

  private val config = ConfigFactory.load()

  private val ssmClient: AWSSimpleSystemsManagement = AWSSimpleSystemsManagementClientBuilder.defaultClient()
  private def buildRequest(key: String) =
    new GetParameterRequest().withName(key).withWithDecryption(true)

  override def handleRequest(input: SNSEvent, context: Context): Unit = {
    val rows = input.getRecords.asScala
    val sns = rows.headOption.map(_.getSNS)
    sns match {
      case Some(v) =>
        val result: GetParameterResult = ssmClient.getParameter(buildRequest(v.getMessage))
        println(config.getString(v.getMessage))
        setEnv(Map(v.getMessage -> result.getParameter.getValue))
        println(result.getParameter.getValue)
        println(config.getString(v.getMessage))
      case None => println("No Data")
    }
  }

  private def setEnv(newEnv: Map[String, String]): Unit = {
    try {
      val processEnvironmentClass = Class.forName("java.lang.ProcessEnvironment")
      val theEnvironmentField = processEnvironmentClass.getDeclaredField("theEnvironment")
      theEnvironmentField.setAccessible(true)
      val env = theEnvironmentField.get(null).asInstanceOf[JavaMap[String, String]]
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
}
