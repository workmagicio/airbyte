import typing
from airbyte_cdk.sources.streams.availability_strategy import AvailabilityStrategy
from airbyte_cdk.sources.streams import Stream
import logging
from typing import Optional, Tuple

if typing.TYPE_CHECKING:
    from airbyte_cdk.sources import Source


class NoneAvailabilityStrategy(AvailabilityStrategy):
    def check_availability(self, stream: Stream, logger: logging.Logger, source: Optional["Source"]) -> Tuple[bool, Optional[str]]:
        return True, ""
