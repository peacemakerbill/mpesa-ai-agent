import 'dart:async';
import 'dart:io' show Platform;

import 'package:sms_autofill/sms_autofill.dart';

/// Reads the sign-in code from the text message, so the person does not type it. Android only.
///
/// This uses Google's SMS Retriever. It needs no SMS permission and cannot read anything else: it hands over one message,
/// and only a message that ends with this app's own 11 character hash. The app sends that hash with the code request, and
/// the server adds it to the text. It listens for five minutes at most and keeps nothing.
///
/// iPhone needs none of this. The keyboard offers the code from the message, and the code box already accepts it.
abstract class CodeReader {
  bool get supported;

  /// Starts listening for one message, then returns the app's hash for the server to put at the end of the text.
  /// Returns null when this phone cannot read the message, so the person types the code. Never throws.
  Future<String?> listen(void Function(String code) onCode);

  Future<void> stop();
}

class DeviceCodeReader implements CodeReader {
  StreamSubscription<String>? _sub;

  static final _digits = RegExp(r'\d{6}');
  static final _hash = RegExp(r'^[A-Za-z0-9+/]{11}$');

  @override
  bool get supported {
    try {
      return Platform.isAndroid;
    } on UnsupportedError {
      return false;
    }
  }

  @override
  Future<String?> listen(void Function(String code) onCode) async {
    if (!supported) return null;
    try {
      await stop();
      final reader = SmsAutoFill();
      _sub = reader.code.listen((text) {
        final match = _digits.firstMatch(text);
        if (match != null) onCode(match.group(0)!);
      });
      await reader.listenForCode(smsCodeRegexPattern: r'\d{6}');
      final hash = await reader.getAppSignature;
      return _hash.hasMatch(hash) ? hash : null;
    } catch (_) {
      // No Google Play services, or the plugin is missing. Signing in by typing the code still works.
      return null;
    }
  }

  @override
  Future<void> stop() async {
    await _sub?.cancel();
    _sub = null;
    if (!supported) return;
    try {
      await SmsAutoFill().unregisterListener();
    } catch (_) {
      // Nothing was listening.
    }
  }
}
