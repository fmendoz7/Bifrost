package examples.bifrost.blocks

import com.google.common.primitives.{Bytes, Ints, Longs}
import examples.bifrost.transaction.box.{BifrostBoxSerializer, StableCoinBox}
import examples.bifrost.transaction.{BifrostTransaction, BifrostTransactionCompanion}
import examples.curvepos.transaction.SimpleBlock._
import io.circe.Json
import io.circe.syntax._
import scorex.core.NodeViewModifier.ModifierTypeId
import scorex.core.block.Block
import scorex.core.block.Block._
import scorex.core.crypto.hash.FastCryptographicHash
import scorex.core.serialization.Serializer
import scorex.core.transaction.box.proposition.{Constants25519, ProofOfKnowledgeProposition, PublicKey25519Proposition}
import scorex.core.transaction.proof.Signature25519
import scorex.core.transaction.state.PrivateKey25519
import scorex.crypto.encode.Base58
import scorex.crypto.signatures.Curve25519

import scala.util.Try

case class BifrostBlock(override val parentId: BlockId,
                        override val timestamp: Block.Timestamp,
                        generatorBox: StableCoinBox,
                        signature: Signature25519,
                        txs: Seq[BifrostTransaction])
  extends Block[ProofOfKnowledgeProposition[PrivateKey25519], BifrostTransaction] {

  override type M = BifrostBlock

  override lazy val modifierTypeId: Byte = BifrostBlock.ModifierTypeId

  override lazy val transactions: Option[Seq[BifrostTransaction]] = Some(txs)

  override lazy val serializer = BifrostBlockCompanion

  override lazy val id: BlockId = FastCryptographicHash(serializer.messageToSign(this))

  override lazy val version: Version = 0: Byte

  override lazy val json: Json = Map(
    "id" -> Base58.encode(id).asJson,
    "parentId" -> Base58.encode(parentId).asJson,
    "timestamp" -> timestamp.asJson,
    "generatorBox" -> Base58.encode(BifrostBoxSerializer.toBytes(generatorBox)).asJson,
    "signature" -> Base58.encode(signature.signature).asJson,
    "txs" -> txs.map(_.json).asJson
  ).asJson
}

object BifrostBlock {
  val ModifierTypeId = 3: Byte

  val SignatureLength = 64

  type GenerationSignature = Array[Byte]

  type BaseTarget = Long

  def create(parentId: BlockId,
             timestamp: Block.Timestamp,
             txs: Seq[BifrostTransaction],
             box: StableCoinBox,
             //attachment: Array[Byte],
             privateKey: PrivateKey25519): BifrostBlock = {
    assert(box.proposition.pubKeyBytes sameElements privateKey.publicKeyBytes)
    val unsigned = BifrostBlock(parentId, timestamp, box, Signature25519(Array.empty), txs)
    val signature = Curve25519.sign(privateKey.privKeyBytes, unsigned.bytes)
    unsigned.copy(signature = Signature25519(signature))
  }
}

object BifrostBlockCompanion extends Serializer[BifrostBlock] {


  def messageToSign(block: BifrostBlock): Array[Byte] = {

    val numTx = Ints.toByteArray(block.txs.length)
    val generatorBoxBytes = BifrostBoxSerializer.toBytes(block.generatorBox)

    Bytes.concat(
        block.parentId,
        Longs.toByteArray(block.timestamp),
        Longs.toByteArray(generatorBoxBytes.length),
        Array(block.version),
        generatorBoxBytes,
        block.signature.signature,
        numTx,  // writes number of transactions, then adds <tx as bytes>| <number of bytes for tx as bytes> for each tx
        block.txs.foldLeft(Array[Byte]())((bytes, tx) => bytes ++ Ints.toByteArray(BifrostTransactionCompanion.toBytes(tx).length) ++ BifrostTransactionCompanion.toBytes(tx))
    )
  }

  override def toBytes(block: BifrostBlock): Array[Byte] = {
    messageToSign(block)
  }

  override def parseBytes(bytes: Array[ModifierTypeId]): Try[BifrostBlock] = Try {

    val parentId = bytes.slice(0, Block.BlockIdLength)
    val Array(timestamp: Long, generatorBoxLen: Long) = (0 until 2).map {
      i => Longs.fromByteArray(bytes.slice(Block.BlockIdLength + i*Longs.BYTES, Block.BlockIdLength + (i + 1)*Longs.BYTES))
    }.toArray

    val version = bytes.slice(Block.BlockIdLength + Longs.BYTES, Block.BlockIdLength + Longs.BYTES + 1).head

    var numBytesRead = Block.BlockIdLength + Longs.BYTES*2 + 1

    val generatorBox = BifrostBoxSerializer.parseBytes(bytes.slice(numBytesRead, numBytesRead + generatorBoxLen.toInt)).get.asInstanceOf[StableCoinBox]
    val signature = Signature25519(bytes.slice(numBytesRead + generatorBoxLen.toInt, numBytesRead + generatorBoxLen.toInt + Signature25519.SignatureSize))

    numBytesRead += generatorBoxLen.toInt + Signature25519.SignatureSize

    val numTxExpected = Ints.fromByteArray(bytes.slice(numBytesRead, numBytesRead + Ints.BYTES))
    numBytesRead += Ints.BYTES

    def unfoldLeft[A,B](seed: B)(f: B => Option[(A, B)]): Seq[A] = {
      f(seed) match {
        case Some((a, b)) => a +: unfoldLeft(b)(f)
        case None => Nil
      }
    }

    val txBytes: Array[Byte] = bytes.slice(numBytesRead, bytes.length)

    val txByteSeq: Seq[Array[Byte]] = unfoldLeft(txBytes) {
      case b if b.length < Ints.BYTES => None
      case b =>
        val bytesToGrab = Ints.fromByteArray(b.take(Ints.BYTES))

        if (b.length - Ints.BYTES < bytesToGrab) {
          None // we're done because we can't grab the number of bytes required
        } else {
          val thisTx: Array[Byte] = b.slice(Ints.BYTES, Ints.BYTES + bytesToGrab)
          Some((thisTx, b.slice(Ints.BYTES + bytesToGrab, b.length)))
        }
    }.ensuring(_.length == numTxExpected)

    val tx: Seq[BifrostTransaction] = txByteSeq.map(tx => BifrostTransactionCompanion.parseBytes(tx).get)

    BifrostBlock(parentId, timestamp, generatorBox, signature, tx)
  }
}