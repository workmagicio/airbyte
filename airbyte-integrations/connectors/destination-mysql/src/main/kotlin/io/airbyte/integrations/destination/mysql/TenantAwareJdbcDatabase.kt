package io.airbyte.integrations.destination.mysql

import io.airbyte.cdk.db.JdbcCompatibleSourceOperations
import io.airbyte.cdk.db.jdbc.DefaultJdbcDatabase
import io.airbyte.cdk.db.jdbc.JdbcUtils
import javax.sql.DataSource

class TenantAwareJdbcDatabase
@JvmOverloads
constructor(
    dataSource: DataSource,
    private val tenantId: Long,
    sourceOperations: JdbcCompatibleSourceOperations<*>? = JdbcUtils.defaultSourceOperations
) : DefaultJdbcDatabase(dataSource, sourceOperations) {

    /**
     * Method to retrieve the tenantId associated with this instance.
     */
    fun getTenantId(): Long {
        return tenantId
    }
}
