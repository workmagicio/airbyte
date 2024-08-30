/*
 * Copyright (c) 2023 Airbyte, Inc., all rights reserved.
 */
package io.airbyte.integrations.destination.mysql

import io.airbyte.cdk.db.jdbc.JdbcDatabase
import io.airbyte.cdk.integrations.base.JavaBaseConstants
import io.airbyte.cdk.integrations.destination.async.model.PartialAirbyteMessage
import java.io.IOException
import java.sql.Connection
import java.sql.SQLException
import org.apache.commons.codec.digest.MurmurHash3

class AnalyticdbForMysqlSqlOperations(
    config: JsonNode,
) : MySQLSqlOperations() {
    @Throws(Exception::class)
    override fun insertRecordsInternalV2(
        database: JdbcDatabase,
        records: List<PartialAirbyteMessage>,
        schema: String?,
        table: String?,
    ) {
        if (records.isEmpty()) {
            return
        }
        try {
            loadDataIntoTable(
                database,
                records,
                schema,
                table,
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
            id: String,
            meta: String,
            data: String,
        )

        val tenantId =
            JdbcUtils
                .parseJdbcParameters(
                    config,
                    JdbcUtils.JDBC_URL_PARAMS_KEY,
                ).get("x_tenant_id")

        var records =
            origins.mapNotNull { record ->
                val data = Jsons.deserializeExact(record.serialized)
                // data.put("tenant_id", tenantId)
                val meta = record.record?.meta?.let { Jsons.jsonNode(it) } ?: Jsons.emptyObject()
                meta.put("tenant_id", tenantId)
                meta.put("hash", hash(o.serialized))
                val id =
                    record.catalog
                        ?.streams
                        ?.firstOrNull { it.stream.name == record.record?.stream }
                        ?.let {
                            it.primaryKey
                                .map { data[it]?.asText() ?: "" } // todo deep take
                                ?.joinToString("|")
                        }
                if (id == null) {
                    LOGGER.warning { "invalid primary key ${Jsons.serialize(record)}" }
                    return null
                }
                Record(id = id, meta = Jsons.serialize(meta), data = record.serialized)
            }

        val results =
            database.queryJsons(
                """
                select
                    ${JavaBaseConstants.COLUMN_NAME_AB_RAW_ID},
                    ${JavaBaseConstants.COLUMN_NAME_AB_META}
                from
                    $schema.$table
                where
                    ${JavaBaseConstants.COLUMN_NAME_AB_RAW_ID} in (${List(
                    records.size,
                ) { "?" }.joinToString(", ")})
                """.trimIndent(),
                records.map { it.id },
            )
        val hashmap = results.toMap // todo

        records = records.filter { x -> x.meta["hash"] != hashmap[x.id] }

        val insert =
            """
            replace into $schema.$table (${JavaBaseConstants.COLUMN_NAME_AB_RAW_ID}, ${JavaBaseConstants.COLUMN_NAME_AB_META}, ${JavaBaseConstants.COLUMN_NAME_DATA})
            values ${List(
                records.size,
            ) { "(?, cast(? as json), cast(? as json))" }.joinToString(", ")}
            """.trimIndent()

        val params = records.flatMap { x -> listOf(x.id, x.meta, x.data) }

        database.execute { connection: Connection ->
            try {
                connection.prepareStatement(insert).use { statement ->
                    params.forEachIndexed { (i, x) -> statement.setString(1 + i, x) }
                    val result = statement.execute()
                    LOGGER.debug { "insert result ${Jsons.serialize(results)}" }
                }
            } catch (e: Exception) {
                throw RuntimeException(e)
            }
        }
    }

    private fun hash(data: String) {
        val hash = MurmurHash3.hash128x64(data.bytes)
        return String.format("%016x%016x", hash[0], hash[1])
    }
}

object JavaMoreConstants {
    const val COLUMN_NAME_TENANT_ID: String = "tenant_id"
}
