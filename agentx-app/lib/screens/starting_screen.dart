import 'dart:async';

import 'package:flutter/material.dart';

import '../app_state.dart';
import '../services/waker.dart';
import '../widgets/ui.dart';

/// Shown while the server wakes up. A free host stops a service that nobody uses, and the first request starts it again,
/// which takes about a minute. The app asks every few seconds and opens as soon as everything answers.
class StartingScreen extends StatefulWidget {
  const StartingScreen({super.key, required this.state});

  final AppState state;

  @override
  State<StartingScreen> createState() => _StartingScreenState();
}

class _StartingScreenState extends State<StartingScreen> {
  final Stopwatch _watch = Stopwatch()..start();
  Timer? _tick;

  @override
  void initState() {
    super.initState();
    // Repaints every second so the "taking longer" note appears on time.
    _tick = Timer.periodic(const Duration(seconds: 1), (_) {
      if (mounted) setState(() {});
    });
  }

  @override
  void dispose() {
    _tick?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final failed = widget.state.server == ServerPhase.unreachable;
    final slow = !failed && _watch.elapsed > const Duration(seconds: 90);
    final answer = widget.state.lastWake;

    return Scaffold(
      body: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 420),
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const BrandMark(size: 88, motion: BrandMotion.loop),
                  const SizedBox(height: 24),
                  Text(
                    failed ? 'Could not start the services' : 'Starting the services',
                    style: text.titleLarge,
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 8),
                  Text(
                    failed
                        ? 'Check your internet connection and try again. If it keeps happening, the server may be down.'
                        : 'The server rests when nobody is using it. Waking it takes up to a minute. You do not need to do anything.',
                    style: text.bodyMedium,
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 24),
                  if (failed)
                    FilledButton(onPressed: widget.state.connect, child: const Text('Try again'))
                  else ...[
                    _Line(label: 'Server', done: answer != null),
                    const SizedBox(height: 8),
                    _Line(label: 'Assistant', done: answer?.agent ?? false),
                    if (slow) ...[
                      const SizedBox(height: 20),
                      Text('This is taking longer than usual. Still trying.', style: text.bodySmall, textAlign: TextAlign.center),
                    ],
                  ],
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// One service and whether it is awake yet.
class _Line extends StatelessWidget {
  const _Line({required this.label, required this.done});

  final String label;
  final bool done;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: '$label ${done ? 'is ready' : 'is starting'}',
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          SizedBox(
            width: 20,
            height: 20,
            child: done
                ? Icon(Icons.check_circle, size: 20, color: Theme.of(context).colorScheme.primary)
                : const Padding(padding: EdgeInsets.all(2), child: CircularProgressIndicator(strokeWidth: 2)),
          ),
          const SizedBox(width: 10),
          Text(done ? '$label is ready' : '$label is starting'),
        ],
      ),
    );
  }
}
