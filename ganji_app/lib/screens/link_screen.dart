import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../app_state.dart';
import '../theme/app_theme.dart';
import '../widgets/ui.dart';

/// Signing in: the customer types their phone number, receives a 6 digit code by SMS and types it back.
/// The code proves the phone is theirs, so nobody can read another person's chat.
class LinkScreen extends StatefulWidget {
  const LinkScreen({super.key, required this.state});

  final AppState state;

  @override
  State<LinkScreen> createState() => _LinkScreenState();
}

class _LinkScreenState extends State<LinkScreen> {
  final _phone = TextEditingController();
  final _code = TextEditingController();
  bool _busy = false;
  bool _sent = false;
  String? _error;
  String? _devCode;
  DateTime? _expires;

  @override
  void dispose() {
    _phone.dispose();
    _code.dispose();
    super.dispose();
  }

  bool _phoneOk() => _phone.text.replaceAll(RegExp(r'\D'), '').length >= 9;

  Future<void> _requestCode() async {
    if (!_phoneOk()) {
      setState(() => _error = 'Enter your phone number, for example 0712 345 678.');
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final r = await widget.state.requestCode(_phone.text.trim());
      if (!mounted) return;
      setState(() {
        _sent = true;
        _devCode = r.devCode;
        _expires = r.expiresAt;
        _code.clear();
      });
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _verify() async {
    final code = _code.text.trim();
    if (!RegExp(r'^\d{6}$').hasMatch(code)) {
      setState(() => _error = 'The code has 6 digits.');
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await widget.state.link(phoneNumber: _phone.text.trim(), code: code);
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  String _minutes() {
    final left = _expires?.difference(DateTime.now()).inMinutes;
    if (left == null || left < 1) return 'a few minutes';
    return '$left minutes';
  }

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final t = context.tk;
    final spinner = SizedBox(height: 20, width: 20, child: CircularProgressIndicator(strokeWidth: 2, color: t.p.ink3));
    return Scaffold(
      appBar: AppBar(
        title: const Row(children: [BrandMark(size: 26), SizedBox(width: 10), Text('M-Ganji')]),
        actions: [
          IconButton(
            onPressed: () => showThemeSheet(context, widget.state),
            icon: const Icon(Icons.palette_outlined),
            tooltip: 'Appearance',
          ),
        ],
      ),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(20, 24, 20, 24),
          children: [
            Text('Sign in', style: text.headlineMedium),
            const SizedBox(height: 8),
            Text(
              _sent ? 'We sent a 6 digit code to ${_phone.text.trim()}. It works once and expires in ${_minutes()}.' : 'Enter your phone number. We send you a code by SMS, so only you can open your chat.',
              style: text.bodyLarge?.copyWith(color: t.p.ink2),
            ),
            const SizedBox(height: 24),
            TextField(
              controller: _phone,
              enabled: !_sent && !_busy,
              keyboardType: TextInputType.phone,
              autofillHints: const [AutofillHints.telephoneNumber],
              decoration: const InputDecoration(labelText: 'Your phone number', hintText: '0712 345 678'),
              onSubmitted: (_) => _requestCode(),
            ),
            if (_sent) ...[
              const SizedBox(height: 14),
              TextField(
                controller: _code,
                keyboardType: TextInputType.number,
                maxLength: 6,
                autofillHints: const [AutofillHints.oneTimeCode],
                decoration: const InputDecoration(labelText: '6 digit code'),
                onSubmitted: (_) => _verify(),
              ),
            ],
            if (_devCode != null) ...[
              const SizedBox(height: 4),
              Callout('This server shows the code instead of sending an SMS, for testing: $_devCode', tone: Tone.warn),
              const SizedBox(height: 8),
              Align(
                alignment: Alignment.centerLeft,
                child: TextButton(onPressed: () => setState(() => _code.text = _devCode!), child: const Text('Fill in the code')),
              ),
            ],
            if (_error != null) ...[
              const SizedBox(height: 4),
              Callout(_error!, tone: Tone.bad),
            ],
            const SizedBox(height: 16),
            FilledButton(
              onPressed: _busy ? null : (_sent ? _verify : _requestCode),
              child: _busy ? spinner : Text(_sent ? 'Sign in' : 'Send me a code'),
            ),
            if (_sent) ...[
              const SizedBox(height: 8),
              Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
                TextButton(onPressed: _busy ? null : _requestCode, child: const Text('Send a new code')),
                TextButton(
                  onPressed: _busy
                      ? null
                      : () => setState(() {
                            _sent = false;
                            _devCode = null;
                            _error = null;
                          }),
                  child: const Text('Change number'),
                ),
              ]),
            ],
            const SizedBox(height: 24),
            Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Icon(Icons.lock_outline, size: 18, color: t.p.ink2),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  'This app never asks for your M-Pesa PIN. You only type it inside M-Pesa itself.',
                  style: text.bodySmall,
                ),
              ),
            ]),
          ],
        ),
      ),
    );
  }
}
