#  Copyright (C) 2024 The Android Open Source Project
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

# Lint as: python3
"""Constants for Wifi-Aware Mobly test"""

import enum
import dataclasses
import datetime

# Package name for the Wi-Fi Aware snippet application
WIFI_AWARE_SNIPPET_PACKAGE_NAME = "com.google.snippet.wifi.aware"
# Timeout duration for Wi-Fi state change operations
WAIT_WIFI_STATE_TIME_OUT = datetime.timedelta(seconds=30)

SERVICE_NAME = "service_name"
SERVICE_SPECIFIC_INFO = "service_specific_info"
MATCH_FILTER = "match_filter"
SUBSCRIBE_TYPE = "subscribe_type"
TERMINATE_NOTIFICATION_ENABLED = "terminate_notification_enabled"
MAX_DISTANCE_MM = "max_distance_mm"
PAIRING_CONFIG = "pairing_config"
AWARE_NETWORK_INFO_CLASS_NAME = "android.net.wifi.aware.WifiAwareNetworkInfo"


@enum.unique
class WifiAwareSnippetEventName(enum.StrEnum):
    """Represents event names for Wi-Fi Aware snippet operations."""

    GET_PAIRED_DEVICE = "getPairedDevices"
    ON_AVAILABLE = "onAvailable"
    ON_LOST = "onLost"


@enum.unique
class WifiAwareSnippetParams(enum.StrEnum):
    """Represents parameters for Wi-Fi Aware snippet events."""

    ALIAS_LIST = "getPairedDevices"


@enum.unique
class DiscoverySessionCallbackMethodType(enum.StrEnum):
    """Represents the types of callback methods for Wi-Fi Aware discovery sessions.

    These callbacks are correspond to DiscoverySessionCallback in the Android documentation:
    https://developer.android.com/reference/android/net/wifi/aware/DiscoverySessionCallback
    """

    PUBLISH_STARTED = "onPublishStarted"
    SUBSCRIBE_STARTED = "onSubscribeStarted"
    SESSION_CONFIG_UPDATED = "onSessionConfigUpdated"
    SESSION_CONFIG_FAILED = "onSessionConfigFailed"
    SESSION_TERMINATED = "onSessionTerminated"
    SERVICE_DISCOVERED = "onServiceDiscovered"
    SERVICE_DISCOVERED_WITHIN_RANGE = "onServiceDiscoveredWithinRange"
    MESSAGE_SEND_SUCCEEDED = "onMessageSendSucceeded"
    MESSAGE_SEND_FAILED = "onMessageSendFailed"
    MESSAGE_RECEIVED = "onMessageReceived"
    PAIRING_REQUEST_RECEIVED = "onPairingSetupRequestReceived"
    PAIRING_SETUP_SUCCEEDED = "onPairingSetupSucceeded"
    PAIRING_SETUP_FAILED = "onPairingSetupFailed"
    PAIRING_VERIFICATION_SUCCEEDED = "onPairingVerificationSucceed"
    PAIRING_VERIFICATION_FAILED = "onPairingVerificationFailed"
    BOOTSTRAPPING_SUCCEEDED = "onBootstrappingSucceeded"
    BOOTSTRAPPING_FAILED = "onBootstrappingFailed"
    # Event for the publish or subscribe step: triggered by onPublishStarted or SUBSCRIBE_STARTED or
    # onSessionConfigFailed
    DISCOVER_RESULT = "discoveryResult"
    # Event for the message send result.
    MESSAGE_SEND_RESULT = "messageSendResult"


@enum.unique
class DiscoverySessionCallbackParamsType(enum.StrEnum):
    CALLBACK_NAME = "callbackName"
    IS_SESSION_INIT = "isSessionInitialized"
    MESSAGE_ID = "messageId"
    RECEIVE_MESSAGE = "receivedMessage"


@enum.unique
class NetworkCbEventName(enum.StrEnum):
    """Represents the event name for ConnectivityManager network callbacks."""
    NETWORK_CALLBACK = "NetworkCallback"


@enum.unique
class NetworkCbEventKey(enum.StrEnum):
    """Represents event data keys for ConnectivityManager network callbacks."""
    NETWORK = "network"
    CALLBACK_NAME = "callbackName"
    NETWORK_CAPABILITIES = "networkCapabilities"
    TRANSPORT_INFO_CLASS_NAME = "transportInfoClassName"


@enum.unique
class NetworkCbName(enum.StrEnum):
    """Represents the name of network callback for ConnectivityManager.

    These callbacks are correspond to DiscoverySessionCallback in the Android documentation:
    https://developer.android.com/reference/android/net/ConnectivityManager.NetworkCallback
    """
    ON_UNAVAILABLE = "onUnavailable"
    ON_CAPABILITIES_CHANGED = "onCapabilitiesChanged"


@enum.unique
class WifiAwareSnippetParams(enum.StrEnum):
    """Represents parameters for Wi-Fi Aware snippet events."""
    SERVICE_SPECIFIC_INFO = "serviceSpecificInfo"
    RECEIVED_MESSAGE = "receivedMessage"
    PEER_HANDLE = "peerHandle"
    MATCH_FILTER = "matchFilter"
    MATCH_FILTER_VALUE = "value"
    PAIRED_ALIAS = "pairedAlias"
    PAIRING_CONFIG = "pairingConfig"
    DISTANCE_MM = "distanceMm"
    LAST_MESSAGE_ID = "lastMessageId"
    PAIRING_REQUEST_ID = "pairingRequestId"
    BOOTSTRAPPING_METHOD = "bootstrappingMethod"


@enum.unique
class SubscribeType(enum.IntEnum):
    """Represents the types of subscriptions in Wi-Fi Aware.

    These callbacks are correspond to SubscribeConfig in the Android documentation:
    https://developer.android.com/reference/android/net/wifi/aware/SubscribeConfig#constants_1
    """

    PASSIVE = 0
    ACTIVE = 1


@enum.unique
class PublishType(enum.IntEnum):
    """Represents the types of publications in Wi-Fi Aware.

    These publication types are correspond to PublishConfig in the Android documentation:
    https://developer.android.com/reference/android/net/wifi/aware/PublishConfig#constants_1
    """

    UNSOLICITED = 0
    SOLICITED = 1


class BootstrappingMethod(enum.IntEnum):
    """Represents bootstrapping methods for Wi-Fi Aware pairing.

    These types are correspond to AwarePairingConfig bootstrapping methods in the Android
    documentation:
    https://developer.android.com/reference/android/net/wifi/aware/AwarePairingConfig#summary
    """
    OPPORTUNISTIC = 1
    PIN_CODE_DISPLAY = 2
    PASSPHRASE_DISPLAY = 4
    QR_DISPLAY = 8
    NFC_TAG = 16
    PIN_CODE_KEYPAD = 32
    PASSPHRASE_KEYPAD = 64
    QR_SCAN = 128
    NFC_READER = 256


@dataclasses.dataclass(frozen=True)
class AwarePairingConfig:
    """Config for Wi-Fi Aware Pairing.

    These configurations correspond to AwarePairingConfig in the Android documentation:
    https://developer.android.com/reference/android/net/wifi/aware/AwarePairingConfig?hl=en
    """
    pairing_cache_enabled: bool = False
    pairing_setup_enabled: bool = False
    pairing_verification_enabled: bool = False
    bootstrapping_methods: BootstrappingMethod = BootstrappingMethod.OPPORTUNISTIC

    def to_dict(self) -> dict[str, int | bool]:
        result = dataclasses.asdict(self)
        result["bootstrapping_methods"] = self.bootstrapping_methods.value
        return result


@dataclasses.dataclass(frozen=True)
class SubscribeConfig:
    """Config for Wi-Fi Aware Subscribe.

    These configurations correspond to SubscribeConfig in the Android documentation:
    https://developer.android.com/reference/android/net/wifi/aware/SubscribeConfig
    """
    service_name: str
    service_specific_info: bytes
    match_filter: list[bytes] | None
    subscribe_type: SubscribeType
    terminate_notification_enabled: bool = False
    max_distance_mm: int | None = None
    pairing_config: AwarePairingConfig | None = None

    def to_dict(self) -> dict[str, str | bool | list[str] | int | dict[str, int | bool | None]]:
        result = dataclasses.asdict(self)
        result["subscribe_type"] = self.subscribe_type.value
        result["service_specific_info"] = self.service_specific_info.decode("utf-8")

        if self.match_filter is None:
            del result["match_filter"]
        else:
            result["match_filter"] = [mf.decode("utf-8") for mf in self.match_filter]

        if self.pairing_config is None:
            del result["pairing_config"]
        else:
            result["pairing_config"] = self.pairing_config.to_dict()

        if self.max_distance_mm is None:
            del result["max_distance_mm"]

        return result


@dataclasses.dataclass(frozen=True)
class PublishConfig:
    """Wi-Fi Aware Publish Config.

    These configurations correspond to PublishConfig in the Android documentation:
    https://developer.android.com/reference/android/net/wifi/aware/PublishConfig
    """
    service_name: str
    service_specific_info: bytes
    match_filter: list[bytes] | None
    publish_type: PublishType
    terminate_notification_enabled: bool
    ranging_enabled: bool
    pairing_config: AwarePairingConfig | None = None

    def to_dict(
        self,
    ) -> dict[str, str | bool | list[str] | int | dict[str, int | bool]]:
        """Convert PublishConfig to dict."""
        result = dataclasses.asdict(self)
        result["publish_type"] = self.publish_type.value
        result["service_specific_info"] = self.service_specific_info.decode("utf-8")

        if self.match_filter is None:
            del result["match_filter"]
        else:
            result["match_filter"] = [mf.decode("utf-8") for mf in self.match_filter]

        if self.pairing_config is None:
            del result["pairing_config"]
        else:
            result["pairing_config"] = self.pairing_config.to_dict()
        return result


class WifiAwareTestConstants:
    """Constants for Wi-Fi Aware test."""
    SERVICE_NAME = "CtsVerifierTestService"
    MATCH_FILTER_BYTES = "bytes used for matching".encode("utf-8")
    PUB_SSI = "Extra bytes in the publisher discovery".encode("utf-8")
    SUB_SSI = "Arbitrary bytes for the subscribe discovery".encode("utf-8")
    LARGE_ENOUGH_DISTANCE_MM = 100000
    PASSWORD = "Some super secret password"
    ALIAS_PUBLISH = "publisher"
    ALIAS_SUBSCRIBE = "subscriber"
    TEST_WAIT_DURATION_MS = 10000
    TEST_MESSAGE = "test message!"
    MESSAGE_ID = 1234


class NetworkCapabilities:
    """Network Capabilities.

    https://developer.android.com/reference/android/net/NetworkCapabilities?hl=en#summary
    """

    class Transport(enum.IntEnum):
        """Transport type.

        https://developer.android.com/reference/android/net/NetworkCapabilities#TRANSPORT_CELLULAR
        """
        TRANSPORT_CELLULAR = 0
        TRANSPORT_WIFI = 1
        TRANSPORT_BLUETOOTH = 2
        TRANSPORT_ETHERNET = 3
        TRANSPORT_VPN = 4
        TRANSPORT_WIFI_AWARE = 5
        TRANSPORT_LOWPAN = 6

    class NetCapability(enum.IntEnum):
        """Network Capability.

        https://developer.android.com/reference/android/net/NetworkCapabilities#NET_CAPABILITY_MMS
        """
        NET_CAPABILITY_MMS = 0
        NET_CAPABILITY_SUPL = 1
        NET_CAPABILITY_DUN = 2
        NET_CAPABILITY_FOTA = 3
        NET_CAPABILITY_IMS = 4
        NET_CAPABILITY_CBS = 5
        NET_CAPABILITY_WIFI_P2P = 6
        NET_CAPABILITY_IA = 7
        NET_CAPABILITY_RCS = 8
        NET_CAPABILITY_XCAP = 9
        NET_CAPABILITY_EIMS = 10
        NET_CAPABILITY_NOT_METERED = 11
        NET_CAPABILITY_INTERNET = 12
        NET_CAPABILITY_NOT_RESTRICTED = 13
        NET_CAPABILITY_TRUSTED = 14
        NET_CAPABILITY_NOT_VPN = 15
        NET_CAPABILITY_VALIDATED = 16
        NET_CAPABILITY_CAPTIVE_PORTAL = 17
        NET_CAPABILITY_NOT_ROAMING = 18
        NET_CAPABILITY_FOREGROUND = 19
        NET_CAPABILITY_NOT_CONGESTED = 20
        NET_CAPABILITY_NOT_SUSPENDED = 21
        NET_CAPABILITY_OEM_PAID = 22
        NET_CAPABILITY_MCX = 23
        NET_CAPABILITY_PARTIAL_CONNECTIVITY = 24
        NET_CAPABILITY_TEMPORARILY_NOT_METERED = 25
        NET_CAPABILITY_OEM_PRIVATE = 26
        NET_CAPABILITY_VEHICLE_INTERNAL = 27
        NET_CAPABILITY_NOT_VCN_MANAGED = 28
        NET_CAPABILITY_ENTERPRISE = 29
        NET_CAPABILITY_VSIM = 30
        NET_CAPABILITY_BIP = 31
        NET_CAPABILITY_HEAD_UNIT = 32
        NET_CAPABILITY_MMTEL = 33
        NET_CAPABILITY_PRIORITIZE_LATENCY = 34
        NET_CAPABILITY_PRIORITIZE_BANDWIDTH = 35


@dataclasses.dataclass(frozen=True)
class NetworkRequest:
    """Wi-Fi Aware Network Request.

    https://developer.android.com/reference/android/net/NetworkRequest
    """
    transport_type: NetworkCapabilities.Transport
    network_specifier_parcel: str

    def to_dict(self) -> dict[str, int | str | dict[str, str | int]]:
        result = {
            'transport_type': self.transport_type.value,
            'network_specifier': self.network_specifier_parcel,
        }
        return result
