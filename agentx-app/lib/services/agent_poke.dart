import 'dart:async';

import 'package:http/http.dart' as http;

/// Wakes the assistant service at the same moment as the server, instead of after it.
///
/// On a free host both services sleep. The app only talks to the server, and the server wakes the assistant service
/// once it is itself awake, so the two waits add up. This sends the assistant service its own wake-up request at the
/// start, so both wake together and the wait is the longer of the two, not the sum.
///
/// It is only a nudge. The answer is never needed: a browser hides the answer of another address, and the server still
/// says when everything is ready. With no address (the setting is empty) it does nothing.
class AgentPoke {
  AgentPoke(
    String baseUrl, {
    http.Client? client,
    this.patience = const Duration(seconds: 100),
    this.pause = const Duration(seconds: 5),
    this.giveUpAfter = const Duration(minutes: 5),
    Future<void> Function(Duration)? sleep,
  })  : _base = _clean(baseUrl),
        _injected = client,
        _sleep = sleep ?? Future<void>.delayed;

  /// Spring Boot's liveness check on the assistant service. It needs no key and shows nothing private.
  static const String healthPath = '/actuator/health/liveness';

  final String _base;
  final http.Client? _injected;
  final Future<void> Function(Duration) _sleep;

  /// How long one request may wait. A sleeping service answers only after it has started, about a minute.
  final Duration patience;

  /// How long to wait before asking again when the last request ended without a good answer.
  final Duration pause;

  /// After this long the nudge stops by itself, even if [stop] was never called.
  final Duration giveUpAfter;

  late final http.Client _client = _injected ?? http.Client();

  bool _active = false;
  int _run = 0;

  /// The address with no slash at the end, or empty when there is none or it is not an http or https address.
  static String _clean(String value) {
    final text = value.trim();
    final uri = Uri.tryParse(text);
    if (uri == null || !(uri.scheme == 'http' || uri.scheme == 'https') || uri.host.isEmpty) return '';
    return text.endsWith('/') ? text.substring(0, text.length - 1) : text;
  }

  /// False when no address was given. Then [start] does nothing.
  bool get enabled => _base.isNotEmpty;

  /// Sends the wake-up request and keeps sending it until [stop], a good answer, or [giveUpAfter]. The returned future
  /// ends when it stops. The app does not wait for it.
  Future<void> start() {
    if (!enabled) return Future<void>.value();
    _active = true;
    return _loop(++_run);
  }

  /// Stops asking. Safe to call when it never started.
  void stop() => _active = false;

  Future<void> _loop(int run) async {
    final uri = Uri.parse('$_base$healthPath');
    final clock = Stopwatch()..start();
    while (_active && run == _run) {
      try {
        final answer = await _client.get(uri).timeout(patience);
        // A phone can read the answer, and 200 means the service is awake. A browser cannot, and ends up below.
        if (answer.statusCode == 200) break;
      } catch (_) {
        // A browser refuses to show the answer of another address, a host says 502 or 503 while a service starts, and a
        // request can run out of patience. The request was sent, which is all that wakes the service. Ask again.
      }
      if (!_active || run != _run || clock.elapsed >= giveUpAfter) break;
      await _sleep(pause);
    }
    if (run == _run) _active = false;
  }
}
