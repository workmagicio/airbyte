/*
 * Copyright (c) 2023 Airbyte, Inc., all rights reserved.
 */
package io.airbyte.integrations.destination.mysql

import io.airbyte.cdk.integrations.destination.StandardNameTransformer

class AnalyticdbForMysqlNameTransformer : StandardNameTransformer() {
    override fun getIdentifier(name: String): String {
        val identifier = applyDefaultCase(super.getIdentifier(name))
        return MySQLNameTransformer.truncateName(identifier, MAX_LENGTH)
    }

    override fun getTmpTableName(name: String): String {
        val tmpTableName = applyDefaultCase(super.getTmpTableName(name)).removePrefix("_airbyte_")
        return MySQLNameTransformer.truncateName(tmpTableName, MAX_LENGTH)
    }

    override fun getRawTableName(name: String): String {
        val rawTableName = applyDefaultCase(super.getRawTableName(name)).removePrefix("_airbyte_")
        return MySQLNameTransformer.truncateName(rawTableName, MAX_LENGTH)
    }

    override fun applyDefaultCase(input: String): String = input.lowercase(Locale.getDefault())

    companion object {
        const val MAX_LENGTH: Int = 127
    }
}
