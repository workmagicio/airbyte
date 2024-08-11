from typing import Any, Mapping
import hashlib
import hmac
from airbyte_cdk.sources.streams.http.requests_native_auth import Oauth2Authenticator
from typing import Any, List, Mapping, MutableMapping, Optional, Tuple
import requests
from urllib.parse import urlparse, parse_qs


class TiktokAuthenticator(Oauth2Authenticator):
    def __init__(self, host: str, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.host = host

    def get_auth_header(self) -> Mapping[str, Any]:
        return {
            "host": self.host,
            "user-agent": "python-requests",
            "x-tts-access-token": self.get_access_token(),
            "Content-Type": "application/json"
        }

    def get_refresh_request_body(self) -> Mapping[str, Any]:
        return {
            "grant_type": "refresh_token",
            "app_key": self._client_id,
            "app_secret": self._client_secret,
            "refresh_token": self._refresh_token,
        }

    def refresh_access_token(self) -> Tuple[str, int]:
        try:
            response = requests.get(
                url=self._token_refresh_endpoint,
                params=self.get_refresh_request_body(),
            )
            response.raise_for_status()
            response_json = response.json()
            return response_json["data"]["access_token"], int(response_json["data"]["access_token_expire_in"])
        except requests.exceptions.RequestException as e:
            if e.response.status_code == 429 or e.response.status_code >= 500:
                raise Exception(f"Error Refresh token failure, {e}") from e
            raise
        except Exception as e:
            raise Exception(f"Error while refreshing access token: {e}") from e


def cal_sign(path, queries, req_body, secret):
    keys = [k for k in queries.keys() if k not in ["sign", "access_token"]]
    keys.sort()

    input_string = ""
    for key in keys:
        input_string += key + ''.join(queries[key])

    input_string = path + input_string

    if req_body:
        input_string += req_body

    input_string = secret + input_string + secret

    return generate_sha256(input_string, secret)


def generate_sha256(input_string, secret):
    h = hmac.new(secret.encode(), input_string.encode(), hashlib.sha256)
    return h.hexdigest()
