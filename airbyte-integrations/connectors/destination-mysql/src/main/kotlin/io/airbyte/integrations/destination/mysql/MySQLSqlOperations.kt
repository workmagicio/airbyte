/*
 * Copyright (c) 2023 Airbyte, Inc., all rights reserved.
 */
package io.airbyte.integrations.destination.mysql

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings
import io.airbyte.cdk.db.jdbc.JdbcDatabase
import io.airbyte.cdk.integrations.base.JavaBaseConstants
import io.airbyte.cdk.integrations.destination.async.model.PartialAirbyteMessage
import io.airbyte.cdk.integrations.destination.jdbc.JdbcSqlOperations
import io.airbyte.commons.json.Jsons
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.IOException
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.util.*


private val LOGGER = KotlinLogging.logger {}

@SuppressFBWarnings(
    value = ["SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE"],
    justification =
        "There is little chance of SQL injection. There is also little need for statement reuse. The basic statement is more readable than the prepared statement."
)
class MySQLSqlOperations : JdbcSqlOperations() {

    @Throws(Exception::class)
    override fun executeTransaction(database: JdbcDatabase, queries: List<String>) {
        database.executeWithinTransaction(queries)
    }

    @Throws(SQLException::class)
    public override fun insertRecordsInternal(
        database: JdbcDatabase,
        records: List<PartialAirbyteMessage>,
        schemaName: String?,
        tmpTableName: String?
    ) {
        throw UnsupportedOperationException("Mysql requires V2")
    }

    @Throws(Exception::class)
    override fun insertRecordsInternalV2(
        database: JdbcDatabase,
        records: List<PartialAirbyteMessage>,
        schemaName: String?,
        tableName: String?
    ) {
        if (records.isEmpty()) {
            return
        }
        try {
            replaceIntoWithPk(
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
    private fun replaceIntoWithPk(
        database: JdbcDatabase,
        records: List<PartialAirbyteMessage>,
        schemaName: String?,
        tableName: String?
    ) {
        val wmTenantId = (database as? TenantAwareJdbcDatabase)!!.getTenantId()

        val queryPrefix = "REPLACE INTO $schemaName.$tableName (wm_tenant_id, ${JavaBaseConstants.COLUMN_NAME_AB_RAW_ID}, ${JavaBaseConstants.COLUMN_NAME_DATA}, ${JavaBaseConstants.COLUMN_NAME_AB_EXTRACTED_AT}, ${JavaBaseConstants.COLUMN_NAME_AB_LOADED_AT}, ${JavaBaseConstants.COLUMN_NAME_AB_META}) VALUES "
        val placeholders = "(?, ?, ?, ?, ?, ?)"
        val query = queryPrefix + List(records.size) { placeholders }.joinToString(", ")

        database.execute { connection ->
            connection.prepareStatement(query).use { stmt ->
                var index = 1
                records.forEach { record ->
                    val catalog = record.catalog?.streams
                        ?.firstOrNull { record.record?.stream == it.stream.name }

                    val fallbackPk = "fallback_" + UUID.randomUUID().toString()

                    val data = Jsons.deserializeExact(record.serialized) //  StandardNameTransformer.formatJsonPath(data!!)
                    val pk = catalog?.let { c ->
                        c.primaryKey.flatten()
                            .filter(String::isNotBlank)
                            .mapNotNull { data[it]?.asText() }
                            .takeIf { it.isNotEmpty() }
                            ?.joinToString("|")
                            ?: c.cursorField
                                .filter(String::isNotBlank)
                                .mapNotNull { data[it]?.asText() }
                                .takeIf { it.isNotEmpty() }
                                ?.joinToString("|")
                    } ?: fallbackPk

                    val jsonData = record.serialized
                    val airbyteMeta = record.record?.meta?.let { Jsons.serialize(it) } ?: "{\"changes\":[]}"
                    val extractedAt = Timestamp.from(Instant.ofEpochMilli(record.record!!.emittedAt))

                    stmt.setLong(index++, wmTenantId)
                    stmt.setString(index++, pk)
                    stmt.setString(index++, jsonData)
                    stmt.setTimestamp(index++, extractedAt)
                    stmt.setTimestamp(index++, extractedAt)
                    stmt.setString(index++, airbyteMeta)
                }

                val results = stmt.execute()
                LOGGER.info { "[WorkMagic] stmt.execute results=${Jsons.serialize(results)}" };
            }
        }
    }

    @Throws(SQLException::class)
    private fun getVersion(database: JdbcDatabase): Double {
        val versions =
            database.queryStrings(
                { connection: Connection ->
                    connection.createStatement().executeQuery("select adb_version()")
                },
                { resultSet: ResultSet -> resultSet.getString("source_version") }
            )
        return versions[0].substring(0, 3).toDouble()
    }

    @Throws(SQLException::class)
    fun isCompatibleVersion(database: JdbcDatabase): VersionCompatibility {
        val version = getVersion(database)
        return VersionCompatibility(version, version >= 3.1)
    }

    override val isSchemaRequired: Boolean
        get() = false

    override fun createTableQueryV1(schemaName: String?, tableName: String?): String {
        throw UnsupportedOperationException("Mysql requires V2")
    }

    override fun createTableQueryV2(schemaName: String?, tableName: String?): String {
        // MySQL requires byte information with VARCHAR. Since we are using uuid as value for the
        // column,
        // 256 is enough
        return String.format(
            """
        CREATE TABLE IF NOT EXISTS %s.%s (
        wm_tenant_id BIGINT,
        %s VARCHAR(256),
        %s JSON,
        %s TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6),
        %s TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6),
        %s JSON,
        PRIMARY KEY (wm_tenant_id, %s)
        );
        
        """.trimIndent(),
            schemaName,
            tableName,
            JavaBaseConstants.COLUMN_NAME_AB_RAW_ID,
            JavaBaseConstants.COLUMN_NAME_DATA,
            JavaBaseConstants.COLUMN_NAME_AB_EXTRACTED_AT,
            JavaBaseConstants.COLUMN_NAME_AB_LOADED_AT,
            JavaBaseConstants.COLUMN_NAME_AB_META,
            JavaBaseConstants.COLUMN_NAME_AB_RAW_ID
        )
    }

    class VersionCompatibility(val version: Double, val isCompatible: Boolean)
}
