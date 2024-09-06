/*
 * Copyright (c) 2023 Airbyte, Inc., all rights reserved.
 */
package io.airbyte.integrations.destination.mysql

import io.airbyte.cdk.integrations.destination.StandardNameTransformer
import java.util.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class AnalyticdbForMysqlNameTransformer : StandardNameTransformer() {
    override fun getIdentifier(name: String): String {
        var identifier = applyDefaultCase(super.getIdentifier(name))
        LOGGER.info("identifier: {}", identifier)
        identifier = MySQLNameTransformer.truncateName(identifier, MAX_LENGTH)
        LOGGER.info("identifier: {}", identifier)
        return identifier
    }

    override fun getTmpTableName(name: String): String {
        var tmpTableName = applyDefaultCase(super.getTmpTableName(name))
        LOGGER.info("tmpTableName: {}", tmpTableName)
        tmpTableName = MySQLNameTransformer.truncateName(tmpTableName, MAX_LENGTH)
        LOGGER.info("tmpTableName: {}", tmpTableName)
        return tmpTableName
    }

    override fun getRawTableName(name: String): String {
        var rawTableName = applyDefaultCase(super.getRawTableName(name))
        LOGGER.info("rawTableName: {}", rawTableName)
        rawTableName = MySQLNameTransformer.truncateName(rawTableName, MAX_LENGTH)
        LOGGER.info("rawTableName: {}", rawTableName)
        return rawTableName
    }

    override fun applyDefaultCase(input: String): String = input.lowercase(Locale.getDefault())

    companion object {
        private val LOGGER: Logger =
            LoggerFactory.getLogger(AnalyticdbForMysqlNameTransformer::class.java)

        const val MAX_LENGTH: Int = 127
    }
}
