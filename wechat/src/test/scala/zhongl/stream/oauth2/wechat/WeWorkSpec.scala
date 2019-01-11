package zhongl.stream.oauth2.wechat

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.ActorMaterializer
import org.scalatest._
import zhongl.stream.oauth2.FreshToken.InvalidToken

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

class WeWorkSpec extends WordSpec with Matchers with BeforeAndAfterAll with Directives with JsonSupport {
  implicit val system: ActorSystem    = ActorSystem(getClass.getSimpleName)
  implicit val mat: ActorMaterializer = ActorMaterializer()
  implicit val ec: ExecutionContext   = system.dispatcher

  private val default: (UserInfo, Uri) => HttpResponse = (_, _) => HttpResponse()
  private val token                                    = AccessToken("token", 60)

  var bound: ServerBinding = _

  "WeWork" should {
    "refresh token" in {
      Await.result(WeWork(default).refresh, 5.second).`access_token` shouldBe "token"
    }

    "create location header with uri state" in {
      val uri = "https://open.work.weixin.qq.com/wwopen/sso/qrConnect?" +
        "appid=your_corp_id&agentid=your_app_secret&redirect_uri=%2Fauthorized&state=aHR0cDovL3Rlc3Q="
      WeWork(default).authorization("http://test") shouldBe Location(uri)
    }

    "authenticated" in {
      val f = WeWork {
        case (UserInfo("zhongl", "ZhongLunFu", Seq(0), "zhong.lunfu@gmail.com", "", 0, 0, 1, "jushi"), _) => HttpResponse()
      }.authenticate(token, HttpRequest(uri = "/authorized?code=c&state=aHR0cDovL3Rlc3Q="))

      Await.result(f, 1.second) shouldBe HttpResponse()
    }

    "complain missing code or state" in {
      val f = WeWork(default).authenticate(token, HttpRequest())
      Await.result(f, 1.second).status shouldBe StatusCodes.BadRequest
    }

    "complain invalid token by 40014" in {
      val f = WeWork(default).authenticate(token, HttpRequest(uri = "/authorized?code=40014&state=aHR0cDovL3Rlc3Q="))
      intercept[IllegalArgumentException](Await.result(f, 1.second)) shouldBe InvalidToken
    }

    "complain invalid token by 42001" in {
      val f = WeWork(default).authenticate(token, HttpRequest(uri = "/authorized?code=42001&state=aHR0cDovL3Rlc3Q="))
      intercept[IllegalArgumentException](Await.result(f, 1.second)) shouldBe InvalidToken
    }

    "complain invalid response by non-json" in {
      val f = WeWork(default).authenticate(token, HttpRequest(uri = "/authorized?code=text&state=aHR0cDovL3Rlc3Q="))
      intercept[IllegalStateException](Await.result(f, 1.second)).getMessage shouldBe "200 OK - text"
    }
  }

  def mockWeWorkServer: Route = get {
    concat(
      (path("gettoken") & parameter('corpid) & parameter('corpsecret)) { (_, _) =>
        val json = "{\"errcode\":0,\"errmsg\":\"ok\",\"access_token\":\"token\",\"expires_in\":7200}"
        complete(HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, json)))
      },
      (path("user" / "getuserinfo") & parameter('access_token) & parameter('code)) {
        case (_, "40014") => complete(Err(40014))
        case (_, "42001") => complete(Err(42001))
        case (_, "text")  => complete("text")
        case (_, _)       => complete(Principal("zhongl"))
      },
      (path("user" / "get") & parameter('access_token) & parameter('userid)) { (_, _) =>
        complete(UserInfo("zhongl", "ZhongLunFu", Seq(0), "zhong.lunfu@gmail.com", "", 0, 0, 1, "jushi"))
      }
    )
  }

  override protected def beforeAll(): Unit = {
    val f = Http().bindAndHandle(mockWeWorkServer, "localhost", 12306)
    bound = Await.result(f, 1.second)
  }

  override protected def afterAll(): Unit = {
    Await.result(bound.unbind().flatMap(_ => system.terminate()), 3.second)
  }
}
