import 'dart:math';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Keeps the login token and a random device id in the phone's secure storage (Keystore on Android, Keychain on iOS).
/// The M-Pesa PIN is never stored anywhere, it is only ever typed inside M-Pesa.
class SecureStore {
  SecureStore([FlutterSecureStorage? storage])
      : _storage = storage ?? const FlutterSecureStorage();

  final FlutterSecureStorage _storage;

  static const _token = 'token';
  static const _phone = 'phone';
  static const _expires = 'expires';
  static const _device = 'deviceId';
  static const _smsConsent = 'smsConsent';
  static const _theme = 'theme';

  Future<String> deviceId() async {
    final existing = await _storage.read(key: _device);
    if (existing != null && existing.length >= 8) return existing;
    const chars = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
    final random = Random.secure();
    final id = List.generate(32, (_) => chars[random.nextInt(chars.length)]).join();
    await _storage.write(key: _device, value: id);
    return id;
  }

  Future<void> saveSession({required String token, required String phone, required DateTime expiresAt}) async {
    await _storage.write(key: _token, value: token);
    await _storage.write(key: _phone, value: phone);
    await _storage.write(key: _expires, value: expiresAt.toIso8601String());
  }

  /// The token, or null when there is none or it has expired.
  Future<String?> token() async {
    final token = await _storage.read(key: _token);
    final expires = DateTime.tryParse(await _storage.read(key: _expires) ?? '');
    if (token == null || expires == null || expires.isBefore(DateTime.now())) return null;
    return token;
  }

  Future<String?> phone() => _storage.read(key: _phone);

  /// The customer agreed to let the app look at M-Pesa messages on request.
  Future<bool> smsConsent() async => await _storage.read(key: _smsConsent) == 'yes';

  Future<void> setSmsConsent(bool value) =>
      value ? _storage.write(key: _smsConsent, value: 'yes') : _storage.delete(key: _smsConsent);

  /// The look the customer picked (a ThemeKey name). Not a secret, kept here so the app needs no extra package.
  Future<String?> theme() => _storage.read(key: _theme);

  Future<void> setTheme(String name) => _storage.write(key: _theme, value: name);

  Future<void> clearSession() async {
    await _storage.delete(key: _token);
    await _storage.delete(key: _phone);
    await _storage.delete(key: _expires);
  }
}
