/// Set at build time:
///   flutter run --dart-define=API_BASE_URL=https://companion.example.com
class AppConfig {
  AppConfig._();

  static const String apiBaseUrl =
      String.fromEnvironment('API_BASE_URL', defaultValue: 'http://10.0.2.2:8084');

  /// The custom scheme used when the https link does not open the app directly.
  static const String linkScheme = 'mpesaagent';

  /// Application (client) id of the Microsoft app registration, for Outlook contacts. Empty hides that source.
  ///   flutter run --dart-define=MS_CLIENT_ID=00000000-0000-0000-0000-000000000000
  static const String microsoftClientId = String.fromEnvironment('MS_CLIENT_ID');

  /// Must match a redirect URI of that registration and the scheme in the platform setup (docs/CONTACTS.md).
  static const String microsoftRedirectUrl = 'mpesaagentauth://callback';
}
