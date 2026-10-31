import 'package:flutter_test/flutter_test.dart';
import 'package:ganji_app/models/sms_match.dart';
import 'package:ganji_app/models/wallet_action.dart';

final approved = DateTime(2026, 10, 29, 21, 10);

WalletAction action(String type, Map<String, String> details, {int amount = 500, bool needsContact = false}) => WalletAction(
      id: '44444444-4444-4444-4444-444444444444',
      type: type,
      status: 'HANDED_OFF',
      amount: amount,
      summary: 's',
      details: details,
      warnings: const [],
      needsContact: needsContact,
      approvedAt: approved,
    );

RawSms sms(String body, {String address = 'MPESA', DateTime? at}) =>
    RawSms(address: address, body: body, date: at ?? approved.add(const Duration(minutes: 3)));

const sent = 'SGL1234567 Confirmed. Ksh500.00 sent to JOHN DOE 0712345678 on 29/10/26 at 9:15 PM. New M-PESA balance is Ksh1,200.00.';

void main() {
  test('finds the message for the request', () {
    final check = findMatchingSms(action('SEND_MONEY', {'recipientPhone': '254712345678'}), [sms(sent)]);

    expect(check.isFound, isTrue);
    expect(check.sms!.code, 'SGL1234567');
    expect(check.sms!.payee, contains('0712345678'));
  });

  test('only the MPESA sender counts', () {
    final check = findMatchingSms(action('SEND_MONEY', {'recipientPhone': '254712345678'}), [sms(sent, address: '0722000111')]);

    expect(check.isFound, isFalse);
    expect(check.problem, isNull);
  });

  test('a different amount, kind or an old message is not a match', () {
    final a = action('SEND_MONEY', {'recipientPhone': '254712345678'});

    expect(findMatchingSms(a, [sms(sent.replaceFirst('Ksh500.00', 'Ksh400.00'))]).isFound, isFalse);
    expect(findMatchingSms(action('PAY_TILL', {'till': '123456'}), [sms(sent)]).isFound, isFalse);
    expect(findMatchingSms(a, [sms(sent, at: approved.subtract(const Duration(minutes: 20)))]).isFound, isFalse);
  });

  test('the newest of several matching messages is used', () {
    final older = sms(sent.replaceFirst('SGL1234567', 'AAA1111111'), at: approved.add(const Duration(minutes: 1)));
    final newer = sms(sent.replaceFirst('SGL1234567', 'BBB2222222'), at: approved.add(const Duration(minutes: 4)));

    final check = findMatchingSms(action('SEND_MONEY', {'recipientPhone': '254712345678'}), [older, newer]);

    expect(check.sms!.code, 'BBB2222222');
  });

  test('a contact request is checked here and the payee is not sent', () {
    final a = action('SEND_MONEY', {'contactName': 'Mama'}, needsContact: true);

    final ok = findMatchingSms(a, [sms(sent)], contactPhone: '+254712345678');
    expect(ok.isFound, isTrue);
    expect(ok.sms!.payee, isNull);

    final wrong = findMatchingSms(a, [sms(sent)], contactPhone: '+254799999999');
    expect(wrong.isFound, isFalse);
    expect(wrong.problem, contains('different number'));
  });

  test('a Ratiba has no message to find', () {
    expect(findMatchingSms(action('SCHEDULE_RATIBA', {'target': 'PAY_TILL'}), [sms(sent)]).isFound, isFalse);
  });

  test('which requests can be checked against a message', () {
    WalletAction withStatus(String status, {String verification = 'NONE', String type = 'PAY_TILL'}) => WalletAction(
        id: 'x', type: type, status: status, amount: 1, summary: '', details: const {}, warnings: const [], needsContact: false,
        verification: verification);

    expect(withStatus('HANDED_OFF').canCheckSms, isTrue);
    expect(withStatus('NOT_DONE').canCheckSms, isTrue);
    expect(withStatus('DONE_REPORTED').canCheckSms, isTrue);
    expect(withStatus('DONE_REPORTED', verification: 'SMS_MATCHED').canCheckSms, isFalse);
    expect(withStatus('CREATED').canCheckSms, isFalse);
    expect(withStatus('DECLINED').canCheckSms, isFalse);
    expect(withStatus('HANDED_OFF', type: 'SCHEDULE_RATIBA').canCheckSms, isFalse);
  });
}
