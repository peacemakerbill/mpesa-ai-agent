import 'package:flutter/material.dart';

import '../models/business_operation.dart';
import '../theme/app_theme.dart';

/// What happened to a request, oldest first: asked, approved, sent, accepted, timed out, finished. Each step has the
/// time it happened and a plain sentence, so a person can see exactly where a payment got to.
class OperationTimeline extends StatelessWidget {
  const OperationTimeline({super.key, required this.steps, DateTime? now}) : _now = now;

  final List<OperationStep> steps;
  final DateTime? _now;

  static String _two(int n) => n.toString().padLeft(2, '0');

  /// "14:05" for today and "03 Nov 14:05" for another day.
  static String whenLabel(DateTime at, DateTime now) {
    final time = '${_two(at.hour)}:${_two(at.minute)}';
    if (at.year == now.year && at.month == now.month && at.day == now.day) return time;
    const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    return '${_two(at.day)} ${months[at.month - 1]} $time';
  }

  @override
  Widget build(BuildContext context) {
    final t = context.tk;
    final text = Theme.of(context).textTheme;
    final now = _now ?? DateTime.now();
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Text('What happened', style: text.titleSmall),
      const SizedBox(height: 8),
      for (var i = 0; i < steps.length; i++)
        IntrinsicHeight(
          child: Row(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            SizedBox(
              width: 22,
              child: Column(children: [
                Padding(
                  padding: const EdgeInsets.only(top: 4),
                  child: Icon(_icon(steps[i]), size: 16, color: _color(t, steps[i])),
                ),
                if (i < steps.length - 1) Expanded(child: Container(width: 1.5, color: t.p.line)),
              ]),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Padding(
                padding: const EdgeInsets.only(bottom: 12),
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  if (steps[i].at != null) Text(whenLabel(steps[i].at!, now), style: text.bodySmall?.copyWith(color: t.p.ink2)),
                  Text(steps[i].text.isEmpty ? _fallback(steps[i].code) : steps[i].text, style: text.bodyMedium),
                ]),
              ),
            ),
          ]),
        ),
    ]);
  }

  static IconData _icon(OperationStep s) {
    if (s.isGood) return Icons.check_circle;
    if (s.isBad) return Icons.cancel;
    if (s.isUnsure) return Icons.help;
    return Icons.circle;
  }

  static Color _color(Tokens t, OperationStep s) {
    if (s.isGood) return t.p.good;
    if (s.isBad) return t.p.bad;
    if (s.isUnsure) return t.p.warn;
    return t.p.ink2;
  }

  static String _fallback(String code) {
    switch (code) {
      case 'ASKED':
        return 'Asked.';
      case 'WAITING':
        return 'Waiting for your approval.';
      case 'APPROVED':
        return 'You approved it.';
      case 'SENT':
        return 'Sent to Safaricom.';
      case 'ACCEPTED':
        return 'Safaricom accepted it.';
      case 'TIMEOUT':
        return 'Safaricom reported a timeout.';
      case 'SUCCEEDED':
        return 'Done.';
      case 'FAILED':
        return 'Did not go through.';
      case 'DECLINED':
        return 'Declined.';
      case 'EXPIRED':
        return 'Not approved in time.';
      default:
        return 'Not sure how it ended.';
    }
  }
}
