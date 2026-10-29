import 'wallet_action.dart';

enum StepState { done, now, todo, bad }

class TimelineStep {
  const TimelineStep(this.label, this.state, [this.note]);

  final String label;
  final StepState state;
  final String? note;
}

const _closedBad = {'DECLINED', 'CANCELLED', 'EXPIRED', 'NOT_DONE'};
const _order = ['CREATED', 'APPROVED', 'HANDED_OFF', 'DONE_REPORTED'];
const _labels = ['Request made', 'Approved on this phone', 'Opened in M-Pesa', 'Reported as done'];

/// Where a request is on its way, for the timeline on the detail screen.
/// A step is done once the request has passed it, "now" is the one the customer does next.
List<TimelineStep> progressFor(WalletAction a) {
  final bad = _closedBad.contains(a.status);
  var reached = a.status == 'NOT_DONE' ? 2 : (bad ? 0 : _order.indexOf(a.status));
  if (reached < 0) reached = 0;
  final steps = <TimelineStep>[
    for (var i = 0; i < _labels.length; i++)
      TimelineStep(
        _labels[i],
        i <= reached ? StepState.done : (i == reached + 1 && !bad ? StepState.now : StepState.todo),
      ),
  ];
  if (bad) steps.add(TimelineStep(a.statusText, StepState.bad));
  steps.add(a.smsBacked
      ? const TimelineStep('Message check', StepState.done, 'A message on this phone matched')
      : const TimelineStep('Message check', StepState.todo, 'Optional, after you finish in M-Pesa'));
  return steps;
}

const _typeLabels = {
  'SEND_MONEY': 'Send money',
  'PAY_BILL': 'Pay Bill',
  'PAY_TILL': 'Buy Goods',
  'PAY_POCHI': 'Pochi la Biashara',
  'BUY_AIRTIME': 'Buy airtime',
  'SCHEDULE_RATIBA': 'Ratiba',
};

/// A plain name for the kind of request.
String typeLabel(String type) => _typeLabels[type] ?? type;

/// 1200 becomes "1,200". Whole shillings only, that is all the server sends.
String formatKes(int amount) {
  final digits = amount.abs().toString();
  final out = StringBuffer(amount < 0 ? '-' : '');
  for (var i = 0; i < digits.length; i++) {
    if (i > 0 && (digits.length - i) % 3 == 0) out.write(',');
    out.write(digits[i]);
  }
  return out.toString();
}

/// "recipientPhone" becomes "Recipient phone".
String niceKey(String key) {
  final spaced = key.replaceAllMapped(RegExp(r'([a-z0-9])([A-Z])'), (m) => '${m[1]} ${m[2]!.toLowerCase()}');
  return spaced.isEmpty ? spaced : spaced[0].toUpperCase() + spaced.substring(1);
}
