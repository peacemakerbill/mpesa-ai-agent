/// Picks apart an M-Pesa confirmation message on the phone. Only the parsed fields ever leave the phone.
///
/// IMPORTANT: the wording below was written from memory of common M-Pesa messages and has NOT been checked against
/// current real messages. Collect a few real ones for each kind (send, Paybill, Till, airtime), put them in
/// test/mpesa_sms_test.dart, and fix the patterns until they pass. Safaricom can change the wording at any time,
/// and when a message does not parse the app simply finds nothing and the customer reports manually.
enum SmsKind { sent, paid, bill, airtime }

class MpesaSms {
  const MpesaSms({
    required this.code,
    required this.kind,
    required this.amount,
    required this.receivedAt,
    this.payee,
    this.account,
  });

  final String code;
  final SmsKind kind;

  /// Whole shillings.
  final int amount;
  final DateTime receivedAt;

  /// The name and number shown after "sent to" or "paid to".
  final String? payee;

  /// The account number of a Paybill payment.
  final String? account;

  MpesaSms withoutPayee() =>
      MpesaSms(code: code, kind: kind, amount: amount, receivedAt: receivedAt, payee: null, account: account);

  String get kindName => switch (kind) {
        SmsKind.sent => 'SENT',
        SmsKind.paid => 'PAID',
        SmsKind.bill => 'BILL',
        SmsKind.airtime => 'AIRTIME',
      };
}

final RegExp _code = RegExp(r'^\s*([A-Z0-9]{10})\s+Confirmed');
final RegExp _amount = RegExp(r'Ksh\s?([\d,]+)(?:\.(\d{1,2}))?', caseSensitive: false);
final RegExp _when = RegExp(r'on\s+\d{1,2}/\d{1,2}/\d{2,4}');
final RegExp _bill = RegExp(r'sent to (.+?) for account (.+?) on\s+\d{1,2}/\d{1,2}/\d{2,4}', caseSensitive: false);
final RegExp _paid = RegExp(r'paid to (.+?)\.?\s+on\s+\d{1,2}/\d{1,2}/\d{2,4}', caseSensitive: false);
final RegExp _sent = RegExp(r'sent to (.+?)\s+on\s+\d{1,2}/\d{1,2}/\d{2,4}', caseSensitive: false);
final RegExp _airtime = RegExp(r'of airtime(?: for (\d[\d ]*\d))?', caseSensitive: false);
final RegExp _reversal = RegExp(r'revers', caseSensitive: false);
final RegExp _badPayee = RegExp(r"[^\p{L}\p{N} .,'&()+/\-]", unicode: true);
final RegExp _badAccount = RegExp(r'[^\p{L}\p{N} ._/\-]', unicode: true);

/// Returns null for anything that is not a confirmed payment this app understands: received money, balances,
/// reversals, Fuliza messages, amounts with cents, or wording it does not recognise.
MpesaSms? parseMpesaSms(String body, DateTime receivedAt) {
  final text = body.replaceAll(RegExp(r'\s+'), ' ').trim();
  final code = _code.firstMatch(text)?.group(1);
  if (code == null || _reversal.hasMatch(text)) return null;

  final a = _amount.firstMatch(text);
  if (a == null) return null;
  final cents = (a.group(2) ?? '0').padRight(2, '0');
  if (int.tryParse(cents) != 0) return null;
  final amount = int.tryParse(a.group(1)!.replaceAll(',', ''));
  if (amount == null || amount <= 0) return null;

  MpesaSms make(SmsKind kind, {String? payee, String? account}) => MpesaSms(
        code: code,
        kind: kind,
        amount: amount,
        receivedAt: receivedAt,
        payee: _clean(payee, _badPayee, 60),
        account: _clean(account, _badAccount, 40),
      );

  final airtime = _airtime.firstMatch(text);
  if (airtime != null && !_sent.hasMatch(text)) return make(SmsKind.airtime, payee: airtime.group(1));
  final bill = _bill.firstMatch(text);
  if (bill != null) return make(SmsKind.bill, payee: bill.group(1), account: bill.group(2));
  final paid = _paid.firstMatch(text);
  if (paid != null) return make(SmsKind.paid, payee: paid.group(1));
  final sent = _sent.firstMatch(text);
  if (sent != null && _when.hasMatch(text)) return make(SmsKind.sent, payee: sent.group(1));
  return null;
}

String? _clean(String? value, RegExp bad, int max) {
  if (value == null) return null;
  var v = value.replaceAll(bad, '').trim();
  if (v.length > max) v = v.substring(0, max).trim();
  return v.isEmpty ? null : v;
}
