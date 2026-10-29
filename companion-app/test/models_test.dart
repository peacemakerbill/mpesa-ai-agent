import 'package:flutter_test/flutter_test.dart';
import 'package:mpesa_companion/models/wallet_action.dart';

void main() {
  test('a request is read from the server JSON and unknown fields are ignored', () {
    final a = WalletAction.fromJson({
      'id': '44444444-4444-4444-4444-444444444444',
      'type': 'PAY_TILL',
      'status': 'CREATED',
      'amount': 300,
      'summary': 'Pay KES 300 to Till 123456',
      'details': {'till': '123456'},
      'warnings': ['Check the name'],
      'needsContact': false,
      'receipt': null,
      'expiresAt': '2026-10-29T10:10:00Z',
      'somethingNew': true,
    });

    expect(a.amount, 300);
    expect(a.details['till'], '123456');
    expect(a.warnings, ['Check the name']);
    expect(a.awaitingApproval, isTrue);
    expect(a.isOpen, isTrue);
    expect(a.expiresAt, isNotNull);
  });

  test('only approved and handed off requests can be finished in M-Pesa', () {
    WalletAction withStatus(String s) => WalletAction.fromJson({
          'id': 'x', 'type': 'PAY_TILL', 'status': s, 'amount': 1, 'summary': '', 'details': {}, 'warnings': [], 'needsContact': false,
        });

    expect(withStatus('APPROVED').canFinishInMpesa, isTrue);
    expect(withStatus('HANDED_OFF').canFinishInMpesa, isTrue);
    expect(withStatus('CREATED').canFinishInMpesa, isFalse);
    expect(withStatus('DONE_REPORTED').isOpen, isFalse);
    expect(withStatus('EXPIRED').isOpen, isFalse);
  });

  test('the hand-off settings ignore blank templates and keep the main code', () {
    final c = HandoffConfig.fromJson({
      'ussdMain': '  ',
      'mpesaAndroidPackage': 'com.example.mpesa',
      'ussdTemplates': {'PAY_TILL': '*1*{till}#', 'PAY_BILL': ''},
    });

    expect(c.ussdMain, '*334#');
    expect(c.ussdTemplates.keys, ['PAY_TILL']);
    expect(c.mpesaAndroidPackage, 'com.example.mpesa');
  });
}
