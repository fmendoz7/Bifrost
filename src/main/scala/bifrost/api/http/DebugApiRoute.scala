package bifrost.api.http

import javax.ws.rs.Path

import akka.actor.{ActorRef, ActorRefFactory}
import akka.http.scaladsl.server.Route
import io.circe.syntax._
import io.swagger.annotations._
import scorex.core.api.http.SuccessApiResponse
import scorex.core.settings.Settings
import scorex.core.transaction.box.proposition.PublicKey25519Proposition
import scorex.crypto.encode.Base58

import scala.concurrent.ExecutionContext.Implicits.global


@Path("/debug")
@Api(value = "/debug", description = "Useful functions", position = 3, produces = "application/json")
case class DebugApiRoute(override val settings: Settings, nodeViewHolderRef: ActorRef)
                        (implicit val context: ActorRefFactory) extends ApiRouteWithView {

  override val route: Route = pathPrefix("debug") {
    infoRoute ~ chain ~ delay ~ myblocks ~ generators
  }

  @Path("/delay/{id}/{blockNum}")
  @ApiOperation(value = "Average delay",
    notes = "Average delay in milliseconds between last $blockNum blocks starting from block with $id", httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "id", value = "Base58-encoded id", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "blockNum", value = "Number of blocks to count delay", required = true, dataType = "string", paramType = "path")
  ))
  def delay: Route = {
    path("delay" / Segment / IntNumber) { case (encodedSignature, count) =>
      getJsonRoute {
        viewAsync().map { view =>
          SuccessApiResponse(Map(
            "delay" -> Base58.decode(encodedSignature).flatMap(id => view.history.averageDelay(id, count))
              .map(_.toString).getOrElse("Undefined")
          ).asJson)
        }
      }
    }
  }

  @Path("/info")
  @ApiOperation(value = "Info", notes = "Debug info about blockchain", httpMethod = "GET")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Json with debug info or error")
  ))
  def infoRoute: Route = path("info") {
    getJsonRoute {
      viewAsync().map { view =>
        SuccessApiResponse( Map(
          "height" -> view.history.height.toString.asJson,
          "score" -> view.history.score.asJson,
          "bestBlockId" -> Base58.encode(view.history.bestBlockId).asJson,
          "bestBlock" -> view.history.bestBlock.json,
          "stateVersion" -> Base58.encode(view.state.version).asJson
        ).asJson)
      }
    }
  }

  @Path("/myblocks")
  @ApiOperation(value = "Info", notes = "Blocks generated by this node", httpMethod = "GET")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Json with my blocks or error")
  ))
  def myblocks: Route = path("myblocks") {
    getJsonRoute {
      viewAsync().map { view =>
        val pubkeys: Set[PublicKey25519Proposition] = view.vault.publicKeys.flatMap {
          case pkp: PublicKey25519Proposition => Some(pkp)
          case _ => None
        }

        val count = view.history.count(b => pubkeys.contains(b.forgerBox.proposition))

        SuccessApiResponse(Map(
          "pubkeys" -> pubkeys.map(pk => Base58.encode(pk.pubKeyBytes)).asJson,
          "count" -> count.asJson
        ).asJson)
      }
    }
  }

  @Path("/generators")
  @ApiOperation(value = "Info", notes = "Blocks generator distribution", httpMethod = "GET")
  def generators: Route = path("generators") {
    getJsonRoute {
      viewAsync().map { view =>
        val map: Map[String, Int] = view.history.forgerDistribution()
          .map(d => Base58.encode(d._1.pubKeyBytes) -> d._2)
        SuccessApiResponse(map.asJson)
      }
    }
  }

  @Path("/chain")
  @ApiOperation(value = "Chain", notes = "Print full chain", httpMethod = "GET")
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "Json with peer list or error")
  ))
  def chain: Route = path("chain") {
    getJsonRoute {
      viewAsync().map { view =>
        SuccessApiResponse(Map(
          "history" -> view.history.toString
        ).asJson)
      }
    }
  }
}
