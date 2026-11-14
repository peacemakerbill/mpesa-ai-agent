import 'dart:async';

import 'code_reader.dart';

/// Puts the sign-in code into the code box by itself, so the screen can send it without the person typing.
///
/// There are two ways a code arrives:
///  - by text message, which the phone reads on Android ([CodeReader]), and
///  - on the screen, when the server shows it (development and staging). It is filled in after a short moment, so the
///    person can see where it came from.
///
/// Each request fills the box once. A code that was wrong is not tried again by itself.
class CodeAutoFill {
  CodeAutoFill(this.reader, {this.showFor = const Duration(milliseconds: 1200), this.startTimeout = const Duration(seconds: 3)});

  final CodeReader reader;

  /// How long a code that the server shows stays on screen before it is filled in and sent.
  final Duration showFor;

  /// The longest to wait for the phone to start listening. Asking for the code must never wait on this.
  final Duration startTimeout;

  void Function(String code)? _fill;
  Timer? _timer;
  bool _filled = false;

  /// Call this before asking for the code, so no message is missed. [fill] is called with the 6 digits.
  /// Returns the app hash to send with the request, or null when the phone cannot read the message.
  Future<String?> start(void Function(String code) fill) async {
    _timer?.cancel();
    _filled = false;
    _fill = fill;
    return reader.listen(_arrived).timeout(startTimeout, onTimeout: () => null);
  }

  void _arrived(String code) {
    if (_filled) return;
    _filled = true;
    _fill?.call(code);
  }

  /// The server showed [code] on the screen.
  void shown(String code) {
    _timer?.cancel();
    _timer = Timer(showFor, () => _arrived(code));
  }

  /// Stops listening and waiting, for example when the screen goes away.
  void stop() {
    _timer?.cancel();
    _timer = null;
    _fill = null;
    unawaited(reader.stop());
  }
}
