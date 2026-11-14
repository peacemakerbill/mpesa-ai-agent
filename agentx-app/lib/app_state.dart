import 'dart:async';

import 'package:flutter/foundation.dart';

import 'api/api_client.dart';
import 'chat/chat_device.dart';
import 'chat/platform_chat_device.dart';
import 'config.dart';
import 'contacts/contact_services.dart';
import 'contacts/platform/platform_contacts.dart';
import 'models/wake.dart';
import 'models/wallet_action.dart';
import 'services/approval.dart';
import 'services/code_reader.dart';
import 'services/launcher.dart';
import 'services/secure_store.dart';
import 'services/sms_reader.dart';
import 'services/waker.dart';
import 'theme/app_theme.dart';

/// Holds what every screen shares: whether this phone is signed in, the hand-off settings, and a link waiting to be opened.
class AppState extends ChangeNotifier {
  AppState({SecureStore? store, ApiClient? api, Approval? approval, Launcher? launcher, ContactServices? contacts, ChatDevice? chatDevice, SmsReader? sms, Waker? waker, CodeReader? codeReader}) {
    this.store = store ?? SecureStore();
    this.api = api ?? ApiClient(baseUrl: AppConfig.apiBaseUrl, tokenProvider: this.store.token);
    this.approval = approval ?? Approval();
    this.launcher = launcher ?? const Launcher();
    this.contacts = contacts ?? createPlatformContactServices();
    this.chatDevice = chatDevice ?? PlatformChatDevice();
    this.sms = sms ?? DeviceSmsReader();
    this.waker = waker ?? Waker(() => this.api.wake());
    this.codeReader = codeReader ?? DeviceCodeReader();
  }

  late final SecureStore store;
  late final ApiClient api;
  late final Approval approval;
  late final Launcher launcher;
  late final ContactServices contacts;
  late final ChatDevice chatDevice;
  late final SmsReader sms;
  late final Waker waker;

  /// Reads the sign-in code from the text message, on Android. Signing in and signing up both use it.
  late final CodeReader codeReader;

  bool ready = false;

  /// Whether the server is awake. A server that sleeps when idle is woken by [connect]; until it answers the app shows
  /// the "Starting the services" screen.
  ServerPhase server = ServerPhase.checking;

  /// The last answer to the wake-up question, so the screen can show what is already awake. Null until one arrives.
  WakeStatus? lastWake;
  DateTime? _readyAt;

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
  }

  /// Wakes the server if it is asleep and waits until it is ready, then reads its settings. Safe to call again, for
  /// instance from the "Try again" button.
  Future<void> connect() async {
    server = ServerPhase.checking;
    lastWake = null;
    notifyListeners();
    // A quick answer never shows the "Starting" screen. Only a server that is really asleep does.
    final reveal = Timer(waker.showAfter, () {
      if (server == ServerPhase.checking) {
        server = ServerPhase.starting;
        notifyListeners();
      }
    });
    final ok = await waker.run(onAnswer: (answer) {
      lastWake = answer;
      if (server == ServerPhase.starting) notifyListeners();
    });
    reveal.cancel();
    server = ok ? ServerPhase.ready : ServerPhase.unreachable;
    if (ok) _readyAt = DateTime.now();
    notifyListeners();
    if (ok) await loadConfig();
  }

  /// Call when the app comes back to the front. A free host puts the server to sleep after about 15 quiet minutes, so
  /// after a long break the app asks once, quietly. Nothing changes on screen while the server is still awake. If it is not,
  /// the "Starting the services" screen comes back and waits for it.
  Future<void> returned() async {
    final since = _readyAt;
    if (server != ServerPhase.ready || since == null) return;
    if (DateTime.now().difference(since) < const Duration(minutes: 10)) return;
    try {
      final answer = await api.wake().timeout(const Duration(seconds: 4));
      if (answer.ready) {
        _readyAt = DateTime.now();
        return;
      }
    } catch (_) {
      // no answer in four seconds means it is asleep, or there is no connection: wait for it properly below
    }
    await connect();
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
  Future<CodeRequest> requestCode(String phoneNumber, {String? appHash}) => api.requestCode(phoneNumber, appHash: appHash);

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
