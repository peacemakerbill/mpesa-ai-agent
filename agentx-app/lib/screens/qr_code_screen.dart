import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:open_filex/open_filex.dart';
import 'package:path_provider/path_provider.dart';

import '../api/api_client.dart';
import '../app_state.dart';
import '../layout/responsive.dart';
import '../models/bonga.dart';
import '../models/business_account.dart';
import '../models/business_operation.dart';
import '../widgets/ui.dart';

/// Saves the QR picture and opens it in a viewer, from where it can be shared or printed.
typedef QrSaver = Future<void> Function(List<int> png, String name);

Future<void> saveAndOpenQr(List<int> png, String name) async {
  final dir = await getApplicationDocumentsDirectory();
  final file = File('${dir.path}/$name.png');
  await file.writeAsBytes(png, flush: true);
  final result = await OpenFilex.open(file.path);
  if (result.type != ResultType.done) {
    throw Exception(result.type == ResultType.noAppToOpen ? 'No app on this device can open a picture.' : 'The picture could not be opened.');
  }
}

/// Who a QR code pays.
enum QrTarget { account, till, paybill, phone }

extension QrTargetX on QrTarget {
  String get label => switch (this) {
        QrTarget.account => 'This account',
        QrTarget.till => 'A Till',
        QrTarget.paybill => 'A Paybill',
        QrTarget.phone => 'A phone number',
      };

  /// The word the server expects.
  String get wire => name;
}

/// True when the account has what Daraja's Dynamic QR needs: the consumer key and secret.
bool canMakeQr(BusinessAccount a) => a.capabilities.any((c) => c.code == 'DYNAMIC_QR' && c.ready);

/// Makes a QR code that a payer scans with the My Safaricom or M-PESA app, for a saved account or for any Till, Paybill or
/// phone number. Safaricom makes the picture. It is shown here once and is not kept, so make a new one when needed.
class QrCodeScreen extends StatefulWidget {
  const QrCodeScreen({super.key, required this.state, required this.accounts, this.initialAccountId, this.save = saveAndOpenQr});

  final AppState state;
  final List<BusinessAccount> accounts;
  final String? initialAccountId;
  final QrSaver save;

  @override
  State<QrCodeScreen> createState() => _QrCodeScreenState();
}

class _QrCodeScreenState extends State<QrCodeScreen> {
  final _amount = TextEditingController();
  final _number = TextEditingController();
  final _reference = TextEditingController();
  final _name = TextEditingController();
  late final List<BusinessAccount> _usable;
  BusinessAccount? _account;
  QrTarget _target = QrTarget.account;
  bool _busy = false;
  String? _error;
  BusinessOperation? _made;

  @override
  void initState() {
    super.initState();
    _usable = widget.accounts.where(canMakeQr).toList();
    for (final a in _usable) {
      if (a.id == widget.initialAccountId) _account = a;
    }
    _account ??= _usable.isEmpty ? null : _usable.first;
  }

  @override
  void dispose() {
    _amount.dispose();
    _number.dispose();
    _reference.dispose();
    _name.dispose();
    super.dispose();
  }

  String? _problem() {
    if (_account == null) return 'Choose the account that makes the code.';
    final amount = int.tryParse(_amount.text.trim().replaceAll(',', ''));
    if (amount == null || amount < 1) return 'Enter the amount in whole shillings.';
    final number = _number.text.replaceAll(RegExp(r'[\s-]'), '');
    if (_target == QrTarget.till || _target == QrTarget.paybill) {
      if (!RegExp(r'^\d{5,7}$').hasMatch(number)) return 'The ${_target == QrTarget.till ? 'Till' : 'Paybill'} number must be 5 to 7 digits.';
    }
    if (_target == QrTarget.phone && number.length < 9) return 'Enter the phone number.';
    if (_target == QrTarget.paybill && _reference.text.trim().isEmpty) return 'A Paybill code needs the account number to pay into.';
    return null;
  }

  Future<void> _make() async {
    final problem = _problem();
    if (problem != null) {
      setState(() => _error = problem);
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final op = await widget.state.api.createQrCode(
        account: _account!.id,
        amount: int.parse(_amount.text.trim().replaceAll(',', '')),
        reference: _reference.text,
        merchantName: _name.text,
        payTo: _target.wire,
        payToNumber: _target == QrTarget.account ? '' : _number.text,
      );
      if (!mounted) return;
      setState(() {
        _busy = false;
        if (op.qrBytes == null) {
          _error = op.failure.isNotEmpty ? op.failure : 'Safaricom did not return a QR code.';
        } else {
          _made = op;
        }
      });
    } on ApiException catch (e) {
      if (e.unauthorized) await widget.state.sessionExpired();
      if (!mounted) return;
      setState(() {
        _busy = false;
        _error = e.message;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _busy = false;
        _error = 'The QR code could not be made. Check your connection and try again.';
      });
    }
  }

  Future<void> _saveImage(BusinessOperation op) async {
    final bytes = op.qrBytes;
    if (bytes == null) return;
    try {
      await widget.save(bytes, 'agentx-qr-${DateTime.now().millisecondsSinceEpoch}');
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('$e'.replaceFirst('Exception: ', ''))));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('QR code to pay')),
      body: SafeArea(top: false, child: CenteredList(maxWidth: 560, children: _made == null ? _form(context) : _result(context, _made!))),
    );
  }

  List<Widget> _form(BuildContext context) {
    final text = Theme.of(context).textTheme;
    if (_usable.isEmpty) {
      return [
        const Callout('A QR code needs one saved business account with its consumer key and secret. Add one under Business accounts first.', tone: Tone.warn),
      ];
    }
    return [
      Text('Safaricom makes the code. The payer scans it with the My Safaricom or M-PESA app and pays by M-PESA.', style: text.bodyMedium),
      const SizedBox(height: 16),
      Text('Account that makes the code', style: text.titleSmall),
      const SizedBox(height: 8),
      Wrap(spacing: 8, runSpacing: 8, children: [
        for (final a in _usable)
          ChoiceChip(label: Text(a.label), selected: _account?.id == a.id, onSelected: (_) => setState(() => _account = a)),
      ]),
      const SizedBox(height: 16),
      Text('Who is paid', style: text.titleSmall),
      const SizedBox(height: 8),
      Wrap(spacing: 8, runSpacing: 8, children: [
        for (final t in QrTarget.values) ChoiceChip(label: Text(t.label), selected: _target == t, onSelected: (_) => setState(() => _target = t)),
      ]),
      if (_target == QrTarget.account && _account != null) ...[
        const SizedBox(height: 8),
        Text(_account!.headline, style: text.bodySmall),
      ],
      const SizedBox(height: 12),
      if (_target != QrTarget.account) ...[
        TextField(
          controller: _number,
          keyboardType: TextInputType.phone,
          decoration: InputDecoration(
            labelText: switch (_target) {
              QrTarget.till => 'Till number',
              QrTarget.paybill => 'Paybill number',
              _ => 'Phone number',
            },
          ),
        ),
        const SizedBox(height: 12),
      ],
      TextField(
        controller: _amount,
        keyboardType: TextInputType.number,
        inputFormatters: [FilteringTextInputFormatter.allow(RegExp(r'[0-9,]'))],
        decoration: const InputDecoration(labelText: 'Amount (KES)'),
      ),
      const SizedBox(height: 12),
      TextField(
        controller: _reference,
        maxLength: 12,
        decoration: InputDecoration(
          labelText: _target == QrTarget.paybill ? 'Account number to pay into' : 'Reference (optional)',
          counterText: '',
        ),
      ),
      const SizedBox(height: 12),
      TextField(
        controller: _name,
        maxLength: 26,
        decoration: const InputDecoration(labelText: 'Name shown on the code (optional)', counterText: ''),
      ),
      if (_error != null) ...[const SizedBox(height: 12), Callout(_error!, tone: Tone.bad)],
      const SizedBox(height: 16),
      FilledButton.icon(
        onPressed: _busy ? null : _make,
        icon: _busy ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2)) : const Icon(Icons.qr_code_2),
        label: Text(_busy ? 'Making the code' : 'Make QR code'),
      ),
    ];
  }

  List<Widget> _result(BuildContext context, BusinessOperation op) {
    final text = Theme.of(context).textTheme;
    final bytes = op.qrBytes!;
    final till = op.qrTill;
    return [
      Panel(
        child: Column(children: [
          Container(
            color: Colors.white,
            padding: const EdgeInsets.all(12),
            child: Image.memory(bytes, width: 260, height: 260, fit: BoxFit.contain, gaplessPlayback: true, semanticLabel: 'QR code to pay'),
          ),
          const SizedBox(height: 12),
          Text(op.summary, style: text.bodyMedium, textAlign: TextAlign.center),
        ]),
      ),
      const SizedBox(height: 12),
      const Callout('Ask the payer to open the My Safaricom or M-PESA app, choose Scan QR and scan this code. It is not kept, so make a new one when you need it again.',
          tone: Tone.info),
      const SizedBox(height: 12),
      FilledButton.icon(onPressed: () => _saveImage(op), icon: const Icon(Icons.download_outlined), label: const Text('Save or share the picture')),
      const SizedBox(height: 8),
      OutlinedButton.icon(
        onPressed: () => setState(() {
          _made = null;
          _error = null;
        }),
        icon: const Icon(Icons.add),
        label: const Text('Make another'),
      ),
      if (till.isNotEmpty) ...[
        const SizedBox(height: 20),
        _BongaCard(till: till, amount: op.amount),
      ],
    ];
  }
}

/// Lipa na Bonga cannot be asked for or charged by a business, so this only explains how the payer does it themselves.
class _BongaCard extends StatelessWidget {
  const _BongaCard({required this.till, required this.amount});

  final String till;
  final int amount;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final steps = bongaSteps(till, amount: amount);
    return Panel(
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text('Prefer to pay with Bonga Points?', style: text.titleSmall),
        const SizedBox(height: 4),
        Text('Safaricom has no way for a business to charge Bonga Points. The payer does it on their own phone:', style: text.bodySmall),
        const SizedBox(height: 8),
        for (var i = 0; i < steps.length; i++)
          Padding(padding: const EdgeInsets.only(bottom: 4), child: Text('${i + 1}. ${steps[i]}', style: text.bodyMedium)),
        const SizedBox(height: 4),
        TextButton.icon(
          onPressed: () async {
            await Clipboard.setData(ClipboardData(text: steps.join('\n')));
            if (context.mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Steps copied.')));
          },
          icon: const Icon(Icons.copy, size: 18),
          label: const Text('Copy the steps'),
        ),
      ]),
    );
  }
}
