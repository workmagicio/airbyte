
import pytz
from datetime import datetime
import time
from typing import Tuple
from typing import Any, Iterable, List, Mapping, MutableMapping, Optional, Union
import pendulum

from airbyte_cdk.sources import AbstractSource
from airbyte_cdk.sources.streams.core import Stream

from source_tiktok_shop.auth import TiktokAuthenticator
from source_tiktok_shop.streams import Orders, ReturnOrders

DEFAULT_RETENTION_PERIOD_IN_DAYS = 30


class SourceTiktokShop(AbstractSource):
    def check_connection(self, logger, config) -> Tuple[bool, any]:
        self._get_stream_kwargs(config)
        return True, None

    @staticmethod
    def _get_stream_kwargs(config: Mapping[str, Any]) -> Mapping[str, Any]:
        period_in_days = DEFAULT_RETENTION_PERIOD_IN_DAYS

        period_in_days_config = config.get("period_in_days")
        if period_in_days_config:
            period_in_days = period_in_days_config

        auth = TiktokAuthenticator(
            token_refresh_endpoint="https://auth.tiktok-shops.com/api/v2/token/refresh",
            client_id=config.get("app_key"),
            client_secret=config.get("app_secret"),
            refresh_token=config.get("refresh_token"),
            host="open-api.tiktokglobalshop.com"
        )

        start_date = config.get("start_date")
        if not start_date:
            start_date = '2023-08-10T00:00:00Z'

        start_timestamp = None
        if start_date is not None:
            utc = pytz.utc
            date = datetime.strptime(start_date, '%Y-%m-%dT%H:%M:%SZ')
            date = utc.localize(date)
            start_timestamp = int(date.timestamp())

            # 获取当前时间
            now_utc = pendulum.now("utc")
            now_utc_seconds = int(time.mktime(now_utc.timetuple()))  # 将 pendulum 时间转换为秒级时间戳

            # 如果 start_timestamp 为空，或者时间小于配置的时间
            use_default_start_date = (
                    not start_timestamp or (now_utc_seconds - start_timestamp) < period_in_days * 86400
            )
            if use_default_start_date:
                start_timestamp = int(time.mktime((pendulum.now("utc").subtract(days=period_in_days)).timetuple()))

        end_date = config.get("end_date")
        end_timestamp = None
        if end_date is not None:
            utc = pytz.utc
            date = datetime.strptime(config.get("start_date"), '%Y-%m-%dT%H:%M:%SZ')
            date = utc.localize(date)
            end_timestamp = int(date.timestamp())

        use_default_end_date = not end_timestamp or end_timestamp < start_timestamp
        if use_default_end_date:
            end_timestamp = None  # None to sync all data

        return {
            "url_base": "https://open-api.tiktokglobalshop.com",
            "authenticator": auth,
            "replication_start_time": start_timestamp,
            "shop_cipher": config.get("shop_cipher"),
            "period_in_days": period_in_days,
            "replication_end_time": end_timestamp,
        }

    def streams(self, config: Mapping[str, Any]) -> List[Stream]:
        stream_list = [
            Orders,
            ReturnOrders
        ]

        stream_kwargs = self._get_stream_kwargs(config)
        streams = []
        for stream in stream_list:
            streams.append(stream(**stream_kwargs))

        return streams
