import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../api/api_client.dart';
import '../app_state.dart';
import '../layout/responsive.dart';
import '../profile/signup_flow.dart';
import '../theme/app_theme.dart';
import '../widgets/ui.dart';

/// Creating an account in two steps: the names, phone number and (optionally) an email, then the 6 digit code sent by SMS.
///
/// Nothing is active until the code is typed. When an email was given, a second code goes to that inbox right after, and
/// the Profile page offers to enter it.
class SignupScreen extends StatefulWidget {
  const SignupScreen({super.key, required this.state});

  final AppState state;

  @override
  State<SignupScreen> createState() => _SignupScreenState();
}

class _SignupScreenState extends State<SignupScreen> {
  final _first = TextEditingController();
  final _middle = TextEditingController();
  final _last = TextEditingController();
  final _phone = TextEditingController();
  final _email = TextEditingController();
  final _code = TextEditingController();
  bool _busy = false;
  bool _sent = false;
  String? _error;
  String? _devCode;
  DateTime? _expires;

  @override
  void dispose() {
    _first.dispose();
    _middle.dispose();
    _last.dispose();
    _phone.dispose();
    _email.dispose();
    _code.dispose();
    super.dispose();
  }

  String? _check() {
    if (_first.text.trim().isEmpty) return 'Enter your first name.';
    if (_last.text.trim().isEmpty) return 'Enter your last name.';
    if (phoneDigits(_phone.text) < 9) return 'Enter your phone number, for example 0712 345 678.';
    final email = _email.text.trim();
    if (email.isNotEmpty && !looksLikeEmail(email)) return 'That email address does not look right. Check it, or leave it empty for now.';
    return null;
  }

  Future<void> _requestCode() async {
    final problem = _check();
    if (problem != null) {
      setState(() => _error = problem);
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final r = await widget.state.api.signupRequest(
        phone: _phone.text.trim(),
        firstName: _first.text.trim(),
        middleName: _middle.text.trim(),
        lastName: _last.text.trim(),
        email: _email.text.trim(),
      );
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
      await finishSignup(widget.state, phone: _phone.text.trim(), code: code);
      if (!mounted) return;
      // The app is signed in now and shows the home page. Remove this page and the sign in page from the way.
      Navigator.of(context).popUntil((route) => route.isFirst);
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
      appBar: AppBar(title: const Text('Create an account')),
      body: SafeArea(
        top: false,
        child: CenteredList(maxWidth: 460, top: 20, bottom: 24, children: [
          Text(
            _sent
                ? 'We sent a 6 digit code to ${_phone.text.trim()}. It works once and expires in ${_minutes()}.'
                : 'Tell us who you are. We send a code to your phone by SMS, so only you can open this account.',
            style: text.bodyLarge?.copyWith(color: t.p.ink2),
          ),
          const SizedBox(height: 20),
          TextField(
            controller: _first,
            enabled: !_sent && !_busy,
            textCapitalization: TextCapitalization.words,
            textInputAction: TextInputAction.next,
            autofillHints: const [AutofillHints.givenName],
            decoration: const InputDecoration(labelText: 'First name', prefixIcon: Icon(Icons.person_outline)),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _middle,
            enabled: !_sent && !_busy,
            textCapitalization: TextCapitalization.words,
            textInputAction: TextInputAction.next,
            autofillHints: const [AutofillHints.middleName],
            decoration: const InputDecoration(labelText: 'Middle name (optional)'),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _last,
            enabled: !_sent && !_busy,
            textCapitalization: TextCapitalization.words,
            textInputAction: TextInputAction.next,
            autofillHints: const [AutofillHints.familyName],
            decoration: const InputDecoration(labelText: 'Last name'),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _phone,
            enabled: !_sent && !_busy,
            keyboardType: TextInputType.phone,
            textInputAction: TextInputAction.next,
            autofillHints: const [AutofillHints.telephoneNumber],
            decoration: const InputDecoration(labelText: 'Your phone number', hintText: '0712 345 678', prefixIcon: Icon(Icons.phone_outlined)),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _email,
            enabled: !_sent && !_busy,
            keyboardType: TextInputType.emailAddress,
            textInputAction: TextInputAction.done,
            autofillHints: const [AutofillHints.email],
            decoration: const InputDecoration(
              labelText: 'Email (optional)',
              helperText: 'Reports and alerts go here once it is verified. You can add it later under Profile.',
              helperMaxLines: 3,
              prefixIcon: Icon(Icons.mail_outline),
            ),
            onSubmitted: (_) => _requestCode(),
          ),
          if (_sent) ...[
            const SizedBox(height: 16),
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
            child: _busy ? spinner : Text(_sent ? 'Create account' : 'Send me a code'),
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
                child: const Text('Change my details'),
              ),
            ]),
          ],
          const SizedBox(height: 20),
          Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Icon(Icons.lock_outline, size: 18, color: t.p.ink2),
            const SizedBox(width: 10),
            Expanded(child: Text('This app never asks for your M-Pesa PIN. You only type it inside M-Pesa itself.', style: text.bodySmall)),
          ]),
        ]),
      ),
    );
  }
}
