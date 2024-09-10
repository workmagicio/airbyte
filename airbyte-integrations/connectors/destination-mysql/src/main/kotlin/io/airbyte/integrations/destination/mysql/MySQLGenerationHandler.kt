package io.airbyte.integrations.destination.mysql

import io.airbyte.cdk.db.jdbc.JdbcDatabase
import io.airbyte.cdk.integrations.destination.jdbc.JdbcGenerationHandler

class MySQLGenerationHandler : JdbcGenerationHandler {
    override fun getGenerationIdInTable(
        database: JdbcDatabase,
        namespace: String,
        name: String
    ): Long? {
        throw UnsupportedOperationException("ADB do not support getGenerationIdInTable")
//        val res1 =
//            database
//                .unsafeQuery(
//                    """SELECT 1
//            |               FROM information_schema.tables
//            |               WHERE table_schema = ?
//            |               AND table_name = ?
//            |               LIMIT 1
//        """.trimMargin(),
//                    namespace,
//                    name,
//                )
//                .toList()
//        val retVal: Long?
//        if (res1.isEmpty()) {
//            retVal = null
//        } else {
//            val res2 =
//                database
//                    .unsafeQuery("SELECT _airbyte_generation_id FROM $namespace.$name LIMIT 1;")
//                    .toList()
//            if (res2.isEmpty()) {
//                retVal = null
//            } else {
//                val genIdInTable = res2.first().get("_airbyte_generation_id").asLong()
//                LOGGER.info { "found generationId in table $namespace.$name: $genIdInTable" }
//                retVal = genIdInTable
//            }
//        }
//        return retVal
    }
}
