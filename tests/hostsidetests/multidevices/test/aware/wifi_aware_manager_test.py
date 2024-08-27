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
"""CTS-V Wi-Fi Aware test reimplemented in Mobly."""

import enum
import sys

from mobly import asserts
from mobly import base_test
from mobly import records
from mobly import test_runner
from mobly import utils
from mobly.controllers import android_device
from mobly.controllers.android_device_lib import callback_handler_v2
from mobly.snippet import callback_event

from aware import constants

RUNTIME_PERMISSIONS = (
    'android.permission.ACCESS_FINE_LOCATION',
    'android.permission.ACCESS_COARSE_LOCATION',
    'android.permission.NEARBY_WIFI_DEVICES'
)
PACKAGE_NAME = constants.WIFI_AWARE_SNIPPET_PACKAGE_NAME
DEFAULT_TIMEOUT = constants.WAIT_WIFI_STATE_TIME_OUT.total_seconds()


@enum.unique
class AttachCallBackMethodType(enum.StrEnum):
    """Represents Attach Callback Method Type in Wi-Fi Aware.

    https://developer.android.com/reference/android/net/wifi/aware/AttachCallback
    """

    ATTACHED = "onAttached"
    ATTACH_FAILED = "onAttachFailed"
    AWARE_SESSION_TERMINATED = "onAwareSessionTerminated"


class WifiAwareManagerTest(base_test.BaseTestClass):
    """Wi-Fi Aware test class."""

    ads: list[android_device.AndroidDevice]
    publisher: android_device.AndroidDevice
    subscriber: android_device.AndroidDevice

    def setup_class(self):
        # Register two Android devices.
        self.ads = self.register_controller(android_device, min_number=2)
        self.publisher = self.ads[0]
        self.subscriber = self.ads[1]

        def setup_device(device: android_device.AndroidDevice):
            device.load_snippet(
                'wifi_aware_snippet', PACKAGE_NAME
            )
            for permission in RUNTIME_PERMISSIONS:
                device.adb.shell(['pm', 'grant', PACKAGE_NAME, permission])
            asserts.abort_all_if(
                not device.wifi_aware_snippet.wifiAwareIsAvailable(),
                f'{device} Wi-Fi Aware is not available.',
            )

        # Set up devices in parallel.
        utils.concurrent_exec(
            setup_device,
            ((self.publisher,), (self.subscriber,)),
            max_workers=2,
            raise_on_exception=True,
        )

    def start_attach(self, ad: android_device.AndroidDevice) -> None:
        """Starts the attach process on the provided device."""
        handler = ad.wifi_aware_snippet.wifiAwareAttach()
        handler.waitAndGet(
            event_name=AttachCallBackMethodType.ATTACHED,
            timeout=DEFAULT_TIMEOUT,
        )
        asserts.assert_true(
            ad.wifi_aware_snippet.wifiAwareIsSessionAttached(),
            f'{ad} attach succeeded, but Wi-Fi Aware session is still null.'
        )

    def test_discovery(self) -> None:
        utils.concurrent_exec(
            self.start_attach,
            ((self.publisher,), (self.subscriber,)),
            max_workers=2,
            raise_on_exception=True,
        )

        is_unsolicited = True
        is_ranging_required = False
        is_pairing_required = False
        publish_handler = self._start_publish(
            is_unsolicited, is_ranging_required,
            is_pairing_required
        )
        subscribe_handler = self._start_subscribe(
            is_unsolicited, is_ranging_required,
            is_pairing_required
        )
        # Wait for discovery.
        self._wait_for_discovery(subscribe_handler, is_ranging_required)

        #  Check if the subscriber received the message.
        self.subscriber.wifi_aware_snippet.wifiAwareSendMessage(
            constants.WifiAwareTestConstants.MESSAGE_ID,
            constants.WifiAwareTestConstants.TEST_MESSAGE,
        )
        discover_data =publish_handler.waitAndGet(
            event_name=constants.DiscoverySessionCallbackMethodType.MESSAGE_RECEIVED,
            timeout=DEFAULT_TIMEOUT,
        )
        message = discover_data.data[
            constants.WifiAwareSnippetParams.RECEIVED_MESSAGE
        ]
        str_message = bytes(message).decode('utf-8')
        asserts.assert_equal(
            str_message,
            constants.WifiAwareTestConstants.TEST_MESSAGE,
            'Message received by publisher does not match the message sent by subscriber.'
        )


    def _wait_for_discovery(
        self,
        subscribe_handler: callback_handler_v2.CallbackHandlerV2,
        is_ranging_required: bool,
    ) -> None:
        event_name = constants.DiscoverySessionCallbackMethodType.SERVICE_DISCOVERED
        if is_ranging_required:
            event_name = (
                constants.DiscoverySessionCallbackMethodType.SERVICE_DISCOVERED_WITHIN_RANGE
            )
        discover_data = subscribe_handler.waitAndGet(event_name=event_name, timeout=DEFAULT_TIMEOUT)
        self.subscriber.log.debug('Discovery data: %s', discover_data.data)

        service_info = bytes(
            discover_data.data[constants.WifiAwareSnippetParams.SERVICE_SPECIFIC_INFO]
        )
        str_expected_service_info = bytes(
            constants.WifiAwareTestConstants.PUB_SSI
        )
        asserts.assert_equal(
            service_info,
            str_expected_service_info,
            f'Got unexpected service info in discovery callback event "{event_name}".'
        )
        match_filters = discover_data.data[constants.WifiAwareSnippetParams.MATCH_FILTER]
        match_filters = [bytes(filter[constants.WifiAwareSnippetParams.MATCH_FILTER_VALUE]) for filter in match_filters]
        asserts.assert_equal(
            match_filters,
            [constants.WifiAwareTestConstants.MATCH_FILTER_BYTES],
            f'Got unexpected match filter data in discovery callback event "{event_name}".'
        )
        self.subscriber.log.info('Discover success.')

    def _start_subscribe(
        self, is_unsolicited: bool, is_ranging_required: bool, is_pairing_required: bool
    ) -> callback_event.CallbackEvent:
        """Starts a subscribing session with the given configuration."""
        # Determine the type of subscription (active or passive)
        if is_unsolicited:
            subscribe_type = constants.SubscribeType.PASSIVE
        else:
            subscribe_type = constants.SubscribeType.ACTIVE

        # Set maximum distance for ranging if required
        if is_ranging_required:
            max_distance_mm = constants.WifiAwareTestConstants.LARGE_ENOUGH_DISTANCE_MM
        else:
            max_distance_mm = None

        # Configure pairing settings if required.
        if is_pairing_required:
            pairing_config = constants.AwarePairingConfig(
                pairing_cache_enabled=True,
                pairing_setup_enabled=True,
                pairing_verification_enabled=True,
                bootstrapping_methods=constants.BootstrappingMethod.OPPORTUNISTIC,
            )
        else:
            pairing_config = None

        # Create subscription configuration.
        subscribe_config = constants.SubscribeConfig(
            service_name=constants.WifiAwareTestConstants.SERVICE_NAME,
            service_specific_info=constants.WifiAwareTestConstants.SUB_SSI,
            match_filter=[constants.WifiAwareTestConstants.MATCH_FILTER_BYTES],
            subscribe_type=subscribe_type,
            terminate_notification_enabled=True,
            max_distance_mm=max_distance_mm,
            pairing_config=pairing_config,
        )

        # Start the subscription session and return the handler.
        subscribe_handler = self.subscriber.wifi_aware_snippet.wifiAwareSubscribe(
            subscribe_config.to_dict()
        )

        # Wait for subscribe session to start.
        subscribe_handler_data = subscribe_handler.waitAndGet(
            event_name=constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT,
            timeout=DEFAULT_TIMEOUT
        )
        callback_name = subscribe_handler_data.data[
            constants.DiscoverySessionCallbackParamsType.CALLBACK_NAME]
        asserts.assert_equal(
            constants.DiscoverySessionCallbackMethodType.SUBSCRIBE_STARTED,
            callback_name,
            f'{self.subscriber} subscribe failed, got event: {callback_name}.'
        )
        is_session_init = subscribe_handler_data.data[
            constants.DiscoverySessionCallbackParamsType.IS_SESSION_INIT]
        asserts.assert_true(
            is_session_init,
            f'{self.subscriber} subscriber succeeded, but null session returned.'
        )
        self.subscriber.log.info('Subscribe succeeded.')
        return subscribe_handler

    def _start_publish(
        self, is_unsolicited: bool, is_ranging_required: bool, is_pairing_required: bool
    ) -> callback_event.CallbackEvent:
        """Starts a publish session with the given configuration."""
        # Determine the type of publishing (solicited or unsolicited)
        if is_unsolicited:
            publish_type = constants.PublishType.UNSOLICITED
        else:
            publish_type = constants.PublishType.SOLICITED

        # Configure pairing settings if required.
        if is_pairing_required:
            pairing_config = constants.AwarePairingConfig(
                pairing_cache_enabled=True,
                pairing_setup_enabled=True,
                pairing_verification_enabled=True,
                bootstrapping_methods=constants.BootstrappingMethod.OPPORTUNISTIC,
            )
        else:
            pairing_config = None

        # Create publishing configuration.
        publish_config = constants.PublishConfig(
            service_name=constants.WifiAwareTestConstants.SERVICE_NAME,
            service_specific_info=constants.WifiAwareTestConstants.PUB_SSI,
            match_filter=[constants.WifiAwareTestConstants.MATCH_FILTER_BYTES],
            publish_type=publish_type,
            terminate_notification_enabled=True,
            pairing_config=pairing_config,
            ranging_enabled=is_ranging_required,
        )

        # Start the publishing session and return the handler.
        publish_handler = self.publisher.wifi_aware_snippet.wifiAwarePublish(
            publish_config.to_dict()
        )

        # Wait for publish session to start.
        publish_handler_data = publish_handler.waitAndGet(
            event_name=constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT,
            timeout=DEFAULT_TIMEOUT
        )
        callback_name = publish_handler_data.data[
            constants.DiscoverySessionCallbackParamsType.CALLBACK_NAME]
        asserts.assert_equal(
            constants.DiscoverySessionCallbackMethodType.PUBLISH_STARTED,
            callback_name,
            f'{self.publisher} publish failed, got event: {callback_name}.',
        )

        is_session_init = publish_handler_data.data[
            constants.DiscoverySessionCallbackParamsType.IS_SESSION_INIT]
        asserts.assert_true(
            is_session_init,
            f'{self.publisher} publish succeeded, but null session returned.'
        )
        self.publisher.log.info('Publish succeeded.')
        return publish_handler

    def _tardown_test(self, ad: android_device.AndroidDevice) -> None:
        """Test completed, release resources."""
        ad.wifi_aware_snippet.wifiAwareDetach()
        ad.wifi_aware_snippet.wifiAwareCloseDiscoverSession()


    def teardown_test(self):
        utils.concurrent_exec(
            self._tardown_test,
            ((self.publisher,), (self.subscriber,)),
            max_workers=2,
            raise_on_exception=True,
        )
        utils.concurrent_exec(
            lambda d: d.services.create_output_excerpts_all(self.current_test_info),
            param_list=[[ad] for ad in self.ads],
            raise_on_exception=True,
        )

    def on_fail(self, record: records.TestResult) -> None:
        android_device.take_bug_reports(self.ads, destination=self.current_test_info.output_path)


if __name__ == '__main__':
    # Take test args
    if '--' in sys.argv:
        index = sys.argv.index('--')
        sys.argv = sys.argv[:1] + sys.argv[index + 1:]

    test_runner.main()
