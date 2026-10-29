import 'package:flutter_test/flutter_test.dart';
import 'package:mpesa_companion/models/progress.dart';
import 'package:mpesa_companion/models/wallet_action.dart';

WalletAction action(String status, {String verification = 'NONE', String type = 'PAY_TILL'}) => WalletAction.fromJson({
      'id': 'x',
      'type': type,
      'status': status,
      'amount': 300,
      'summary': 'Pay KES 300 to Till 123456',
      'details': <String, dynamic>{},
      'warnings': <String>[],
      'needsContact': false,
      'verification': verification,
    });

List<StepState> states(WalletAction a) => progressFor(a).map((s) => s.state).toList();

void main() {
  test('a new request has made the first step and waits for approval', () {
    expect(states(action('CREATED')), [StepState.done, StepState.now, StepState.todo, StepState.todo, StepState.todo]);
  });

  test('an opened request waits for the customer to say how it went', () {
    expect(states(action('HANDED_OFF')), [StepState.done, StepState.done, StepState.done, StepState.now, StepState.todo]);
  });

  test('a finished request with a matching message has every step done', () {
    expect(states(action('DONE_REPORTED', verification: 'SMS_MATCHED')), everyElement(StepState.done));
  });

  test('a declined request stops after the first step and says so', () {
    final steps = progressFor(action('DECLINED'));
    expect(steps.map((s) => s.state), [StepState.done, StepState.todo, StepState.todo, StepState.todo, StepState.bad, StepState.todo]);
    expect(steps[4].label, 'Declined');
  });

  test('a request that did not go through keeps the steps it passed', () {
    final steps = progressFor(action('NOT_DONE'));
    expect(steps.take(3).map((s) => s.state), everyElement(StepState.done));
    expect(steps[3].state, StepState.todo);
    expect(steps[4].state, StepState.bad);
  });

  test('amounts get thousands separators', () {
    expect(formatKes(0), '0');
    expect(formatKes(950), '950');
    expect(formatKes(1200), '1,200');
    expect(formatKes(1234567), '1,234,567');
  });

  test('detail names read as plain words', () {
    expect(niceKey('recipientPhone'), 'Recipient phone');
    expect(niceKey('till'), 'Till');
    expect(niceKey('airtimePhone'), 'Airtime phone');
  });

  test('every kind of request has a plain name', () {
    expect(typeLabel('SEND_MONEY'), 'Send money');
    expect(typeLabel('PAY_TILL'), 'Buy Goods');
    expect(typeLabel('SOMETHING_NEW'), 'SOMETHING_NEW');
  });
}
