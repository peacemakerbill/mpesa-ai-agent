import 'dart:async';

import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../app_state.dart';
import '../layout/responsive.dart';
import '../models/business_operation.dart';
import '../services/approval.dart';
import '../theme/app_theme.dart';
import '../widgets/ui.dart';

Tone toneForOperation(OperationStage stage) {
  switch (stage) {
    case OperationStage.done:
      return Tone.good;
    case OperationStage.waiting:
      return Tone.warn;
    case OperationStage.sent:
      return Tone.info;
    case OperationStage.failed:
    case OperationStage.declined:
    case OperationStage.expired:
      return Tone.bad;
    case OperationStage.unclear:
      return Tone.warn;
  }
}

String clockTime(DateTime t) => '${t.hour.toString().padLeft(2, '0')}:${t.minute.toString().padLeft(2, '0')}';

/// One request that the assistant prepared on a business account. Anything that sends money out waits here until the
/// owner approves it with fingerprint, face or phone lock. Nothing is sent to Safaricom before that.
class BusinessOperationScreen extends StatefulWidget {
  const BusinessOperationScreen({super.key, required this.state, required this.operationId});

  final AppState state;
  final String operationId;

  @override
  State<BusinessOperationScreen> createState() => _BusinessOperationScreenState();
}

class _BusinessOperationScreenState extends State<BusinessOperationScreen> {
  BusinessOperation? _op;
  String? _error;
  bool _loading = true;
  bool _busy = false;
  int? _lastStatus;
  Timer? _poll;
  int _polls = 0;

  AppState get s => widget.state;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _poll?.cancel();
    super.dispose();
  }

  Future<T?> _guard<T>(Future<T> Function() call) async {
    setState(() {
      _busy = true;
      _error = null;
      _lastStatus = null;
    });
    try {
      return await call();
    } on ApiException catch (e) {
      _lastStatus = e.status;
      if (e.unauthorized) {
        await s.sessionExpired();
        if (mounted) Navigator.of(context).popUntil((r) => r.isFirst);
        return null;
      }
      if (mounted) setState(() => _error = e.message);
      return null;
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _load() async {
    final op = await _guard(() => s.api.businessOperation(widget.operationId));
    if (!mounted) return;
    setState(() {
      _op = op ?? _op;
      _loading = false;
    });
    _watch();
  }

  /// After the request is sent, Safaricom answers on its own a few seconds later. Look now and then, for about two minutes.
  void _watch() {
    _poll?.cancel();
    if (_op?.stage != OperationStage.sent) return;
    _poll = Timer.periodic(const Duration(seconds: 5), (t) async {
      _polls++;
      if (_polls > 24 || !mounted) {
        t.cancel();
        return;
      }
      try {
        final op = await s.api.businessOperation(widget.operationId);
        if (!mounted) return;
        setState(() => _op = op);
        if (op.stage != OperationStage.sent) t.cancel();
      } on Exception {
        // try again at the next tick
      }
    });
  }

  Future<void> _approve() async {
    final op = _op!;
    final result = await s.approval.ask('Confirm that you want to: ${op.summary}');
    if (!mounted) return;
    switch (result) {
      case ApprovalResult.cancelled:
        setState(() => _error = 'Not approved. Nothing was sent.');
        return;
      case ApprovalResult.notAvailable:
        setState(() => _error = 'This phone has no fingerprint, face or screen lock set up. Set one in your phone settings, then try again.');
        return;
      case ApprovalResult.approvedBiometric:
      case ApprovalResult.approvedDeviceLock:
        break;
    }
    final updated = await _guard(() => s.api.approveBusinessOperation(op.id, biometric: result == ApprovalResult.approvedBiometric));
    if (!mounted) return;
    if (updated != null) {
      setState(() => _op = updated);
      _polls = 0;
      _watch();
    } else if (_lastStatus == 0) {
      // The server sends the request to Safaricom inside that call. If no answer came back it may still have gone out,
      // so look at the real state instead of letting the person approve again.
      setState(() => _error = 'No answer yet. It may have been sent. Check the status below before doing anything else.');
      await _refresh();
    }
  }

  Future<void> _refresh() async {
    try {
      final op = await s.api.businessOperation(widget.operationId);
      if (mounted) setState(() => _op = op);
    } on Exception {
      // keep what is on screen
    }
  }

  Future<void> _decline() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (c) => AppDialog(
        title: const Text('Decline this request?'),
        content: const Text('Nothing will be sent to Safaricom.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('Back')),
          TextButton(onPressed: () => Navigator.pop(c, true), child: const Text('Decline')),
        ],
      ),
    );
    if (ok != true) return;
    final updated = await _guard(() => s.api.declineBusinessOperation(_op!.id));
    if (updated != null && mounted) setState(() => _op = updated);
  }

  @override
  Widget build(BuildContext context) {
    final op = _op;
    return Scaffold(
      appBar: AppBar(
        title: Text(op == null || op.typeLabel.isEmpty ? 'Business request' : op.typeLabel),
        actions: [IconButton(onPressed: _busy ? null : _load, icon: const Icon(Icons.refresh), tooltip: 'Refresh')],
      ),
      body: SafeArea(top: false, child: _body(context)),
    );
  }

  Widget _body(BuildContext context) {
    final op = _op;
    if (op == null) {
      if (_loading) return const Center(child: CircularProgressIndicator());
      return CenteredList(maxWidth: 560, top: 20, children: [
        Callout(_error ?? 'This request could not be loaded.', tone: Tone.bad),
        const SizedBox(height: 12),
        FilledButton(onPressed: _load, child: const Text('Try again')),
      ]);
    }
    final text = Theme.of(context).textTheme;
    final t = context.tk;
    final decide = op.canDecide(DateTime.now());
    return CenteredList(maxWidth: 560, top: 16, children: [
      Row(children: [
        Expanded(child: Text('From ${op.accountLabel}', style: text.titleMedium)),
        StatusTag(op.stageLabel, tone: toneForOperation(op.stage)),
      ]),
      const SizedBox(height: 16),
      if (op.amount > 0) AmountHero(amount: op.amount, summary: op.summary) else Text(op.summary, style: text.bodyLarge?.copyWith(color: t.p.ink2)),
      const SizedBox(height: 16),
      if (_error != null) ...[Callout(_error!, tone: Tone.bad), const SizedBox(height: 12)],
      if (decide) ...[
        Callout(
          op.movesMoneyOut
              ? 'Money leaves your business account as soon as you approve. Check the number and the amount first.'
              : 'This is sent to Safaricom as soon as you approve.',
          tone: Tone.warn,
        ),
        if (op.expiresAt != null) ...[
          const SizedBox(height: 8),
          Text('This stops working at ${clockTime(op.expiresAt!)}. After that, ask again in the chat.', style: text.bodySmall?.copyWith(color: t.p.ink2)),
        ],
        const SizedBox(height: 12),
      ],
      if (op.details.isNotEmpty) KeyValueList(op.details),
      if (op.receipt.isNotEmpty) ...[
        const SizedBox(height: 12),
        KeyValueList([MapEntry('M-Pesa code', op.receipt)]),
      ],
      if (op.resultText.isNotEmpty && !decide) ...[
        const SizedBox(height: 12),
        Callout(op.resultText, tone: op.stage == OperationStage.done ? Tone.good : toneForOperation(op.stage)),
      ],
      if (op.failure.isNotEmpty && op.stage != OperationStage.waiting) ...[
        const SizedBox(height: 12),
        Callout(op.failure, tone: op.stage == OperationStage.unclear ? Tone.warn : Tone.bad),
      ],
      if (op.stage == OperationStage.sent) ...[
        const SizedBox(height: 12),
        Text('Safaricom answers in a few seconds. The answer also arrives in your chat.', style: text.bodySmall?.copyWith(color: t.p.ink2)),
      ],
      if (decide) ...[
        const SizedBox(height: 20),
        FilledButton.icon(
          onPressed: _busy ? null : _approve,
          icon: _busy ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2)) : const Icon(Icons.fingerprint),
          label: const Text('Approve'),
        ),
        const SizedBox(height: 8),
        OutlinedButton(onPressed: _busy ? null : _decline, child: const Text('Decline')),
        const SizedBox(height: 12),
        Text('You approve with your fingerprint, face or phone lock. This app never sees your M-Pesa PIN or your keys.', style: text.bodySmall?.copyWith(color: t.p.ink2)),
      ],
      if (op.createdAt != null) ...[
        const SizedBox(height: 16),
        Text('Asked at ${clockTime(op.createdAt!)}. Reference ${op.id.length >= 8 ? op.id.substring(0, 8) : op.id}.', style: text.bodySmall?.copyWith(color: t.p.ink2)),
      ],
    ]);
  }
}
