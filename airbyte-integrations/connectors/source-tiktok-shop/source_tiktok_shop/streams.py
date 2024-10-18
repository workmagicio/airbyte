from abc import ABC
from typing import Tuple

import requests
import pytz

import pendulum
import json
import time
from airbyte_cdk.models import SyncMode
from airbyte_cdk.sources import AbstractSource
from airbyte_cdk.sources.streams.core import Stream
from airbyte_cdk.sources.streams.http import HttpStream
from airbyte_cdk.sources.streams.availability_strategy import AvailabilityStrategy
from airbyte_cdk.sources.streams import IncrementalMixin
from airbyte_cdk.sources.streams.core import StreamData
from source_tiktok_shop.auth import TiktokAuthenticator, cal_sign
from source_tiktok_shop.availability_strategy import NoneAvailabilityStrategy
from datetime import datetime
from typing import Any, Iterable, List, Mapping, MutableMapping, Optional, Union


class TiktokShopStream(HttpStream, IncrementalMixin, ABC):
    authenticator = None
    page_size = 100

    def __init__(
            self,
            url_base: str,
            replication_start_time: int,
            shop_cipher: str,
            authenticator: TiktokAuthenticator,
            period_in_days: int,
            replication_end_time: Optional[int],
            *args,
            **kwargs,
    ):
        super().__init__(*args, **kwargs)

        self.authenticator = authenticator
        self._url_base = url_base.rstrip("/") + "/"
        self.replication_start_time = replication_start_time
        self._replication_end_time = replication_end_time
        self._shop_cipher = shop_cipher
        self._period_in_days = period_in_days

    @property
    def url_base(self) -> str:
        return self._url_base

    def request_headers(self, *args, **kwargs) -> Mapping[str, Any]:
        return self.authenticator.get_auth_header()


class Orders(TiktokShopStream):
    use_cache = True
    _state = {}

    def __init__(
            self,
            url_base: str,
            replication_start_time: int,
            shop_cipher: str,
            authenticator: TiktokAuthenticator,
            period_in_days: int,
            *args,
            **kwargs,
    ):
        super().__init__(url_base, replication_start_time, shop_cipher, authenticator, period_in_days, *args, **kwargs)

    @property
    def primary_key(self) -> Union[str, List[str], None]:
        return ["id"]  # 使用 'user_id' 和 'order_id' 共同作为主键

    def path(self, **kwargs) -> str:
        return "/order/202309/orders/search"

    @property
    def cursor_field(self) -> str:
        return "update_time"

    @property
    def http_method(self) -> str:
        return "POST"

    @property
    def state(self):
        return self._state

    @state.setter
    def state(self, value):
        if value:
            self._state[self.cursor_field] = value[self.cursor_field]

    def request_params(
            self, stream_state: Mapping[str, Any], next_page_token: Mapping[str, Any] = None, **kwargs
    ) -> MutableMapping[str, Any]:
        self._update_start_time(stream_state)
        timestamp = str(int(time.time()))
        params = {
            "app_key": self.authenticator._client_id,
            "shop_cipher": self._shop_cipher,
            "timestamp": timestamp,
            "page_size": str(self.page_size),
            "sort_field": "update_time"
        }
        if next_page_token:
            params["page_token"] = next_page_token["page_token"]

        sign = cal_sign(self.path(), params, self._get_request_body(), self.authenticator._client_secret)

        params["sign"] = sign

        print("debug_request_params:", params)

        return params

    def _update_start_time(self, stream_state: Mapping[str, Any]):
        if self.cursor_field and stream_state:
            start_date = max(stream_state.get(self.cursor_field, self.replication_start_time), self.replication_start_time)
            start_date = min(start_date, int(time.time()))
            self.replication_start_time = start_date

    def next_page_token(self, response: requests.Response) -> Optional[Mapping[str, Any]]:
        stream_data = response.json()
        next_page_token = stream_data.get("data").get("next_page_token")
        if next_page_token and next_page_token != '':
            return {'page_token': next_page_token}

    def parse_response(
            self,
            response: requests.Response,
            stream_state: Mapping[str, Any] = None,
            stream_slice: Mapping[str, Any] = None,
            **kwargs: Any,
    ) -> Iterable[Mapping]:
        yield from response.json().get('data', {}).get('orders', [])

    def _get_request_body(self) -> str:
        request_body = {}

        if self.replication_start_time is not None:
            request_body["update_time_ge"] = self.replication_start_time
        if self._replication_end_time is not None:
            request_body["update_time_lt"] = self._replication_end_time

        return json.dumps(request_body)

    def request_body_data(
            self,
            stream_state: Optional[Mapping[str, Any]],
            stream_slice: Optional[Mapping[str, Any]] = None,
            next_page_token: Optional[Mapping[str, Any]] = None,
    ) -> Optional[Union[Mapping[str, Any], str]]:
        self._update_start_time(stream_state)
        return self._get_request_body()

    def get_updated_state(self, current_stream_state: dict, latest_record: dict):
        current_stream_state = current_stream_state or {}
        latest_cursor_value = latest_record.get(self.cursor_field)
        current_cursor_value = current_stream_state.get(self.cursor_field)

        if latest_cursor_value and current_cursor_value:
            return {self.cursor_field: max(latest_cursor_value, current_cursor_value)}
        else:
            return {self.cursor_field: latest_cursor_value or current_cursor_value}

    @property
    def availability_strategy(self) -> Optional[AvailabilityStrategy]:
        return NoneAvailabilityStrategy()

    def read_records(
        self,
        sync_mode: SyncMode,
        cursor_field: Optional[List[str]] = None,
        stream_slice: Optional[Mapping[str, Any]] = None,
        stream_state: Optional[Mapping[str, Any]] = None,
    ) -> Iterable[StreamData]:
        for record in super().read_records(sync_mode, cursor_field, stream_slice, stream_state):
            self.state = {self.cursor_field: record.get(self.cursor_field)}
            yield record


class ReturnOrders(Orders):
    use_cache = True
    _state = {}
    page_size = 50

    @property
    def primary_key(self) -> Union[str, List[str], None]:
        return ["return_id"]  # 使用 'user_id' 和 'order_id' 共同作为主键

    def path(self, **kwargs) -> str:
        return "/return_refund/202309/returns/search"


    def parse_response(
            self,
            response: requests.Response,
            stream_state: Mapping[str, Any] = None,
            stream_slice: Mapping[str, Any] = None,
            **kwargs: Any,
    ) -> Iterable[Mapping]:
        yield from response.json().get('data', {}).get('return_orders', [])
