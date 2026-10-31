import 'package:flutter_test/flutter_test.dart';
import 'package:ganji_app/models/mpesa_sms.dart';

// These sample messages are from memory of the usual M-Pesa wording. Replace or add real ones from your own phone
// (remove names and numbers) before relying on the parser.
void main() {
  final at = DateTime(2026, 10, 29, 21, 15);

  test('a send money message', () {
    final sms = parseMpesaSms(
        'SGL1234567 Confirmed. Ksh500.00 sent to JOHN DOE 0712345678 on 29/10/26 at 9:15 PM. New M-PESA balance is Ksh1,200.00. Transaction cost, Ksh5.00.',
        at)!;

    expect(sms.code, 'SGL1234567');
    expect(sms.kind, SmsKind.sent);
    expect(sms.amount, 500);
    expect(sms.payee, 'JOHN DOE 0712345678');
    expect(sms.account, isNull);
  });

  test('a till payment', () {
    final sms = parseMpesaSms(
        'SGL1234568 Confirmed. Ksh300.00 paid to SHOP NAME. on 29/10/26 at 9:15 PM.New M-PESA balance is Ksh900.00. Transaction cost, Ksh0.00.', at)!;

    expect(sms.kind, SmsKind.paid);
    expect(sms.amount, 300);
    expect(sms.payee, 'SHOP NAME');
  });

  test('a Paybill payment keeps the account and thousands separators', () {
    final sms = parseMpesaSms(
        'SGL1234569 Confirmed. Ksh1,200.00 sent to KPLC PREPAID for account 123456 on 29/10/26 at 9:15 PM. New M-PESA balance is Ksh100.00.', at)!;

    expect(sms.kind, SmsKind.bill);
    expect(sms.amount, 1200);
    expect(sms.payee, 'KPLC PREPAID');
    expect(sms.account, '123456');
  });

  test('airtime', () {
    final sms = parseMpesaSms('SGL123456A Confirmed.You bought Ksh50.00 of airtime on 29/10/26 at 9:15 PM.New M-PESA balance is Ksh850.00.', at)!;

    expect(sms.kind, SmsKind.airtime);
    expect(sms.amount, 50);
  });

  test('messages that are not a payment we made are ignored', () {
    expect(parseMpesaSms('SGL1234570 Confirmed.You have received Ksh500.00 from JANE 0722000111 on 29/10/26 at 9:15 PM.', at), isNull);
    expect(parseMpesaSms('SGL1234571 Confirmed. Reversal of transaction SGL1234567 Ksh500.00 sent to JOHN on 29/10/26', at), isNull);
    expect(parseMpesaSms('Your M-PESA balance is Ksh100.00', at), isNull);
    expect(parseMpesaSms('Win Ksh500.00 sent to you on 29/10/26, call us', at), isNull);
    expect(parseMpesaSms('', at), isNull);
  });

  test('an amount with cents cannot match a whole shilling request, so it is not offered', () {
    expect(parseMpesaSms('SGL1234567 Confirmed. Ksh500.50 sent to JOHN 0712345678 on 29/10/26 at 9:15 PM.', at), isNull);
  });

  test('odd characters are removed before anything is sent', () {
    final sms = parseMpesaSms('SGL1234567 Confirmed. Ksh500.00 sent to JOHN <b>DOE</b> 0712345678 on 29/10/26 at 9:15 PM.', at)!;

    expect(sms.payee, isNot(contains('<')));
    expect(sms.payee, contains('DOE'));
  });

  test('a contact request can drop the payee', () {
    final sms = parseMpesaSms('SGL1234567 Confirmed. Ksh500.00 sent to MAMA 0712345678 on 29/10/26 at 9:15 PM.', at)!;

    expect(sms.withoutPayee().payee, isNull);
    expect(sms.withoutPayee().code, 'SGL1234567');
  });
}
