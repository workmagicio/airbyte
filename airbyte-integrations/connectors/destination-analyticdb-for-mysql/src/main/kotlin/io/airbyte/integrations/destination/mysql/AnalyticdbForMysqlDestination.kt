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
    MySQLDestination(AnalyticdbForMysqlSqlOperations()),
    Destination {

    override val namingResolver: NamingConventionTransformer
        get() = AnalyticdbForMysqlNameTransformer()

    // override fun spec(): ConnectorSpecification {
    //     val spec: ConnectorSpecification = Jsons.clone(super.spec())
    //     val prop = spec.connectionSpecification["properties"] as ObjectNode
    //     prop
    //         .putObject(AnalyticdbForMysqlDestination.TENANT_ID_KEY)
    //         .put("title", "Tenant Id")
    //         .put("description", "Tenant Id")
    //         .put("type", "string")
    //         .put("order", prop.size())
    //     return modifySpec(super.spec())
    // }

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
            LOGGER.info("starting destination: {}", AnalyticdbForMysqlDestination::class.java)
            try {
                IntegrationRunner(destination).run(args)
            } catch (e: Exception) {
                handleException(e)
            }
            LOGGER.info("complete destination: {}", AnalyticdbForMysqlDestination::class.java)
        }
    }
}
