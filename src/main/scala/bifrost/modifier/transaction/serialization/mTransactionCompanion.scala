package bifrost.modifier.transaction.serialization

import bifrost.modifier.transaction.bifrostTransaction.{AssetCreation, AssetRedemption, ProgramTransaction, ProgramTransfer, Transaction, TransferTransaction}
import bifrost.serialization.Serializer
import com.google.common.primitives.Ints

import scala.util.Try

object mTransactionCompanion extends Serializer[Transaction] {

  override def toBytes(m: Transaction): Array[Byte] = m match {
    case c: ProgramTransaction => ProgramTransactionCompanion.toBytes(c)
    case prT: ProgramTransfer => ProgramTransferCompanion.toBytes(prT)
    case p: TransferTransaction => TransferTransactionCompanion.toBytes(p)
    case ar: AssetRedemption => AssetRedemptionCompanion.toBytes(ar)
    case ac: AssetCreation => AssetCreationCompanion.toBytes(ac) //AssetCreationCompanion.toBytes(ac)
  }

  override def parseBytes(bytes: Array[Byte]): Try[Transaction] = Try {
    val typeLength = Ints.fromByteArray(bytes.slice(0, Ints.BYTES))
    val typeStr = new String(bytes.slice(Ints.BYTES, Ints.BYTES + typeLength))

    typeStr match {
      case "ProgramTransaction" => ProgramTransactionCompanion.parseBytes(bytes).get
      case "ProgramTransfer" => ProgramTransferCompanion.parseBytes(bytes).get
      case "TransferTransaction" => TransferTransactionCompanion.parseBytes(bytes).get
      case "AssetRedemption" => AssetRedemptionCompanion.parseBytes(bytes).get
      case "AssetCreation" => AssetCreationCompanion.parseBytes(bytes).get
      case "CoinbaseTransaction" => CoinbaseTransactionCompanion.parseBytes(bytes).get
    }
  }
}

