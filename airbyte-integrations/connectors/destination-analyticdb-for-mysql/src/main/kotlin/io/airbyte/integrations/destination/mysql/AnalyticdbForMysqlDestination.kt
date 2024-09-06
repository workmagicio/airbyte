/*
 * Copyright (c) 2023 Airbyte, Inc., all rights reserved.
 */
package io.airbyte.integrations.destination.mysql

import com.fasterxml.jackson.databind.JsonNode
import io.airbyte.cdk.integrations.base.Destination
import io.airbyte.cdk.integrations.base.IntegrationRunner
import io.airbyte.cdk.integrations.destination.NamingConventionTransformer
import io.airbyte.protocol.models.v0.AirbyteConnectionStatus
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class AnalyticdbForMysqlDestination :
    MySQLDestination(AnalyticdbForMysqlNameTransformer(), AnalyticdbForMysqlSqlOperations()),
    Destination {

    override val namingResolver: NamingConventionTransformer
        get() = AnalyticdbForMysqlNameTransformer()

    override fun check(config: JsonNode): AirbyteConnectionStatus {
        LOGGER.info("config: {}", config)
        return AirbyteConnectionStatus().withStatus(AirbyteConnectionStatus.Status.SUCCEEDED)
    }

    companion object {
        private val LOGGER: Logger =
            LoggerFactory.getLogger(AnalyticdbForMysqlDestination::class.java)

        @JvmStatic
        @Throws(Exception::class)
        fun main(args: Array<String>) {
            val destination = AnalyticdbForMysqlDestination()
            LOGGER.info("build 2024-09-06 11:00:00")
            LOGGER.info("starting destination: {}", AnalyticdbForMysqlDestination::class.java)
            try {
                IntegrationRunner(destination).run(args)
            } catch (e: Exception) {
                handleException(e)
            }
            LOGGER.info("finished destination: {}", AnalyticdbForMysqlDestination::class.java)
        }
    }
}
