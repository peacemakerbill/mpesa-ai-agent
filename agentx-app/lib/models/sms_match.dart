import 'handoff.dart';
import 'mpesa_sms.dart';
import 'wallet_action.dart';

/// A message as read from the phone's inbox.
class RawSms {
  const RawSms({required this.address, required this.body, required this.date});

  final String? address;
  final String body;
  final DateTime? date;
}

class SmsCheck {
  const SmsCheck.found(MpesaSms this.sms) : problem = null;
  const SmsCheck.none() : sms = null, problem = null;
  const SmsCheck.problem(String this.problem) : sms = null;

  final MpesaSms? sms;
  final String? problem;

  bool get isFound => sms != null;
}

/// How early a message may be before the request was approved. Phone and server clocks differ a little.
const Duration smsEarlier = Duration(minutes: 2);

Set<SmsKind> _kindsFor(String type) => switch (type) {
      'SEND_MONEY' => {SmsKind.sent},
      'PAY_POCHI' => {SmsKind.sent, SmsKind.paid},
      'PAY_TILL' => {SmsKind.paid},
      'PAY_BILL' => {SmsKind.bill},
      'BUY_AIRTIME' => {SmsKind.airtime},
      _ => <SmsKind>{},
    };

/// Looks through the messages for the one that fits this request, on the phone, before anything is sent.
/// Only messages from the M-Pesa sender are considered. [contactPhone] is the number the customer picked from
/// their contacts, and is compared here and never sent to the server.
SmsCheck findMatchingSms(WalletAction action, List<RawSms> inbox, {String? contactPhone, DateTime? now}) {
  final kinds = _kindsFor(action.type);
  if (kinds.isEmpty) return const SmsCheck.none();
  final since = (action.approvedAt ?? (now ?? DateTime.now()).subtract(const Duration(minutes: 30))).subtract(smsEarlier);

  MpesaSms? best;
  for (final raw in inbox) {
    if ((raw.address ?? '').toUpperCase() != 'MPESA' || raw.date == null) continue;
    final sms = parseMpesaSms(raw.body, raw.date!);
    if (sms == null || sms.amount != action.amount || !kinds.contains(sms.kind)) continue;
    if (sms.receivedAt.isBefore(since)) continue;
    if (best == null || sms.receivedAt.isAfter(best.receivedAt)) best = sms;
  }
  if (best == null) return const SmsCheck.none();

  // For a contact the server never knew the number, so the check happens here.
  if (action.needsContact) {
    final seen = _lastNine(best.payee);
    final expected = contactPhone == null ? null : _lastNine(contactPhone);
    if (seen != null && expected != null && seen != expected) {
      return const SmsCheck.problem(
          'The M-Pesa message shows a different number from the contact you chose. If money may have gone to the wrong number, tell the assistant right away.');
    }
    return SmsCheck.found(best.withoutPayee());
  }
  return SmsCheck.found(best);
}

String? _lastNine(String? text) {
  if (text == null) return null;
  for (final m in RegExp(r'\d[\d ]{7,}\d').allMatches(text)) {
    final digits = m.group(0)!.replaceAll(' ', '');
    if (digits.length >= 9) return digits.substring(digits.length - 9);
  }
  return null;
}

/// Exposed for the screen to show what was found, with the number in the local format.
String describeSms(MpesaSms sms) {
  final who = sms.payee == null ? '' : ' to ${sms.payee}';
  return 'KES ${sms.amount}$who (${sms.code})';
}

String localNumber(String phone) => toLocalPhone(phone);
