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


val LOGGER = KotlinLogging.logger {}

@SuppressFBWarnings(
    value = ["SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE"],
    justification =
        "There is little chance of SQL injection. There is also little need for statement reuse. The basic statement is more readable than the prepared statement."
)
class MySQLSqlOperations(val wmTenantId: Long) : JdbcSqlOperations() {

    @Throws(Exception::class)
    override fun executeTransaction(database: JdbcDatabase, queries: List<String>) {
        database.executeWithinTransaction(queries)
    }

    @Throws(Exception::class)
    override fun insertRecordsInternalV2(
        database: JdbcDatabase,
        records: List<PartialAirbyteMessage>,
        schemaName: String?,
        tableName: String?,
        syncId: Long,
        generationId: Long
    ) {
        if (records.isEmpty()) {
            return
        }
        try {
            val queryPrefix = "REPLACE INTO $schemaName.$tableName (wm_tenant_id, ${JavaBaseConstants.COLUMN_NAME_AB_RAW_ID}, ${JavaBaseConstants.COLUMN_NAME_DATA}, ${JavaBaseConstants.COLUMN_NAME_AB_EXTRACTED_AT}, ${JavaBaseConstants.COLUMN_NAME_AB_LOADED_AT}, ${JavaBaseConstants.COLUMN_NAME_AB_META}, ${JavaBaseConstants.COLUMN_NAME_AB_GENERATION_ID}) VALUES "
            val placeholders = "(?, ?, ?, ?, ?, ?, ?)"
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
                        val airbyteMeta =
                            if (record.record!!.meta == null) {
                                """{"changes":[],"${JavaBaseConstants.AIRBYTE_META_SYNC_ID_KEY}":$syncId}"""
                            } else {
                                Jsons.serialize(
                                    record.record!!
                                        .meta!!
                                        .withAdditionalProperty(
                                            JavaBaseConstants.AIRBYTE_META_SYNC_ID_KEY,
                                            syncId,
                                        )
                                )
                            }
                        val extractedAt = Timestamp.from(Instant.ofEpochMilli(record.record!!.emittedAt))

                        stmt.setLong(index++, wmTenantId)
                        stmt.setString(index++, pk)
                        stmt.setString(index++, jsonData)
                        stmt.setTimestamp(index++, extractedAt)
                        stmt.setTimestamp(index++, extractedAt)
                        stmt.setString(index++, airbyteMeta)
                        stmt.setLong(index++, generationId)
                    }

                    val results = stmt.execute()
                    LOGGER.info { "[WorkMagic] stmt.execute results=${Jsons.serialize(results)}" };
                }
            }
        } catch (e: IOException) {
            throw SQLException(e)
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
        // hack for ADB, do not check version. Also support mysql
        val version = 1.0
        return VersionCompatibility(version, version >= 0)
//        val version = getVersion(database)
//        return VersionCompatibility(version, version >= 3.1)
    }

    override val isSchemaRequired: Boolean
        get() = false

    override fun overwriteRawTable(database: JdbcDatabase, rawNamespace: String, rawName: String) {
        throw UnsupportedOperationException("ADB do not support overwriteRawTable")
//        val tmpName = rawName + AbstractStreamOperation.TMP_TABLE_SUFFIX
//        database.executeWithinTransaction(
//            listOf(
//                "ALTER TABLE $rawNamespace.$tmpName TO $rawNamespace.$rawName, $rawNamespace.$rawName TO $rawNamespace.$tmpName"
//            )
//        )
    }

    override fun createTableQueryV1(schemaName: String?, tableName: String?): String {
        throw UnsupportedOperationException("ADB requires V2")
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
        %s TIMESTAMP(6),
        %s TIMESTAMP(6),
        %s JSON,
        %s BIGINT,
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
            JavaBaseConstants.COLUMN_NAME_AB_GENERATION_ID,
            JavaBaseConstants.COLUMN_NAME_AB_RAW_ID
        )
    }

    class VersionCompatibility(val version: Double, val isCompatible: Boolean)
}
