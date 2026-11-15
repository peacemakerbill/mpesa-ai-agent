import 'package:flutter/foundation.dart';

/// The server address is picked for you while you develop, and set at build time for anything else:
///   flutter run --dart-define=API_BASE_URL=https://agentx.example.com
class AppConfig {
  AppConfig._();

  static const String _fromBuild = String.fromEnvironment('API_BASE_URL');

  /// The address given at build time, or the usual local one for the platform the app is running on.
  static String get apiBaseUrl => _fromBuild.isNotEmpty ? _fromBuild : defaultApiBaseUrl(defaultTargetPlatform, web: kIsWeb);

  /// A release build has no business looking for a server on the user's own phone. It must be told where the real one is, so
  /// that a forgotten setting shows a plain message on the first run instead of a sign-in that never works.
  static bool get missingServerAddress => needsServerAddress(release: kReleaseMode, configured: _fromBuild);

  static bool needsServerAddress({required bool release, required String configured}) => release && configured.isEmpty;

  /// Where a server on the developer's own computer is found. The Android emulator reaches the computer as 10.0.2.2, and
  /// everything else (Linux, macOS and Windows desktops, the iOS simulator, a browser) as localhost. A real phone, a
  /// staging server and a release build need API_BASE_URL.
  static String defaultApiBaseUrl(TargetPlatform platform, {required bool web}) =>
      !web && platform == TargetPlatform.android ? 'http://10.0.2.2:8084' : 'http://localhost:8084';

  /// Where the assistant service is, for example https://agent-service-free-ab.onrender.com. The app asks it to wake up at
  /// the same time as the server, so a free host that puts both to sleep is ready sooner. Empty does nothing, which is right
  /// for a server that never sleeps. Only the address of its public health check is used, and it holds no secret.
  static const String agentWakeUrl = String.fromEnvironment('AGENT_WAKE_URL');

  /// The custom scheme used when the https link does not open the app directly.
  static const String linkScheme = 'agentx';

  /// Application (client) id of the Microsoft app registration, for Outlook contacts. Empty hides that source.
  ///   flutter run --dart-define=MS_CLIENT_ID=00000000-0000-0000-0000-000000000000
  static const String microsoftClientId = String.fromEnvironment('MS_CLIENT_ID');

  /// Must match a redirect URI of that registration and the scheme in the platform setup (docs/CONTACTS.md).
  static const String microsoftRedirectUrl = 'agentxauth://callback';
}
