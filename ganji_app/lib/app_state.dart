import 'package:flutter/foundation.dart';

import 'api/api_client.dart';
import 'chat/chat_device.dart';
import 'chat/platform_chat_device.dart';
import 'config.dart';
import 'contacts/contact_services.dart';
import 'contacts/platform/platform_contacts.dart';
import 'models/wallet_action.dart';
import 'services/approval.dart';
import 'services/launcher.dart';
import 'services/secure_store.dart';
import 'services/sms_reader.dart';
import 'theme/app_theme.dart';

/// Holds what every screen shares: whether this phone is signed in, the hand-off settings, and a link waiting to be opened.
class AppState extends ChangeNotifier {
  AppState({SecureStore? store, ApiClient? api, Approval? approval, Launcher? launcher, ContactServices? contacts, ChatDevice? chatDevice, SmsReader? sms}) {
    this.store = store ?? SecureStore();
    this.api = api ?? ApiClient(baseUrl: AppConfig.apiBaseUrl, tokenProvider: this.store.token);
    this.approval = approval ?? Approval();
    this.launcher = launcher ?? Launcher();
    this.contacts = contacts ?? createPlatformContactServices();
    this.chatDevice = chatDevice ?? PlatformChatDevice();
    this.sms = sms ?? DeviceSmsReader();
  }

  late final SecureStore store;
  late final ApiClient api;
  late final Approval approval;
  late final Launcher launcher;
  late final ContactServices contacts;
  late final ChatDevice chatDevice;
  late final SmsReader sms;

  bool ready = false;
  bool linked = false;
  String? phone;
  HandoffConfig config = HandoffConfig.fallback;

  /// The look the customer picked. Kept across unlinking, it is not tied to the account.
  ThemeKey theme = ThemeKey.daylight;

  /// A request id from a link, opened as soon as the phone is linked.
  String? pendingActionId;

  Future<void> start() async {
    theme = ThemeKeyX.parse(await store.theme());
    linked = await store.token() != null;
    phone = linked ? await store.phone() : null;
    ready = true;
    notifyListeners();
    if (linked) await loadConfig();
  }

  Future<void> setTheme(ThemeKey key) async {
    if (key == theme) return;
    theme = key;
    notifyListeners();
    try {
      await store.setTheme(key.name);
    } catch (_) {
      // the choice still applies until the app closes
    }
  }

  Future<void> loadConfig() async {
    try {
      config = await api.config();
      notifyListeners();
    } on ApiException {
      // keep the fallback, the guided steps still work
    }
  }

  /// Asks for a sign-in code to be sent by SMS to [phoneNumber].
  Future<CodeRequest> requestCode(String phoneNumber) => api.requestCode(phoneNumber);

  Future<void> link({required String phoneNumber, required String code}) async {
    final result = await api.verify(
      phone: phoneNumber,
      code: code,
      deviceId: await store.deviceId(),
      deviceName: 'Phone',
    );
    await store.saveSession(token: result.token, phone: result.phone, expiresAt: result.expiresAt);
    phone = result.phone;
    linked = true;
    notifyListeners();
    await loadConfig();
  }

  Future<void> signOut() async {
    try {
      await api.signOut();
    } on ApiException {
      // still sign out on this phone
    }
    await store.clearSession();
    await store.setSmsConsent(false);
    linked = false;
    phone = null;
    notifyListeners();
  }

  /// Call when the server says the login is no longer valid.
  Future<void> sessionExpired() async {
    await store.clearSession();
    linked = false;
    notifyListeners();
  }

  void openLink(String actionId) {
    pendingActionId = actionId;
    notifyListeners();
  }

  String? takePending() {
    final id = pendingActionId;
    pendingActionId = null;
    return id;
  }
}
