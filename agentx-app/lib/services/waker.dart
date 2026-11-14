import '../api/api_client.dart';
import '../models/wake.dart';

/// Where the app stands with the server. It is [checking] for the first moments, [starting] when the server is asleep
/// and is being woken, [ready] when everything answers and [unreachable] when waking took too long.
enum ServerPhase { checking, starting, ready, unreachable }

/// Wakes a server that sleeps when nobody uses it, and waits until it is ready.
///
/// Free hosts stop a service after a quiet spell and start it again on the next request, which takes about a minute. Asking
/// the server to wake is that request. This keeps asking every few seconds, so the app can show "Starting the services"
/// instead of a failed sign-in.
class Waker {
  Waker(
    this._wake, {
    this.pause = const Duration(seconds: 3),
    this.giveUpAfter = const Duration(minutes: 4),
    this.showAfter = const Duration(milliseconds: 1500),
    Future<void> Function(Duration)? sleep,
  }) : _sleep = sleep ?? Future<void>.delayed;

  final Future<WakeStatus> Function() _wake;
  final Future<void> Function(Duration) _sleep;

  /// How long to wait between two questions.
  final Duration pause;

  /// After this long without an answer that says "ready", the app stops and offers to try again.
  final Duration giveUpAfter;

  /// A server that answers sooner than this never shows the "Starting" screen at all.
  final Duration showAfter;

  /// Asks until the server is ready. [onAnswer] gets every answer, or null when the server did not answer yet.
  /// Returns true when ready and false when [giveUpAfter] passed first.
  Future<bool> run({void Function(WakeStatus? answer)? onAnswer}) async {
    final clock = Stopwatch()..start();
    while (true) {
      try {
        final answer = await _wake();
        onAnswer?.call(answer);
        if (answer.ready) return true;
      } on ApiException catch (e) {
        // A server from before this check existed has no such address. It never sleeps in a way we could help, so go on.
        if (e.status == 404) return true;
        // 0 is no answer at all, 502 and 503 are what a host says while a service starts. Keep asking.
        onAnswer?.call(null);
      }
      if (clock.elapsed >= giveUpAfter) return false;
      await _sleep(pause);
    }
  }
}
