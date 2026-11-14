import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../api/api_client.dart';
import '../app_state.dart';
import '../models/wallet_action.dart';
import '../theme/app_theme.dart';
import 'ui.dart';

/// Opens the sheet where a request is approved with a code sent by text message, for a phone that cannot do a fingerprint,
/// face or screen lock check, or for a customer who would rather not use it. The code goes to the customer's own number.
///
/// [send] asks the server for a code and [approve] sends the typed code. Both throw [ApiException] with a plain message.
/// Whatever the screen needs to change after approving is done inside [approve], so it happens even if the sheet is closed
/// while the answer is on its way. Returns true when the request was approved.
Future<bool> showOtpApproval(
  BuildContext context, {
  required AppState state,
  required String summary,
  required Future<ApprovalCodeSent> Function() send,
  required Future<void> Function(String code) approve,
  bool moneyOut = false,
}) async {
  final done = await showModalBottomSheet<bool>(
    context: context,
    isScrollControlled: true,
    constraints: const BoxConstraints(maxWidth: 560),
    builder: (_) => OtpApprovalSheet(state: state, summary: summary, send: send, approve: approve, moneyOut: moneyOut),
  );
  return done == true;
}

class OtpApprovalSheet extends StatefulWidget {
  const OtpApprovalSheet({
    super.key,
    required this.state,
    required this.summary,
    required this.send,
    required this.approve,
    this.moneyOut = false,
  });

  final AppState state;
  final String summary;
  final Future<ApprovalCodeSent> Function() send;
  final Future<void> Function(String code) approve;
  final bool moneyOut;

  @override
  State<OtpApprovalSheet> createState() => _OtpApprovalSheetState();
}

class _OtpApprovalSheetState extends State<OtpApprovalSheet> {
  final TextEditingController _code = TextEditingController();
  Timer? _timer;
  int _wait = 0;
  bool _sending = false;
  bool _approving = false;
  bool _sent = false;
  String? _error;
  String? _devCode;

  @override
  void initState() {
    super.initState();
    _send();
  }

  @override
  void dispose() {
    _timer?.cancel();
    _code.dispose();
    super.dispose();
  }

  void _startWait(int seconds) {
    _timer?.cancel();
    setState(() => _wait = seconds);
    if (seconds <= 0) return;
    _timer = Timer.periodic(const Duration(seconds: 1), (t) {
      if (!mounted) {
        t.cancel();
        return;
      }
      setState(() => _wait = _wait - 1);
      if (_wait <= 0) t.cancel();
    });
  }

  /// A signed-out phone cannot approve anything. Returns true when that is what happened, and the sheet is closed.
  Future<bool> _signedOut(ApiException e) async {
    if (!e.unauthorized) return false;
    await widget.state.sessionExpired();
    if (mounted) Navigator.of(context).pop(false);
    return true;
  }

  Future<void> _send() async {
    setState(() {
      _sending = true;
      _error = null;
    });
    try {
      final sent = await widget.send();
      if (!mounted) return;
      _code.clear();
      if (sent.devCode != null) _code.text = sent.devCode!;
      setState(() {
        _sent = true;
        _devCode = sent.devCode;
      });
      _startWait(sent.resendAfterSeconds);
    } on ApiException catch (e) {
      if (await _signedOut(e)) return;
      if (!mounted) return;
      // "A code was just sent" means one is already on its way and still works, so the box stays and the timer starts.
      if (e.status == 429) _startWait(30);
      setState(() => _error = e.message);
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  Future<void> _approve() async {
    final code = _code.text.trim();
    if (code.length != 6) {
      setState(() => _error = 'Enter the 6 digit code from the text message.');
      return;
    }
    setState(() {
      _approving = true;
      _error = null;
    });
    try {
      await widget.approve(code);
      if (mounted) Navigator.of(context).pop(true);
    } on ApiException catch (e) {
      if (await _signedOut(e)) return;
      if (mounted) setState(() => _error = e.message);
    } finally {
      if (mounted) setState(() => _approving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = context.tk;
    final text = Theme.of(context).textTheme;
    final busy = _sending || _approving;
    final ready = _code.text.trim().length == 6;
    return SafeArea(
      child: Padding(
        padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(context).bottom),
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(20, 4, 12, 16),
          child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            const SheetHeader(title: 'Approve with OTP', subtitle: 'A code is sent by text message to your number.'),
            const SizedBox(height: 12),
            Padding(
              padding: const EdgeInsets.only(right: 8),
              child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(color: t.p.surface2, borderRadius: t.corners),
                  child: Text(widget.summary, style: text.titleMedium),
                ),
                const SizedBox(height: 8),
                Text('The text message names this request. If it does not match, do not enter the code.', style: text.bodySmall),
                if (widget.moneyOut) ...[
                  const SizedBox(height: 12),
                  const Callout('Money leaves your business account as soon as you approve. Check the number and the amount first.', tone: Tone.warn),
                ],
                const SizedBox(height: 16),
                if (_devCode != null) ...[
                  Callout('Test system: the code is $_devCode. A real system sends it by text message only.', tone: Tone.info),
                  const SizedBox(height: 12),
                ],
                TextField(
                  controller: _code,
                  autofocus: true,
                  keyboardType: TextInputType.number,
                  textInputAction: TextInputAction.done,
                  autofillHints: const [AutofillHints.oneTimeCode],
                  inputFormatters: [FilteringTextInputFormatter.digitsOnly, LengthLimitingTextInputFormatter(6)],
                  style: text.headlineSmall?.copyWith(letterSpacing: 6),
                  decoration: InputDecoration(
                    labelText: '6 digit code',
                    helperText: _sending ? 'Sending the code...' : (_sent ? 'Code sent. It works for 5 minutes.' : null),
                  ),
                  onChanged: (_) => setState(() => _error = null),
                  onSubmitted: (_) {
                    if (!busy) _approve();
                  },
                ),
                if (_error != null) ...[
                  const SizedBox(height: 12),
                  Callout(_error!, tone: Tone.bad),
                ],
                const SizedBox(height: 16),
                FilledButton.icon(
                  onPressed: busy || !ready ? null : _approve,
                  icon: _approving
                      ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
                      : const Icon(Icons.verified_outlined),
                  label: Text(_approving ? 'Approving...' : 'Approve'),
                ),
                const SizedBox(height: 8),
                TextButton(
                  onPressed: busy || _wait > 0 ? null : _send,
                  child: Text(_wait > 0 ? 'Send a new code in ${_wait}s' : 'Send a new code'),
                ),
                const SizedBox(height: 4),
                const SheetCloseButton(),
              ]),
            ),
          ]),
        ),
      ),
    );
  }
}
