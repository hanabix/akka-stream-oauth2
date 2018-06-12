## Desgin


```scala
// Http Flow with Guard and Routes
Inlet[HttpRequest] ~> Guard ~> Routes ~> Merge ~> Outlet[Future[HttpResponse]]
                      Guard           ~> Merge

type Guard = FanOut[HttpRequest, HttpRequest, Future[HttpReponse]]
// Guard
Inlet[HttpRequest] ~> Partition                                   ~> Outlet0[HttpRequest]
                      Partition ~> RedirectToAuthorizate ~> Merge ~> Outlet1[Future[HttpResponse]]
                      Partition ~> Authenticate          ~> Merge


type OAuth = Flow[(Future[AccessToken], HttpRequest), (Future[AccessToken], Future[HttpReponse])]
// Authencitate Flow
Inlet[HttpRequest] ~> Zip ~> OAuth2 ~> Unzip ~> Outlet[Future[HttpResponse]]
                      Zip <~ Merge  <~ Unzip
                             Merge  <~ Source[Future[AccessToken]]


def oauth(fetchToken: HttpRequest, fetchUser: String => HttpRequest, http: HttpRequest => Future[HttpResponse]):
(Future[AccessToken], HttpRequest) => (Future[AccessToken], Futurn[HttpResponse]) = {
    case (af, req) =>

}

trait OAuth2 {
  type AccessToken
  type Principal
  type Code

  protected def refresh: Future[AccessToken]
  protected def principal(c: Code, a: AccessToken): Future[Principal]

  def apply(at: Future[AccessToken], c: Code): (Future[AccessToken], Future[Principal]) = {
    at.recoverWith { case _ => refresh }.flatMap { a =>
      val p = Promise[AccessToken]
      val f = principal(c, a)
      f.onComplete {
        case Failure(cause: InvalidToken) => p.failed(cause)
        case _                            => p.success(a)
      }
      (p.future, f)
    }
  }
}
```
