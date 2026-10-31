import 'wallet_action.dart';

/// One line of the guided steps. [copy] is a value the customer may want to copy and paste.
class HandoffStep {
  const HandoffStep(this.text, {this.copy, this.copyLabel});

  final String text;
  final String? copy;
  final String? copyLabel;
}

class HandoffPlan {
  const HandoffPlan({required this.dialUri, required this.steps, required this.prefilled});

  /// A tel: link that opens the dialer with a code typed in. The customer still presses call.
  final String dialUri;
  final List<HandoffStep> steps;

  /// True when a configured template filled in the details. False means only the main M-Pesa menu is dialled.
  final bool prefilled;
}

final RegExp _ussdSafe = RegExp(r'^[0-9*#]+$');
final RegExp _placeholder = RegExp(r'\{(amount|phone|paybill|account|till)\}');

/// 254712345678 becomes 0712345678. Anything else is returned unchanged.
String toLocalPhone(String phone) {
  final digits = phone.replaceAll(RegExp(r'\D'), '');
  if (digits.length == 12 && digits.startsWith('254')) return '0${digits.substring(3)}';
  return digits.isEmpty ? phone : digits;
}

String telUri(String ussd) => 'tel:${ussd.replaceAll('#', '%23')}';

/// Works out what to show and dial. [contactPhone] is the number the customer picked from their contacts
/// on this phone. It is used here and never sent to the server.
HandoffPlan buildHandoff(WalletAction action, HandoffConfig config, {String? contactPhone}) {
  final d = action.details;
  final type = action.type == 'SCHEDULE_RATIBA' ? (d['target'] ?? action.type) : action.type;

  String? phone;
  switch (type) {
    case 'SEND_MONEY':
      phone = contactPhone ?? d['recipientPhone'];
      break;
    case 'PAY_POCHI':
      phone = d['pochiPhone'];
      break;
    case 'BUY_AIRTIME':
      phone = d['airtimePhone'];
      break;
  }
  final localPhone = phone == null ? null : toLocalPhone(phone);
  final amount = action.amount.toString();

  final values = <String, String?>{
    'amount': amount,
    'phone': localPhone,
    'paybill': d['paybill'],
    'account': d['account'],
    'till': d['till'],
  };

  final steps = <HandoffStep>[];
  switch (action.type) {
    case 'SEND_MONEY':
      steps
        ..add(const HandoffStep('In M-Pesa, choose the option for sending money.'))
        ..add(HandoffStep('Enter the number.', copy: localPhone, copyLabel: 'number'))
        ..add(HandoffStep('Enter the amount: KES $amount.', copy: amount, copyLabel: 'amount'));
      break;
    case 'PAY_BILL':
      steps
        ..add(const HandoffStep('In M-Pesa, choose Lipa na M-Pesa, then Paybill.'))
        ..add(HandoffStep('Enter the Paybill number.', copy: d['paybill'], copyLabel: 'Paybill'))
        ..add(HandoffStep('Enter the account number.', copy: d['account'], copyLabel: 'account'))
        ..add(HandoffStep('Enter the amount: KES $amount.', copy: amount, copyLabel: 'amount'));
      break;
    case 'PAY_TILL':
      steps
        ..add(const HandoffStep('In M-Pesa, choose Lipa na M-Pesa, then Buy Goods and Services.'))
        ..add(HandoffStep('Enter the Till number.', copy: d['till'], copyLabel: 'Till'))
        ..add(HandoffStep('Enter the amount: KES $amount.', copy: amount, copyLabel: 'amount'));
      break;
    case 'PAY_POCHI':
      steps
        ..add(const HandoffStep('In M-Pesa, choose the option for Pochi la Biashara.'))
        ..add(HandoffStep('Enter the number.', copy: localPhone, copyLabel: 'number'))
        ..add(HandoffStep('Enter the amount: KES $amount.', copy: amount, copyLabel: 'amount'));
      break;
    case 'BUY_AIRTIME':
      steps
        ..add(const HandoffStep('In M-Pesa, choose the option for airtime.'))
        ..add(localPhone == null
            ? const HandoffStep('Choose your own number.')
            : HandoffStep('Enter the number.', copy: localPhone, copyLabel: 'number'))
        ..add(HandoffStep('Enter the amount: KES $amount.', copy: amount, copyLabel: 'amount'));
      break;
    case 'SCHEDULE_RATIBA':
      steps
        ..add(const HandoffStep('In M-Pesa, open Ratiba and choose to set up a new one.'))
        ..add(HandoffStep(action.summary));
      break;
  }
  steps.add(const HandoffStep(
      'Check the name M-Pesa shows. Only then enter your M-Pesa PIN, inside M-Pesa. Never type it anywhere else.'));

  final template = config.ussdTemplates[action.type];
  if (template != null && template.isNotEmpty) {
    final filled = _fill(template, values);
    // Only digits, star and hash may ever be dialled. Anything else means the template needs values we cannot put in a code.
    if (filled != null && _ussdSafe.hasMatch(filled)) {
      return HandoffPlan(dialUri: telUri(filled), steps: steps, prefilled: true);
    }
  }
  return HandoffPlan(dialUri: telUri(config.ussdMain), steps: steps, prefilled: false);
}

String? _fill(String template, Map<String, String?> values) {
  var missing = false;
  final out = template.replaceAllMapped(_placeholder, (m) {
    final v = values[m.group(1)];
    if (v == null || v.isEmpty) {
      missing = true;
      return '';
    }
    return v;
  });
  return missing ? null : out;
}
