/*
 * Copyright (c) 2023 Airbyte, Inc., all rights reserved.
 */
package io.airbyte.integrations.destination.mysql

import com.fasterxml.jackson.databind.node.ObjectNode
import io.airbyte.cdk.db.jdbc.JdbcDatabase
import io.airbyte.cdk.integrations.base.JavaBaseConstants
import io.airbyte.cdk.integrations.destination.async.model.PartialAirbyteMessage
import io.airbyte.commons.json.Jsons
import java.io.IOException
import java.sql.Connection
import java.sql.SQLException
import kotlin.random.Random
import org.apache.commons.codec.digest.MurmurHash3
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class AnalyticdbForMysqlSqlOperations : MySQLSqlOperations() {

    @Throws(Exception::class)
    override fun insertRecordsInternalV2(
        database: JdbcDatabase,
        records: List<PartialAirbyteMessage>,
        schemaName: String?,
        tableName: String?,
    ) {
        if (records.isEmpty()) {
            return
        }
        try {
            loadDataIntoTable(
                database,
                records,
                schemaName,
                tableName,
            )
        } catch (e: IOException) {
            throw SQLException(e)
        }
    }

    @Throws(SQLException::class)
    private fun loadDataIntoTable(
        database: JdbcDatabase,
        origins: List<PartialAirbyteMessage>,
        schema: String?,
        table: String?,
    ) {
        data class Record(
            val id: String,
            val meta: String,
            val data: String,
            val hash: String,
        )

        LOGGER.info("database: {}", database)
        LOGGER.info("database source config: {}", database.sourceConfig)
        LOGGER.info("database database config: {}", database.databaseConfig)

        // todo
//        val config = Jsons.deserialize("""{}""")

//        val tenantId =
//            JdbcUtils.parseJdbcParameters(config, JdbcUtils.JDBC_URL_PARAMS_KEY)["x_tenant_id"]

        val tenantId = 1

        var records =
            origins.mapNotNull { record ->
                LOGGER.info("record: {}", record)
                LOGGER.info("record stream descriptor: {}", record.state?.stream?.streamDescriptor)

                val node = (record.record?.data ?: Jsons.emptyObject()) as ObjectNode

                LOGGER.info("node: {}", node)

                node.put("tenant_id", tenantId)

                LOGGER.info("node: {}", node)

                // val meta = ((record.record?.meta?.let { Jsons.jsonNode(it) }
                //     ?: Jsons.emptyObject()) as ObjectNode)
                val meta = Jsons.jsonNode(record.record?.meta ?: Jsons.emptyObject()) as ObjectNode

                LOGGER.info("meta: {}", meta)

                meta.put("tenant_id", tenantId)

                LOGGER.info("meta: {}", meta)

                val data = Jsons.serialize(node)

                LOGGER.info("data: {}", data)

                val hash = hash(data)

                LOGGER.info("hash: {}", hash)

                meta.put("hash", hash)

                LOGGER.info("meta: {}", meta)

                val id = Random.nextLong().toString()
//                val id =
//                    record.catalog
//                        ?.streams
//                        ?.firstOrNull { it.stream.name == record.record?.stream }
//                        ?.let {
//                            it.primaryKey
//                                .map { data[it]?.asText() ?: "" } // todo deep take
//                                ?.joinToString("|")
//                        }
                if (id == null) {
                    LOGGER.warn("invalid primary key $record")
                    null
                } else {
                    Record(id = id, meta = Jsons.serialize(meta), data = data, hash = hash)
                }
            }

        LOGGER.info("records: {}", records)

        val results =
            database.queryJsons(
                """
                select
                    ${JavaBaseConstants.COLUMN_NAME_AB_RAW_ID} as id,
                    ${JavaBaseConstants.COLUMN_NAME_AB_META} as meta
                from
                    $schema.$table
                where
                    ${JavaBaseConstants.COLUMN_NAME_AB_RAW_ID} in (${
                    List(records.size) { "?" }.joinToString(", ")
                })
                """.trimIndent(),
                * records.map { it.id }.toTypedArray(),
            )

        LOGGER.info("results: {}", results)

        val hashmap =
            results.associate { x ->
                x["id"].asText() to Jsons.deserialize(x["meta"].asText()).path("hash").asText()
            }

        LOGGER.info("hashmap: {}", hashmap)

        records = records.filter { x -> x.hash != hashmap[x.id] }

        LOGGER.info("records: {}", records)

        val params = records.flatMap { x -> listOf(x.id, x.meta, x.data) }

        LOGGER.info("params: {}", params)

        val insert =
            """
            replace into $schema.$table (
                ${JavaBaseConstants.COLUMN_NAME_AB_RAW_ID},
                ${JavaBaseConstants.COLUMN_NAME_AB_META},
                ${JavaBaseConstants.COLUMN_NAME_DATA}
            )
            values ${
                List(records.size) { "(?, ?, ?)" }.joinToString(", ")
            }
            """.trimIndent()

        LOGGER.info("insert: {}", insert)

        database.execute { connection: Connection ->
            try {
                connection.prepareStatement(insert).use { statement ->
                    params.forEachIndexed { i, x -> statement.setString(1 + i, x) }
                    val result = statement.execute()
                    LOGGER.debug("insert result $result")
                }
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }
    }

    private fun hash(data: String): String {
        val hash = MurmurHash3.hash128x64(data.toByteArray())
        return String.format("%016x%016x", hash[0], hash[1])
    }

    companion object {
        private val LOGGER: Logger =
            LoggerFactory.getLogger(AnalyticdbForMysqlSqlOperations::class.java)
    }
}
