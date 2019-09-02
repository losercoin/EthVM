package com.ethvm.processing.processors

import com.ethvm.avro.capture.CanonicalKeyRecord
import com.ethvm.avro.capture.TraceListRecord
import com.ethvm.avro.processing.BalanceDeltaType
import com.ethvm.avro.processing.TokenType
import com.ethvm.common.extensions.bigInteger
import com.ethvm.common.extensions.hexToBI
import com.ethvm.db.Tables.*
import com.ethvm.db.tables.records.BalanceDeltaRecord
import com.ethvm.processing.cache.FungibleBalanceCache
import com.ethvm.processing.cache.InternalTxsCountsCache
import com.ethvm.processing.extensions.toBalanceDeltas
import com.ethvm.processing.extensions.toDbRecords
import mu.KotlinLogging
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.jooq.DSLContext
import org.koin.core.inject
import org.koin.core.qualifier.named
import java.math.BigDecimal
import java.math.BigInteger
import java.sql.Timestamp
import java.time.Duration

class EtherBalanceProcessor : AbstractProcessor<TraceListRecord>() {

  override val logger = KotlinLogging.logger {}

  override val processorId: String = "ether-balance-processor"

  private val topicTraces: String by inject(named("topicTraces"))

  override val topics: List<String> = listOf(topicTraces)

  private lateinit var fungibleBalanceCache: FungibleBalanceCache

  private lateinit var internalTxsCountsCache: InternalTxsCountsCache

  override val maxTransactionTime = Duration.ofMillis(300)

  override fun blockHashFor(value: TraceListRecord): String = value.blockHash

  override fun initialise(txCtx: DSLContext, latestSyncBlock: BigInteger?) {

    fungibleBalanceCache = FungibleBalanceCache(memoryDb, diskDb, scheduledExecutor, TokenType.ETHER)
    fungibleBalanceCache.initialise(txCtx)

    internalTxsCountsCache = InternalTxsCountsCache(memoryDb, diskDb, scheduledExecutor)
    internalTxsCountsCache.initialise(txCtx)
  }

  override fun reset(txCtx: DSLContext) {

    txCtx.truncate(TRACE).execute()

    fungibleBalanceCache.reset(txCtx)
    internalTxsCountsCache.reset(txCtx)
  }

  override fun rewindUntil(txCtx: DSLContext, blockNumber: BigInteger) {

    fungibleBalanceCache.rewindUntil(txCtx, blockNumber)
    internalTxsCountsCache.rewindUntil(txCtx, blockNumber)

    txCtx
      .deleteFrom(TRACE)
      .where(TRACE.BLOCK_NUMBER.ge(blockNumber.toBigDecimal()))
      .execute()
  }

  override fun process(txCtx: DSLContext, record: ConsumerRecord<CanonicalKeyRecord, TraceListRecord>) {

    val blockNumber = record.key().number.bigInteger()

    var deltas =
      if (blockNumber > BigInteger.ZERO) {
        emptyList()
      } else {
        // Premine balance allocations from Genesis block

        val genesisBlock = netConfig.genesis

        var timestampMs = genesisBlock.timestamp
        if (timestampMs == 0L) {
          timestampMs = System.currentTimeMillis()
        }

        genesisBlock
          .allocations
          .map { (address, balance) ->

            BalanceDeltaRecord().apply {
              this.address = address
              this.counterpartAddress = null
              this.blockNumber = BigDecimal.ZERO
              this.blockHash = genesisBlock.hash
              this.deltaType = BalanceDeltaType.PREMINE_BALANCE.toString()
              this.tokenType = TokenType.ETHER.toString()
              this.amount = balance.balance.hexToBI().toBigDecimal()
              this.timestamp = Timestamp(timestampMs)
              this.isReceiving = true
            }
          }
      }

    // hard forks

    deltas = deltas + netConfig
      .chainConfigForBlock(blockNumber)
      .hardForkBalanceDeltas(blockNumber)

    // deltas for traces

    val traceList = record.value()

    deltas = deltas + record.value().toBalanceDeltas()

    deltas.forEach { delta ->

      when (val tokenType = delta.tokenType) {
        TokenType.ETHER.toString() -> fungibleBalanceCache.add(delta)
        else -> throw UnsupportedOperationException("Unexpected token type: $tokenType")
      }
    }

    // internal tx counts
    deltas
      .groupBy { it.blockNumber }
      .forEach { internalTxsCountsCache.count(it.value, it.key.toBigInteger()) }

    // transaction traces

    val traceRecords = traceList.toDbRecords()
    txCtx.batchInsert(traceRecords).execute()

    // write delta records

    txCtx.batchInsert(deltas).execute()

    // write balance records

    fungibleBalanceCache.writeToDb(txCtx)

    // write count records
    internalTxsCountsCache.writeToDb(txCtx)
  }
}
