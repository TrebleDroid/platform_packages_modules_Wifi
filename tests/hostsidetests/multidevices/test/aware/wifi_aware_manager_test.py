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
import datetime
import enum
import logging
import random
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
    'android.permission.NEARBY_WIFI_DEVICES',
)
PACKAGE_NAME = constants.WIFI_AWARE_SNIPPET_PACKAGE_NAME
_DEFAULT_TIMEOUT = constants.WAIT_WIFI_STATE_TIME_OUT.total_seconds()
_REQUEST_NETWORK_TIMEOUT_MS = 15 * 1000
_MSG_ID_SUB_TO_PUB = random.randint(1000, 5000)
_MSG_ID_PUB_TO_SUB = random.randint(5001, 9999)
_MSG_SUB_TO_PUB = "Let's talk [Random Identifier: %s]" % utils.rand_ascii_str(5)
_MSG_PUB_TO_SUB = 'Ready [Random Identifier: %s]' % utils.rand_ascii_str(5)
_PUB_SSI = constants.WifiAwareTestConstants.PUB_SSI
_MATCH_FILTER = [constants.WifiAwareTestConstants.MATCH_FILTER_BYTES]
_CALLBACK_NAME = constants.DiscoverySessionCallbackParamsType.CALLBACK_NAME
_IS_SESSION_INIT = constants.DiscoverySessionCallbackParamsType.IS_SESSION_INIT
_TRANSPORT_TYPE_WIFI_AWARE = (
    constants.NetworkCapabilities.Transport.TRANSPORT_WIFI_AWARE
)


@enum.unique
class AttachCallBackMethodType(enum.StrEnum):
    """Represents Attach Callback Method Type in Wi-Fi Aware.

    https://developer.android.com/reference/android/net/wifi/aware/AttachCallback
    """
    ATTACHED = 'onAttached'
    ATTACH_FAILED = 'onAttachFailed'
    AWARE_SESSION_TERMINATED = 'onAwareSessionTerminated'


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

    def test_create_wifi_aware_network(self) -> None:
        """Test that creates a Wi-Fi Aware network.

        This test case tests against WiFi Aware with security type OPEN,
        unsolicited publish type, and passive subscribe type.

        Test steps:
        1. Attach a Wi-Fi Aware session on each Android device.
        2. Publisher creates a publish discovery session.
        3. Subscriber creates a subscribe discovery session.
        4. Waits for subscriber to discover the published discovery session.
        5. Sends messages between the publisher and subscriber.
        6. Creates a Wi-Fi Aware network between the publisher and subscriber.
        """
        publisher_attach_session = self._start_attach(self.publisher)
        subscriber_attach_session = self._start_attach(self.subscriber)
        pub_aware_session_cb_handler = self._start_publish(
            attach_session_id=publisher_attach_session,
            publish_type=constants.PublishType.UNSOLICITED,
            service_specific_info=_PUB_SSI,
            match_filter=_MATCH_FILTER,
            is_ranging_enabled=False,
        )
        publish_session = pub_aware_session_cb_handler.callback_id
        self.publisher.log.info('Created the publish session.')

        sub_aware_session_cb_handler = self._start_subscribe(
            attach_session_id=subscriber_attach_session,
            subscribe_type=constants.SubscribeType.PASSIVE,
            match_filter=_MATCH_FILTER,
        )
        subscribe_session = sub_aware_session_cb_handler.callback_id
        self.subscriber.log.info('Created the subscribe session.')

        # Wait for discovery.
        subscriber_peer = self._wait_for_discovery(
            sub_aware_session_cb_handler,
            pub_service_specific_info=_PUB_SSI,
            is_ranging_enabled=False,
        )
        self.subscriber.log.info('Discovered the published session.')

        # Subscriber sends a message to publisher.
        publisher_peer = self._send_msg_and_check_received(
            sender=self.subscriber,
            sender_aware_session_cb_handler=sub_aware_session_cb_handler,
            receiver=self.publisher,
            receiver_aware_session_cb_handler=pub_aware_session_cb_handler,
            discovery_session=subscribe_session,
            peer=subscriber_peer,
            send_message=_MSG_SUB_TO_PUB,
            send_message_id=_MSG_ID_SUB_TO_PUB,
        )
        logging.info(
            'The subscriber sent a message and the publisher received it.'
        )

        # Publisher sends a message to subscriber.
        self._send_msg_and_check_received(
            sender=self.publisher,
            sender_aware_session_cb_handler=pub_aware_session_cb_handler,
            receiver=self.subscriber,
            receiver_aware_session_cb_handler=sub_aware_session_cb_handler,
            discovery_session=publish_session,
            peer=publisher_peer,
            send_message=_MSG_PUB_TO_SUB,
            send_message_id=_MSG_ID_PUB_TO_SUB,
        )
        logging.info(
            'The publisher sent a message and the subscriber received it.'
        )

        # Request network.
        pub_network_cb_handler = self._request_network(ad=self.publisher,
                                                       discovery_session=publish_session,
                                                       peer=publisher_peer,)
        sub_network_cb_handler = self._request_network(ad=self.subscriber,
                                                       discovery_session=subscribe_session,
                                                       peer=subscriber_peer,)
        # Wait for network.
        self._wait_for_network(
            ad=self.publisher,
            request_network_cb_handler=pub_network_cb_handler,
        )
        self._wait_for_network(
            ad=self.subscriber,
            request_network_cb_handler=sub_network_cb_handler,
        )
        logging.info('Wi-Fi Aware network created successfully.')

        self.publisher.wifi_aware_snippet.connectivityUnregisterNetwork(pub_network_cb_handler.callback_id)
        self.subscriber.wifi_aware_snippet.connectivityUnregisterNetwork(sub_network_cb_handler.callback_id)
        self.publisher.wifi_aware_snippet.wifiAwareCloseDiscoverSession(publish_session)
        self.subscriber.wifi_aware_snippet.wifiAwareCloseDiscoverSession(subscribe_session)
        self.publisher.wifi_aware_snippet.wifiAwareDetach(publisher_attach_session)
        self.subscriber.wifi_aware_snippet.wifiAwareDetach(subscriber_attach_session)

    def _start_attach(self, ad: android_device.AndroidDevice) -> str:
        """Starts the attach process on the provided device."""
        handler = ad.wifi_aware_snippet.wifiAwareAttach()
        attach_event = handler.waitAndGet(
            event_name=AttachCallBackMethodType.ATTACHED,
            timeout=_DEFAULT_TIMEOUT,
        )
        asserts.assert_true(
            ad.wifi_aware_snippet.wifiAwareIsSessionAttached(),
            f'{ad} attach succeeded, but Wi-Fi Aware session is still null.'
        )
        ad.log.info('Attach Wi-Fi Aware session succeeded.')
        return attach_event.callback_id

    def _start_publish(
        self,
        *,
        attach_session_id,
        publish_type,
        service_name=constants.WifiAwareTestConstants.SERVICE_NAME,
        service_specific_info=constants.WifiAwareTestConstants.PUB_SSI,
        match_filter=_MATCH_FILTER,
        is_ranging_enabled=False,
    ) -> callback_event.CallbackEvent:
        """Starts a publish session with the given configuration."""
        config = constants.PublishConfig(
            service_name=service_name,
            service_specific_info=service_specific_info,
            match_filter=match_filter,
            publish_type=publish_type,
            terminate_notification_enabled=True,
            ranging_enabled=is_ranging_enabled,
        )

        # Start the publishing session and return the handler.
        publish_handler = self.publisher.wifi_aware_snippet.wifiAwarePublish(
            attach_session_id, config.to_dict()
        )

        # Wait for publish session to start.
        discovery_event = publish_handler.waitAndGet(
            event_name=constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT,
            timeout=_DEFAULT_TIMEOUT
        )
        callback_name = discovery_event.data[_CALLBACK_NAME]
        asserts.assert_equal(
            constants.DiscoverySessionCallbackMethodType.PUBLISH_STARTED,
            callback_name,
            f'{self.publisher} publish failed, got callback: {callback_name}.',
        )

        is_session_init = discovery_event.data[_IS_SESSION_INIT]
        asserts.assert_true(
            is_session_init,
            f'{self.publisher} publish succeeded, but null discovery session returned.'
        )
        return publish_handler

    def _start_subscribe(
        self,
        *,
        attach_session_id,
        subscribe_type,
        service_name=constants.WifiAwareTestConstants.SERVICE_NAME,
        service_specific_info=constants.WifiAwareTestConstants.SUB_SSI,
        match_filter=_MATCH_FILTER,
        max_distance_mm=None,
    ) -> callback_event.CallbackEvent:
        """Starts a subscribing session with the given configuration."""
        # Create subscription configuration.
        config = constants.SubscribeConfig(
            service_name=service_name,
            service_specific_info=service_specific_info,
            match_filter=match_filter,
            subscribe_type=subscribe_type,
            terminate_notification_enabled=True,
            max_distance_mm=max_distance_mm,
        )

        # Start the subscription session and return the handler.
        subscribe_handler = self.subscriber.wifi_aware_snippet.wifiAwareSubscribe(
            attach_session_id, config.to_dict()
        )

        # Wait for subscribe session to start.
        discovery_event = subscribe_handler.waitAndGet(
            event_name=constants.DiscoverySessionCallbackMethodType.DISCOVER_RESULT,
            timeout=_DEFAULT_TIMEOUT
        )
        callback_name = discovery_event.data[_CALLBACK_NAME]
        asserts.assert_equal(
            constants.DiscoverySessionCallbackMethodType.SUBSCRIBE_STARTED,
            callback_name,
            f'{self.subscriber} subscribe failed, got callback: {callback_name}.'
        )
        is_session_init = discovery_event.data[_IS_SESSION_INIT]
        asserts.assert_true(
            is_session_init,
            f'{self.subscriber} subscribe succeeded, but null session returned.'
        )
        return subscribe_handler

    def _wait_for_discovery(
        self,
        sub_aware_session_cb_handler: callback_handler_v2.CallbackHandlerV2,
        pub_service_specific_info: str,
        is_ranging_enabled: bool,
    ) -> int:
        """Waits for the subscriber to discover the published service."""
        event_name = constants.DiscoverySessionCallbackMethodType.SERVICE_DISCOVERED
        if is_ranging_enabled:
            event_name = (
                constants.DiscoverySessionCallbackMethodType.SERVICE_DISCOVERED_WITHIN_RANGE
            )
        discover_data = sub_aware_session_cb_handler.waitAndGet(
            event_name=event_name, timeout=_DEFAULT_TIMEOUT
        )

        service_info = bytes(
            discover_data.data[constants.WifiAwareSnippetParams.SERVICE_SPECIFIC_INFO]
        )
        str_expected_service_info = bytes(
            constants.WifiAwareTestConstants.PUB_SSI
        )
        asserts.assert_equal(
            service_info,
            str_expected_service_info,
            f'{self.subscriber} got unexpected service info in discovery'
            f' callback event "{event_name}".'
        )
        match_filters = discover_data.data[
            constants.WifiAwareSnippetParams.MATCH_FILTER]
        match_filters = [
            bytes(filter[constants.WifiAwareSnippetParams.MATCH_FILTER_VALUE])
            for filter in match_filters
        ]
        asserts.assert_equal(
            match_filters,
            [constants.WifiAwareTestConstants.MATCH_FILTER_BYTES],
            f'{self.subscriber} got unexpected match filter data in discovery'
            f' callback event "{event_name}".'
        )
        return discover_data.data[constants.WifiAwareSnippetParams.PEER_ID]


    def _send_msg_and_check_received(
        self,
        *,
        sender: android_device.AndroidDevice,
        sender_aware_session_cb_handler: callback_handler_v2.CallbackHandlerV2,
        receiver: android_device.AndroidDevice,
        receiver_aware_session_cb_handler: callback_handler_v2.CallbackHandlerV2,
        discovery_session: str,
        peer: int,
        send_message: str,
        send_message_id: int,
    ) -> int:
        sender.wifi_aware_snippet.wifiAwareSendMessage(
            discovery_session, peer, send_message_id, send_message
        )
        message_send_result = sender_aware_session_cb_handler.waitAndGet(
            event_name=constants.DiscoverySessionCallbackMethodType.MESSAGE_SEND_RESULT,
            timeout=_DEFAULT_TIMEOUT,
        )
        callback_name = message_send_result.data[
            constants.DiscoverySessionCallbackParamsType.CALLBACK_NAME
        ]
        asserts.assert_equal(
            callback_name,
            constants.DiscoverySessionCallbackMethodType.MESSAGE_SEND_SUCCEEDED,
            f'{sender} failed to send message with an unexpected callback.',
        )
        actual_send_message_id = message_send_result.data[
            constants.DiscoverySessionCallbackParamsType.MESSAGE_ID
        ]
        asserts.assert_equal(
            actual_send_message_id,
            send_message_id,
            f'{sender} send message succeeded but message ID mismatched.'
        )
        receive_message_event = receiver_aware_session_cb_handler.waitAndGet(
            event_name=constants.DiscoverySessionCallbackMethodType.MESSAGE_RECEIVED,
            timeout=_DEFAULT_TIMEOUT,
        )
        received_message_raw = receive_message_event.data[
            constants.WifiAwareSnippetParams.RECEIVED_MESSAGE
        ]
        received_message = bytes(received_message_raw).decode('utf-8')
        asserts.assert_equal(
            received_message,
            send_message,
            f'{receiver} received the message but message content mismatched.'
        )
        return receive_message_event.data[constants.WifiAwareSnippetParams.PEER_ID]

    def _request_network(
        self,
        ad: android_device.AndroidDevice,
        discovery_session: str,
        peer: int,
    ) -> callback_handler_v2.CallbackHandlerV2:
        """Requests a Wi-Fi Aware network."""
        network_specifier_parcel = (
            ad.wifi_aware_snippet.wifiAwareCreateNetworkSpecifier(discovery_session, peer)
        )
        network_request_dict = constants.NetworkRequest(
            transport_type=_TRANSPORT_TYPE_WIFI_AWARE,
            network_specifier_parcel=network_specifier_parcel,
        ).to_dict()
        return ad.wifi_aware_snippet.connectivityRequestNetwork(
            network_request_dict, _REQUEST_NETWORK_TIMEOUT_MS
        )

    def _wait_for_network(
        self,
        ad: android_device.AndroidDevice,
        request_network_cb_handler: callback_handler_v2.CallbackHandlerV2,
    ):
        """Waits for network to be available."""
        network_callback_event = request_network_cb_handler.waitAndGet(
            event_name=constants.NetworkCbEventName.NETWORK_CALLBACK,
            timeout=_DEFAULT_TIMEOUT,
        )
        callback_name = network_callback_event.data[_CALLBACK_NAME]
        if callback_name == constants.NetworkCbName.ON_UNAVAILABLE:
            asserts.fail(
                f'{ad} failed to request the network, got callback'
                f' {callback_name}.'
            )
        elif callback_name == constants.NetworkCbName.ON_CAPABILITIES_CHANGED:
            # `network` is the network whose capabilities have changed.
            network = network_callback_event.data[
                constants.NetworkCbEventKey.NETWORK]
            network_capabilities = network_callback_event.data[
                constants.NetworkCbEventKey.NETWORK_CAPABILITIES]
            asserts.assert_true(
                network and network_capabilities,
                f'{ad} received a null Network or NetworkCapabilities!?.'
            )
            transport_info_class_name = network_callback_event.data[
                constants.NetworkCbEventKey.TRANSPORT_INFO_CLASS_NAME]
            asserts.assert_equal(
                transport_info_class_name,
                constants.AWARE_NETWORK_INFO_CLASS_NAME,
                f'{ad} network capabilities changes but it is not a WiFi Aware'
                ' network.',
            )
        else:
            asserts.fail(
                f'{ad} got unknown request network callback {callback_name}.'
            )

    def teardown_test(self):
        utils.concurrent_exec(
            self._teardown_test_on_device,
            ((self.publisher,), (self.subscriber,)),
            max_workers=2,
            raise_on_exception=True,
        )
        utils.concurrent_exec(
            lambda d: d.services.create_output_excerpts_all(self.current_test_info),
            param_list=[[ad] for ad in self.ads],
            raise_on_exception=True,
        )

    def _teardown_test_on_device(self, ad: android_device.AndroidDevice) -> None:
        ad.wifi_aware_snippet.wifiAwareCloseAllWifiAwareSession()

    def on_fail(self, record: records.TestResult) -> None:
        android_device.take_bug_reports(self.ads, destination=self.current_test_info.output_path)


if __name__ == '__main__':
    # Take test args
    if '--' in sys.argv:
        index = sys.argv.index('--')
        sys.argv = sys.argv[:1] + sys.argv[index + 1:]

    test_runner.main()
