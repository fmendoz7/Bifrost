package bifrost.modifier.transaction.bifrostTransaction

import bifrost.crypto.{PrivateKey25519, Signature25519}
import bifrost.settings.Settings
import bifrost.modifier.transaction.bifrostTransaction.Transaction.Nonce
import bifrost.modifier.box._
import bifrost.modifier.box.proposition.{ProofOfKnowledgeProposition, PublicKey25519Proposition}
import bifrost.crypto.PrivateKey25519Companion
import bifrost.wallet.Wallet
import bifrost.BifrostApp
import bifrost.modifier.transaction.BoxTransaction
import com.google.common.primitives.Longs
import io.circe.{Json}
import io.circe.parser.parse
import scorex.crypto.encode.Base58

import scala.io.Source
import scala.util.Try

trait TransactionSettings extends Settings

trait Transaction
  extends BoxTransaction[ProofOfKnowledgeProposition[PrivateKey25519], Any, Box] {
  lazy val bloomTopics: Option[IndexedSeq[Array[Byte]]] = None

  val boxIdsToOpen: IndexedSeq[Array[Byte]]

  implicit lazy val settings = new TransactionSettings {
    val testnetEndowment: Nonce = 20L
    override lazy val settingsJSON: Map[String, Json] = settingsFromFile(BifrostApp.settingsFilename)

    override def settingsFromFile(filename: String): Map[String, Json] = Try {
      val jsonString = Source.fromFile(filename).mkString
      parse(jsonString).right.get
    }
      .recoverWith {
        case _ =>
          Try {
            val jsonString = Source.fromURL(getClass.getResource(s"/$filename")).mkString
            parse(jsonString).right.get
          }
      }
      .toOption
      .flatMap(_.asObject)
      .map(_.toMap)
      .getOrElse(Map())
  }

}

object Transaction {
  type Nonce = Long
  type Value = Long

  def stringToPubKey(rawString: String): PublicKey25519Proposition =
    PublicKey25519Proposition(Base58.decode(rawString).get)

  def stringToSignature(rawString: String): Signature25519 = Signature25519(Base58.decode(rawString).get)

  def nonceFromDigest(digest: Array[Byte]): Nonce = Longs.fromByteArray(digest.take(Longs.BYTES))

  def signTx(w: Wallet, props: IndexedSeq[PublicKey25519Proposition], message: Array[Byte]):
  Map[PublicKey25519Proposition, Signature25519] = props.map { prop =>
    val secret = w.secretByPublicImage(prop).get
    val signature = PrivateKey25519Companion.sign(secret, message)
    prop -> signature
  }.toMap
}