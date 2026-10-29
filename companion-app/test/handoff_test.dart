import 'package:flutter_test/flutter_test.dart';
import 'package:mpesa_companion/models/handoff.dart';
import 'package:mpesa_companion/models/wallet_action.dart';

WalletAction action(String type, Map<String, String> details, {int amount = 300}) => WalletAction(
      id: '44444444-4444-4444-4444-444444444444',
      type: type,
      status: 'APPROVED',
      amount: amount,
      summary: 'summary',
      details: details,
      warnings: const [],
      needsContact: false,
    );

HandoffConfig config([Map<String, String> templates = const {}]) => HandoffConfig(
      ussdMain: '*334#',
      mpesaAndroidPackage: '',
      mpesaIosUrl: '',
      ussdTemplates: templates,
    );

void main() {
  test('phone numbers are shown in the local format', () {
    expect(toLocalPhone('254712345678'), '0712345678');
    expect(toLocalPhone('+254 712 345 678'), '0712345678');
    expect(toLocalPhone('0712345678'), '0712345678');
  });

  test('the # in a code is encoded so the dialler keeps it', () {
    expect(telUri('*334#'), 'tel:*334%23');
  });

  test('without a template only the main M-Pesa menu is dialled', () {
    final plan = buildHandoff(action('PAY_TILL', {'till': '123456'}), config());

    expect(plan.prefilled, isFalse);
    expect(plan.dialUri, 'tel:*334%23');
    expect(plan.steps.map((s) => s.copy), contains('123456'));
    expect(plan.steps.map((s) => s.copy), contains('300'));
  });

  test('a configured template is filled with the details', () {
    final plan = buildHandoff(action('PAY_TILL', {'till': '123456'}), config({'PAY_TILL': '*334*5*{till}*{amount}#'}));

    expect(plan.prefilled, isTrue);
    expect(plan.dialUri, 'tel:*334*5*123456*300%23');
  });

  test('a template that would need letters is never dialled', () {
    final plan = buildHandoff(
      action('PAY_BILL', {'paybill': '888880', 'account': 'ROOM 12'}),
      config({'PAY_BILL': '*334*{paybill}*{account}*{amount}#'}),
    );

    expect(plan.prefilled, isFalse);
    expect(plan.dialUri, 'tel:*334%23');
  });

  test('a numeric account is allowed', () {
    final plan = buildHandoff(
      action('PAY_BILL', {'paybill': '888880', 'account': '12345'}),
      config({'PAY_BILL': '*334*{paybill}*{account}*{amount}#'}),
    );

    expect(plan.prefilled, isTrue);
  });

  test('a template with a value we do not have falls back to guided steps', () {
    final plan = buildHandoff(action('BUY_AIRTIME', {}), config({'BUY_AIRTIME': '*140*{amount}*{phone}#'}));

    expect(plan.prefilled, isFalse);
    expect(plan.steps.map((s) => s.text), contains('Choose your own number.'));
  });

  test('a contact picked on the phone fills in the number', () {
    final a = action('SEND_MONEY', {'contactName': 'Mama'});
    final plan = buildHandoff(a, config({'SEND_MONEY': '*334*1*{phone}*{amount}#'}), contactPhone: '+254722000111');

    expect(plan.dialUri, 'tel:*334*1*0722000111*300%23');
    expect(plan.steps.map((s) => s.copy), contains('0722000111'));
  });

  test('a Ratiba uses the target type to find the number', () {
    final a = action('SCHEDULE_RATIBA', {'target': 'SEND_MONEY', 'recipientPhone': '254722000111'});
    final plan = buildHandoff(a, config());

    expect(plan.steps.first.text, contains('Ratiba'));
    expect(plan.prefilled, isFalse);
  });

  test('every plan ends by telling the customer to type the PIN only inside M-Pesa', () {
    for (final type in ['SEND_MONEY', 'PAY_BILL', 'PAY_TILL', 'PAY_POCHI', 'BUY_AIRTIME', 'SCHEDULE_RATIBA']) {
      final plan = buildHandoff(action(type, {'target': 'PAY_TILL'}), config());
      expect(plan.steps.last.text, contains('inside M-Pesa'), reason: type);
    }
  });
}
