import 'package:agentx_app/config.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('the Android emulator reaches the computer as 10.0.2.2', () {
    expect(AppConfig.defaultApiBaseUrl(TargetPlatform.android, web: false), 'http://10.0.2.2:8084');
  });

  test('desktops and the iOS simulator use localhost', () {
    for (final p in [TargetPlatform.linux, TargetPlatform.macOS, TargetPlatform.windows, TargetPlatform.iOS]) {
      expect(AppConfig.defaultApiBaseUrl(p, web: false), 'http://localhost:8084', reason: '$p');
    }
  });

  test('only a release build without an address is a mistake', () {
    expect(AppConfig.needsServerAddress(release: true, configured: ''), isTrue);
    expect(AppConfig.needsServerAddress(release: true, configured: 'https://agentx.example.com'), isFalse);
    expect(AppConfig.needsServerAddress(release: false, configured: ''), isFalse);
  });

  test('a browser on Android still uses localhost', () {
    expect(AppConfig.defaultApiBaseUrl(TargetPlatform.android, web: true), 'http://localhost:8084');
  });
}
