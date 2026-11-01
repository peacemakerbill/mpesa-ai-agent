import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../api/api_client.dart';
import '../app_state.dart';
import '../layout/responsive.dart';
import '../theme/app_theme.dart';
import '../widgets/ui.dart';

/// Signing in: the customer types their phone number, receives a 6 digit code by SMS and types it back.
/// The code proves the phone is theirs, so nobody can read another person's chat.
///
/// On a phone the brand sits in a band at the top. On a wide window it fills the left half and the form is on the right.
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
    return LayoutBuilder(
      builder: (context, box) {
        final wide = box.maxWidth >= 860;
        if (wide) {
          return Scaffold(
            body: Row(children: [
              Expanded(flex: 5, child: _BrandPanel(state: widget.state)),
              Expanded(
                flex: 4,
                child: SafeArea(
                  child: Center(
                    child: SingleChildScrollView(
                      padding: const EdgeInsets.all(32),
                      child: ConstrainedBox(
                        constraints: const BoxConstraints(maxWidth: 420),
                        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: _form(context)),
                      ),
                    ),
                  ),
                ),
              ),
            ]),
          );
        }
        final keyboardOpen = MediaQuery.viewInsetsOf(context).bottom > 0;
        return Scaffold(
          body: Column(children: [
            _BrandBand(state: widget.state, compactBand: keyboardOpen),
            Expanded(
              child: SafeArea(
                top: false,
                child: CenteredList(maxWidth: 460, top: 24, bottom: 24, children: _form(context)),
              ),
            ),
          ]),
        );
      },
    );
  }

  List<Widget> _form(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final t = context.tk;
    final spinner = SizedBox(height: 20, width: 20, child: CircularProgressIndicator(strokeWidth: 2, color: t.p.ink3));
    return [
      Text('Sign in', style: text.headlineMedium),
      const SizedBox(height: 8),
      Text(
        _sent
            ? 'We sent a 6 digit code to ${_phone.text.trim()}. It works once and expires in ${_minutes()}.'
            : 'Enter your phone number. We send you a code by SMS, so only you can open your chat.',
        style: text.bodyLarge?.copyWith(color: t.p.ink2),
      ),
      const SizedBox(height: 24),
      TextField(
        controller: _phone,
        enabled: !_sent && !_busy,
        keyboardType: TextInputType.phone,
        autofillHints: const [AutofillHints.telephoneNumber],
        decoration: const InputDecoration(labelText: 'Your phone number', hintText: '0712 345 678', prefixIcon: Icon(Icons.phone_outlined)),
        onSubmitted: (_) => _requestCode(),
      ),
      if (_sent) ...[
        const SizedBox(height: 14),
        TextField(
          controller: _code,
          keyboardType: TextInputType.number,
          maxLength: 6,
          textAlign: TextAlign.center,
          style: t.numeric(26),
          inputFormatters: [FilteringTextInputFormatter.digitsOnly],
          autofillHints: const [AutofillHints.oneTimeCode],
          decoration: const InputDecoration(labelText: '6 digit code', counterText: ''),
          onSubmitted: (_) => _verify(),
        ),
      ],
      if (_devCode != null) ...[
        const SizedBox(height: 12),
        Callout('This server shows the code instead of sending an SMS, for testing: $_devCode', tone: Tone.warn),
        const SizedBox(height: 8),
        Align(
          alignment: Alignment.centerLeft,
          child: TextButton(onPressed: () => setState(() => _code.text = _devCode!), child: const Text('Fill in the code')),
        ),
      ],
      if (_error != null) ...[
        const SizedBox(height: 12),
        Callout(_error!, tone: Tone.bad),
      ],
      const SizedBox(height: 16),
      FilledButton(
        onPressed: _busy ? null : (_sent ? _verify : _requestCode),
        child: _busy ? spinner : Text(_sent ? 'Sign in' : 'Send me a code'),
      ),
      if (_sent) ...[
        const SizedBox(height: 8),
        Wrap(alignment: WrapAlignment.spaceBetween, children: [
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
    ];
  }
}

/// The band across the top of the sign-in page on a phone.
class _BrandBand extends StatelessWidget {
  const _BrandBand({required this.state, required this.compactBand});

  final AppState state;

  /// Hides the tagline while the keyboard is open, so the form keeps the room.
  final bool compactBand;

  @override
  Widget build(BuildContext context) {
    final t = context.tk;
    final p = t.p;
    final text = Theme.of(context).textTheme;
    final top = MediaQuery.paddingOf(context).top;
    return Container(
      width: double.infinity,
      color: p.headBg,
      child: Stack(children: [
        Positioned.fill(child: BrandPattern(color: p.headInk.withAlpha(22))),
        Padding(
          padding: EdgeInsets.fromLTRB(20, top + 14, 8, compactBand ? 12 : 22),
          child: Row(crossAxisAlignment: CrossAxisAlignment.center, children: [
            BrandMark(size: compactBand ? 30 : 40, ink: p.headInk, accent: p.headDark ? p.headInk : p.accent),
            const SizedBox(width: 12),
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, mainAxisSize: MainAxisSize.min, children: [
                Text('M-Ganji', style: text.titleLarge?.copyWith(color: p.headInk)),
                if (!compactBand) Text('Chat to pay. Approve on your phone.', style: text.bodySmall?.copyWith(color: p.headInk.withAlpha(200))),
              ]),
            ),
            IconButton(
              onPressed: () => showThemeSheet(context, state),
              icon: Icon(Icons.palette_outlined, color: p.headInk),
              tooltip: 'Appearance',
            ),
          ]),
        ),
      ]),
    );
  }
}

/// The left half of the sign-in page on a wide window.
class _BrandPanel extends StatelessWidget {
  const _BrandPanel({required this.state});

  final AppState state;

  @override
  Widget build(BuildContext context) {
    final t = context.tk;
    final p = t.p;
    final text = Theme.of(context).textTheme;
    Widget point(IconData icon, String words) => Padding(
          padding: const EdgeInsets.only(top: 14),
          child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Icon(icon, size: 20, color: p.headInk),
            const SizedBox(width: 12),
            Expanded(child: Text(words, style: text.bodyLarge?.copyWith(color: p.headInk.withAlpha(225)))),
          ]),
        );
    return Container(
      color: p.headBg,
      child: Stack(fit: StackFit.expand, children: [
        Positioned.fill(child: BrandPattern(color: p.headInk.withAlpha(22))),
        SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(40),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Row(children: [
                BrandMark(size: 44, ink: p.headInk, accent: p.headDark ? p.headInk : p.accent),
                const SizedBox(width: 14),
                Text('M-Ganji', style: text.headlineSmall?.copyWith(color: p.headInk)),
                const Spacer(),
                IconButton(
                  onPressed: () => showThemeSheet(context, state),
                  icon: Icon(Icons.palette_outlined, color: p.headInk),
                  tooltip: 'Appearance',
                ),
              ]),
              const Spacer(),
              ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 520),
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text('Chat to pay.\nApprove on your phone.', style: text.headlineLarge?.copyWith(color: p.headInk)),
                  const SizedBox(height: 14),
                  Text(
                    'M-Ganji prepares the request. You approve it with your fingerprint, face or phone lock, then finish inside M-Pesa.',
                    style: text.bodyLarge?.copyWith(color: p.headInk.withAlpha(225)),
                  ),
                  const SizedBox(height: 14),
                  point(Icons.lock_outline, 'Your PIN stays in M-Pesa. This app never sees it.'),
                  point(Icons.verified_user_outlined, 'Nothing moves until you approve it.'),
                  point(Icons.contacts_outlined, 'Your contacts stay on your device.'),
                ]),
              ),
              const Spacer(flex: 2),
            ]),
          ),
        ),
      ]),
    );
  }
}
