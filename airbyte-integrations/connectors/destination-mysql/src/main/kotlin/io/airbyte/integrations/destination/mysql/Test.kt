package io.airbyte.integrations.destination.mysql

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.airbyte.cdk.integrations.base.Destination
import io.airbyte.cdk.integrations.base.DestinationConfig
import io.airbyte.commons.json.Jsons
import io.airbyte.protocol.models.v0.AirbyteMessage
import io.airbyte.protocol.models.v0.ConfiguredAirbyteCatalog
import java.nio.charset.StandardCharsets
import java.util.function.Consumer


fun main() {
    val objectMapper = ObjectMapper()
    val configJson =
        """{"ssl":false,"host":"localhost","port":3306,"database":"airbyte_test","password":"****","username":"root","wm_tenant_id":"6","tunnel_method":{"tunnel_method":"NO_TUNNEL"},"raw_data_schema":"airbyte_test","disable_type_dedupe":true}"""
    val config: JsonNode = objectMapper.readTree(configJson)
    val catalogJson =
        """{"streams":[{"stream":{"name":"campaigns","json_schema":{"type":"object","$""" + """schema":"http://json-schema.org/draft-07/schema#","properties":{"id":{"type":"string"},"type":{"type":"string"},"links":{"type":["null","object"],"properties":{"self":{"type":"string"}},"additionalProperties":true},"attributes":{"type":["null","object"],"properties":{"name":{"type":"string"},"status":{"type":"string"},"channel":{"type":"string"},"message":{"type":"string"},"archived":{"type":"boolean"},"audiences":{"type":["null","object"],"properties":{"excluded":{"type":["null","array"],"items":{"type":["null","string"]}},"included":{"type":["null","array"],"items":{"type":["null","string"]}}},"additionalProperties":true},"send_time":{"type":["null","string"],"format":"date-time"},"created_at":{"type":["null","string"],"format":"date-time"},"updated_at":{"type":["null","string"],"format":"date-time"},"scheduled_at":{"type":["null","string"],"format":"date-time"},"send_options":{"type":["null","object"],"properties":{"use_smart_sending":{"type":["null","boolean"]},"ignore_unsubscribes":{"type":["null","boolean"]}}},"send_strategy":{"type":["null","object"],"properties":{"method":{"type":"string"},"options_sto":{"type":["null","object"],"properties":{"date":{"type":"string","format":"date"}}},"options_static":{"type":["null","object"],"properties":{"datetime":{"type":"string","format":"date-time","airbyte_type":"timestamp_without_timezone"},"is_local":{"type":["null","boolean"]},"send_past_recipients_immediately":{"type":["null","boolean"]}}},"options_throttled":{"type":["null","object"],"properties":{"datetime":{"type":"string","format":"date-time","airbyte_type":"timestamp_without_timezone"},"throttle_percentage":{"type":"integer"}}}},"additionalProperties":true},"tracking_options":{"type":["null","object"],"properties":{"is_add_utm":{"type":["null","boolean"]},"utm_params":{"type":["null","array"],"items":{"type":["null","object"],"properties":{"name":{"type":"string"},"value":{"type":"string"}}}},"is_tracking_opens":{"type":["null","boolean"]},"is_tracking_clicks":{"type":["null","boolean"]}},"additionalProperties":true}},"additionalProperties":true},"updated_at":{"type":["null","string"],"format":"date-time"},"relationships":{"type":["null","object"],"properties":{"tags":{"type":["null","object"],"properties":{"data":{"type":"array","items":{"type":["null","object"],"properties":{"id":{"type":"string"},"type":{"type":"string"}}}},"links":{"type":["null","object"],"properties":{"self":{"type":"string"},"related":{"type":"string"}}}}}},"additionalProperties":true}},"additionalProperties":true},"supported_sync_modes":["full_refresh","incremental"],"source_defined_cursor":true,"default_cursor_field":["updated_at"],"source_defined_primary_key":[["id"]],"is_resumable":true},"sync_mode":"incremental","cursor_field":["updated_at"],"destination_sync_mode":"append","primary_key":[["id"]]}]}"""
    val catalog: ConfiguredAirbyteCatalog = Jsons.`object`(objectMapper.readTree(catalogJson), ConfiguredAirbyteCatalog::class.java)!!
    val destination: Destination = MySQLDestination()
    DestinationConfig.Companion.initialize(config, true)
    val consumer = destination.getSerializedMessageConsumer(config, catalog, Consumer<AirbyteMessage> { message: AirbyteMessage ->
        LOGGER.info { "Destination.defaultOutputRecordCollector(message), message: ${message}" }
        Destination.defaultOutputRecordCollector(message)
    }
    )
    LOGGER.info { "destination.getSerializedMessageConsumer, consumer: ${consumer}" }
    LOGGER.info { "Starting buffered read of input stream" }
    consumer!!.start()
    val line1 = """{"type":"RECORD","record":{"stream":"campaigns","data":{"type":"campaign","id":"01J70ZFH4BQ31673N4BMK7Z29D","attributes":{"name":"Email Campaign - Sep 5, 2024, 7:35 PM","status":"Draft","archived":false,"channel":"email","audiences":{"included":["XnUuez"],"excluded":[]},"send_options":{"use_smart_sending":true,"ignore_unsubscribes":false},"message":"01J70ZFH4GR2NBKQ7BYWBWBR36","tracking_options":{"is_tracking_opens":true,"is_tracking_clicks":true,"is_add_utm":true,"utm_params":[]},"send_strategy":{"method":"static","options_static":{"datetime":"2024-09-04T16:00:00+00:00","is_local":false,"send_past_recipients_immediately":null},"options_throttled":null,"options_sto":null},"created_at":"2024-09-05T11:35:26.092405+00:00","scheduled_at":null,"updated_at":"2024-09-05T11:35:26.092440+00:00","send_time":null},"relationships":{"tags":{"links":{"self":"https://a.klaviyo.com/api/campaigns/01J70ZFH4BQ31673N4BMK7Z29D/relationships/tags/","related":"https://a.klaviyo.com/api/campaigns/01J70ZFH4BQ31673N4BMK7Z29D/tags/"}}},"links":{"self":"https://a.klaviyo.com/api/campaigns/01J70ZFH4BQ31673N4BMK7Z29D/"},"updated_at":"2024-09-05T11:35:26.092440+00:00"},"emitted_at":1725947805082}}"""
    consumer.accept(line1, line1.toByteArray(StandardCharsets.UTF_8).size)
    val line2 = """{"type":"TRACE","trace":{"type": "STREAM_STATUS", "stream_status":{"status":"COMPLETE","stream_descriptor":{"name":"campaigns"}}},"state":{"type":"STREAM","stream":{"stream_descriptor":{"name":"campaigns"},"stream_state":{"updated_at":"2024-09-05T11:35:26.092440+00:00"}},"sourceStats":{"recordCount":0.0},"id":2}}"""
    consumer.accept(line2, line2.toByteArray(StandardCharsets.UTF_8).size)
    LOGGER.info { "Finished buffered read of input stream" }
    consumer.close()
//    consumer.use { consumer -> {
//        LOGGER.info { "Starting buffered read of input stream" }
//        consumer!!.start()
//        val line = """{"type":"STATE","state":{"type":"STREAM","stream":{"stream_descriptor":{"name":"campaigns"},"stream_state":{"updated_at":"2024-09-05T11:35:26.092440+00:00"}},"sourceStats":{"recordCount":0.0},"id":2}}"""
//        consumer.accept(line, line.toByteArray(StandardCharsets.UTF_8).size)
//        LOGGER.info { "Finished buffered read of input stream" }
//    } }
}
