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
                f'{device} Wi-Fi Aware is not available',
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
        # TODO: publish and subscribe, then check if the subscriber received the message.

    def teardown_test(self):
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
