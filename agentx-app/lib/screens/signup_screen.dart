import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../api/api_client.dart';
import '../app_state.dart';
import '../profile/signup_flow.dart';
import '../theme/app_theme.dart';
import '../widgets/auth_shell.dart';
import '../widgets/ui.dart';

/// Creating an account in three short steps: who you are, how to reach you, then the 6 digit code sent by SMS.
///
/// Nothing is active until the code is typed. When an email was given, a second code goes to that inbox right after, and the
/// Profile page offers to enter it. The page looks like the sign in page: the brand in a band on a phone, or on the left of a wide
/// window, and the form beside it.
class SignupScreen extends StatefulWidget {
  const SignupScreen({super.key, required this.state});

  final AppState state;

  @override
  State<SignupScreen> createState() => _SignupScreenState();
}

class _SignupScreenState extends State<SignupScreen> {
  static const _labels = ['About you', 'Contact', 'Verify'];
  static const _cooldown = 30;

  final _first = TextEditingController();
  final _middle = TextEditingController();
  final _last = TextEditingController();
  final _phone = TextEditingController();
  final _email = TextEditingController();
  final _code = TextEditingController();
  final _codeFocus = FocusNode();
  Timer? _ticker;
  int _step = 0;
  int _resendLeft = 0;
  bool _showMiddle = false;
  bool _busy = false;
  String? _error;
  String? _devCode;
  DateTime? _expires;

  @override
  void dispose() {
    _ticker?.cancel();
    _first.dispose();
    _middle.dispose();
    _last.dispose();
    _phone.dispose();
    _email.dispose();
    _code.dispose();
    _codeFocus.dispose();
    super.dispose();
  }

  // checks

  String? _checkNames() {
    if (_first.text.trim().isEmpty) return 'Enter your first name.';
    if (_last.text.trim().isEmpty) return 'Enter your last name.';
    return null;
  }

  String? _checkContact() {
    if (phoneDigits(_phone.text) < 9) return 'Enter your phone number, for example 0712 345 678.';
    final email = _email.text.trim();
    if (email.isNotEmpty && !looksLikeEmail(email)) return 'That email address does not look right. Check it, or leave it empty for now.';
    return null;
  }

  // moving between the steps

  void _go(int step) {
    setState(() {
      _step = step;
      _error = null;
    });
    if (step == 2) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) _codeFocus.requestFocus();
      });
    }
  }

  void _back() {
    if (_busy) return;
    if (_step > 0) {
      _go(_step - 1);
    } else {
      Navigator.of(context).maybePop();
    }
  }

  void _startCooldown() {
    _ticker?.cancel();
    setState(() => _resendLeft = _cooldown);
    _ticker = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (!mounted) {
        timer.cancel();
        return;
      }
      setState(() => _resendLeft = _resendLeft > 0 ? _resendLeft - 1 : 0);
      if (_resendLeft == 0) timer.cancel();
    });
  }

  void _next() {
    if (_busy) return;
    switch (_step) {
      case 0:
        final problem = _checkNames();
        if (problem != null) {
          setState(() => _error = problem);
        } else {
          _go(1);
        }
      case 1:
        _requestCode();
      default:
        _verify();
    }
  }

  // talking to the server

  Future<void> _requestCode({bool again = false}) async {
    final problem = _checkNames() ?? _checkContact();
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
        _devCode = r.devCode;
        _expires = r.expiresAt;
        _code.clear();
      });
      _startCooldown();
      if (!again || _step != 2) _go(2);
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
      if (mounted) {
        // Empty the boxes so the next try starts clean, and keep the keyboard up.
        _code.clear();
        setState(() => _error = e.message);
        _codeFocus.requestFocus();
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  String _minutes() {
    final left = _expires?.difference(DateTime.now()).inMinutes;
    if (left == null || left < 1) return 'a few minutes';
    return '$left minutes';
  }

  // the page

  @override
  Widget build(BuildContext context) {
    final reduce = MediaQuery.disableAnimationsOf(context);
    return PopScope(
      canPop: _step == 0,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) _back();
      },
      child: AuthShell(
        state: widget.state,
        onBack: _back,
        headline: 'Open your account.\nPay from the chat.',
        blurb: 'One SMS code confirms your phone. There is no password to remember.',
        points: const [
          AuthPoint(Icons.sms_outlined, 'Your phone number is your sign in, confirmed by an SMS code.'),
          AuthPoint(Icons.mail_outline, 'Add an email for reports and alerts. It is verified with its own code.'),
          AuthPoint(Icons.lock_outline, 'Your PIN stays in M-Pesa. This app never sees it.'),
        ],
        children: [
          _Steps(step: _step, labels: _labels, onTap: (i) {
            if (!_busy) _go(i);
          }),
          const SizedBox(height: 22),
          AnimatedSwitcher(
            duration: reduce ? Duration.zero : const Duration(milliseconds: 240),
            switchInCurve: Curves.easeOutCubic,
            switchOutCurve: Curves.easeIn,
            transitionBuilder: (child, animation) => FadeTransition(
              opacity: animation,
              child: SlideTransition(
                position: Tween<Offset>(begin: const Offset(0.04, 0), end: Offset.zero).animate(animation),
                child: child,
              ),
            ),
            layoutBuilder: (current, previous) => Stack(alignment: Alignment.topLeft, children: [...previous, if (current != null) current]),
            child: KeyedSubtree(key: ValueKey<int>(_step), child: _body(context)),
          ),
          ..._tail(context),
        ],
      ),
    );
  }

  Widget _body(BuildContext context) {
    switch (_step) {
      case 0:
        return _aboutYou(context);
      case 1:
        return _contact(context);
      default:
        return _verifyStep(context);
    }
  }

  Widget _heading(BuildContext context, String title, String words) {
    final text = Theme.of(context).textTheme;
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      Text(title, style: text.headlineMedium),
      const SizedBox(height: 8),
      Text(words, style: text.bodyLarge?.copyWith(color: context.tk.p.ink2)),
      const SizedBox(height: 20),
    ]);
  }

  // Step 1: the name, with a live preview so the page feels like it is already theirs.
  Widget _aboutYou(BuildContext context) {
    final fields = <Widget>[
      TextField(
        controller: _first,
        enabled: !_busy,
        textCapitalization: TextCapitalization.words,
        textInputAction: TextInputAction.next,
        autofillHints: const [AutofillHints.givenName],
        decoration: const InputDecoration(labelText: 'First name', prefixIcon: Icon(Icons.person_outline)),
      ),
      TextField(
        controller: _last,
        enabled: !_busy,
        textCapitalization: TextCapitalization.words,
        textInputAction: TextInputAction.next,
        autofillHints: const [AutofillHints.familyName],
        decoration: const InputDecoration(labelText: 'Last name'),
        onSubmitted: (_) => _next(),
      ),
    ];
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      _heading(context, 'Create your account', 'Tell us who you are. Your name is shown in the app and on the reports you send.'),
      ListenableBuilder(
        listenable: Listenable.merge([_first, _middle, _last]),
        builder: (context, _) => _NamePreview(name: _fullName(), initials: _initials()),
      ),
      const SizedBox(height: 18),
      LayoutBuilder(
        builder: (context, box) {
          if (box.maxWidth >= 380) {
            return Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Expanded(child: fields[0]),
              const SizedBox(width: 12),
              Expanded(child: fields[1]),
            ]);
          }
          return Column(children: [fields[0], const SizedBox(height: 12), fields[1]]);
        },
      ),
      const SizedBox(height: 4),
      if (_showMiddle) ...[
        const SizedBox(height: 8),
        TextField(
          controller: _middle,
          enabled: !_busy,
          textCapitalization: TextCapitalization.words,
          textInputAction: TextInputAction.next,
          autofillHints: const [AutofillHints.middleName],
          decoration: const InputDecoration(labelText: 'Middle name (optional)'),
        ),
      ] else
        Align(
          alignment: Alignment.centerLeft,
          child: TextButton.icon(
            onPressed: _busy ? null : () => setState(() => _showMiddle = true),
            icon: const Icon(Icons.add, size: 18),
            label: const Text('Add a middle name'),
          ),
        ),
    ]);
  }

  // Step 2: how to reach the person, and why an email is worth adding.
  Widget _contact(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final p = context.tk.p;
    Widget benefit(IconData icon, String words) => Padding(
          padding: const EdgeInsets.only(top: 10),
          child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Icon(icon, size: 18, color: p.accent),
            const SizedBox(width: 10),
            Expanded(child: Text(words, style: text.bodyMedium)),
          ]),
        );
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      _heading(context, 'How can we reach you?', 'Your phone gets the SMS code, and it is how you sign in from now on.'),
      TextField(
        controller: _phone,
        enabled: !_busy,
        keyboardType: TextInputType.phone,
        textInputAction: TextInputAction.next,
        autofillHints: const [AutofillHints.telephoneNumber],
        decoration: const InputDecoration(labelText: 'Your phone number', hintText: '0712 345 678', prefixIcon: Icon(Icons.phone_outlined)),
      ),
      const SizedBox(height: 12),
      TextField(
        controller: _email,
        enabled: !_busy,
        keyboardType: TextInputType.emailAddress,
        textInputAction: TextInputAction.done,
        autofillHints: const [AutofillHints.email],
        decoration: const InputDecoration(labelText: 'Email (optional)', hintText: 'you@example.com', prefixIcon: Icon(Icons.mail_outline)),
        onSubmitted: (_) => _next(),
      ),
      const SizedBox(height: 16),
      Panel(
        color: p.surface2,
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text('Why add an email?', style: text.titleSmall),
          benefit(Icons.description_outlined, 'Ask the chat to send a report as PDF, CSV or Excel.'),
          benefit(Icons.notifications_none, 'Get a note when your account signs in or a payment finishes.'),
          benefit(Icons.schedule, 'No rush. You can add and verify it later under Profile.'),
        ]),
      ),
    ]);
  }

  // Step 3: the SMS code, in six boxes.
  Widget _verifyStep(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final p = context.tk.p;
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      _heading(
        context,
        'Enter the code',
        'We sent a 6 digit code to ${_phone.text.trim()}. It works once and expires in ${_minutes()}.',
      ),
      Panel(
        padding: const EdgeInsets.fromLTRB(14, 10, 8, 10),
        child: Row(children: [
          _Avatar(initials: _initials(), size: 40),
          const SizedBox(width: 12),
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(_fullName(), style: text.titleSmall, maxLines: 1, overflow: TextOverflow.ellipsis),
              Text(_phone.text.trim(), style: text.bodySmall?.copyWith(color: p.ink2)),
            ]),
          ),
          TextButton(onPressed: _busy ? null : () => _go(0), child: const Text('Edit')),
        ]),
      ),
      const SizedBox(height: 20),
      _CodeEntry(
        controller: _code,
        focus: _codeFocus,
        enabled: !_busy,
        hasError: _error != null,
        onChanged: () {
          if (_error != null) setState(() => _error = null);
        },
        onComplete: _verify,
      ),
      if (_devCode != null) ...[
        const SizedBox(height: 14),
        Callout('This server shows the code instead of sending an SMS, for testing: $_devCode', tone: Tone.warn),
        Align(
          alignment: Alignment.centerLeft,
          child: TextButton(
            onPressed: () => setState(() => _code.text = _devCode!),
            child: const Text('Fill in the code'),
          ),
        ),
      ],
    ]);
  }

  /// Everything under the step: the error, the main button, and the smaller actions.
  List<Widget> _tail(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final p = context.tk.p;
    final spinner = SizedBox(height: 20, width: 20, child: CircularProgressIndicator(strokeWidth: 2, color: p.ink3));
    final label = _step == 0 ? 'Continue' : (_step == 1 ? 'Send me a code' : 'Create account');
    return [
      if (_error != null) ...[
        const SizedBox(height: 14),
        Callout(_error!, tone: Tone.bad),
      ],
      const SizedBox(height: 18),
      FilledButton(onPressed: _busy ? null : _next, child: _busy ? spinner : Text(label)),
      const SizedBox(height: 6),
      if (_step == 2)
        Align(
          alignment: Alignment.center,
          child: TextButton(
            onPressed: (_busy || _resendLeft > 0) ? null : () => _requestCode(again: true),
            child: Text(_resendLeft > 0 ? 'Send a new code in ${_resendLeft}s' : 'Send a new code'),
          ),
        )
      else if (_step == 1)
        Align(
          alignment: Alignment.center,
          child: TextButton(onPressed: _busy ? null : _back, child: const Text('Back')),
        ),
      const SizedBox(height: 18),
      Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Icon(Icons.lock_outline, size: 18, color: p.ink2),
        const SizedBox(width: 10),
        Expanded(child: Text('This app never asks for your M-Pesa PIN. You only type it inside M-Pesa itself.', style: text.bodySmall)),
      ]),
    ];
  }

  // small helpers

  String _fullName() {
    final parts = [_first.text, _middle.text, _last.text].map((s) => s.trim()).where((s) => s.isNotEmpty);
    return parts.join(' ');
  }

  String _initials() {
    String first(String s) {
      final t = s.trim();
      return t.isEmpty ? '' : String.fromCharCode(t.runes.first).toUpperCase();
    }

    return first(_first.text) + first(_last.text);
  }
}

/// The three steps as a bar that fills in, with the name of each underneath. Earlier steps can be tapped to go back.
class _Steps extends StatelessWidget {
  const _Steps({required this.step, required this.labels, required this.onTap});

  final int step;
  final List<String> labels;
  final void Function(int index) onTap;

  @override
  Widget build(BuildContext context) {
    final t = context.tk;
    final p = t.p;
    final text = Theme.of(context).textTheme;
    return Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
      for (var i = 0; i < labels.length; i++)
        Expanded(
          child: InkWell(
            onTap: i < step ? () => onTap(i) : null,
            child: Padding(
              padding: EdgeInsets.only(right: i == labels.length - 1 ? 0 : 8, bottom: 2),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                AnimatedContainer(
                  duration: const Duration(milliseconds: 240),
                  height: 4,
                  decoration: BoxDecoration(
                    color: i <= step ? p.accent : p.line,
                    borderRadius: BorderRadius.circular(t.square ? 0 : 4),
                  ),
                ),
                const SizedBox(height: 8),
                Row(children: [
                  if (i < step) ...[Icon(Icons.check_circle, size: 14, color: p.good), const SizedBox(width: 4)],
                  Flexible(
                    child: Text(
                      labels[i],
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: text.bodySmall?.copyWith(
                        color: i == step ? p.ink : p.ink3,
                        fontWeight: i == step ? FontWeight.w700 : FontWeight.w500,
                      ),
                    ),
                  ),
                ]),
              ]),
            ),
          ),
        ),
    ]);
  }
}

/// A round badge with the person's initials, or a person icon while there is nothing to show yet.
class _Avatar extends StatelessWidget {
  const _Avatar({required this.initials, this.size = 48});

  final String initials;
  final double size;

  @override
  Widget build(BuildContext context) {
    final t = context.tk;
    final p = t.p;
    return AnimatedContainer(
      duration: const Duration(milliseconds: 200),
      width: size,
      height: size,
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: p.accentSoft,
        border: Border.all(color: p.accentLine),
        borderRadius: t.square ? BorderRadius.zero : BorderRadius.circular(size),
      ),
      child: initials.isEmpty
          ? Icon(Icons.person_outline, size: size * 0.5, color: p.accent)
          : Text(initials, style: t.numeric(size * 0.38, color: p.accent)),
    );
  }
}

/// Shows the name as it is typed, so the first page already feels like the person's own account.
class _NamePreview extends StatelessWidget {
  const _NamePreview({required this.name, required this.initials});

  final String name;
  final String initials;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final p = context.tk.p;
    final empty = name.isEmpty;
    return Panel(
      padding: const EdgeInsets.all(14),
      child: Row(children: [
        _Avatar(initials: initials, size: 52),
        const SizedBox(width: 14),
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(
              empty ? 'Your name' : name,
              style: text.titleMedium?.copyWith(color: empty ? p.ink3 : p.ink),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
            const SizedBox(height: 2),
            Text(empty ? 'It appears here as you type.' : 'Account holder', style: text.bodySmall?.copyWith(color: p.ink2)),
          ]),
        ),
      ]),
    );
  }
}

/// Six boxes for the SMS code. One real text field sits invisibly over them, so typing, pasting and the phone's own code
/// suggestion all work. The code is sent as soon as the sixth digit is in.
class _CodeEntry extends StatelessWidget {
  const _CodeEntry({
    required this.controller,
    required this.focus,
    required this.enabled,
    required this.hasError,
    required this.onChanged,
    required this.onComplete,
  });

  final TextEditingController controller;
  final FocusNode focus;
  final bool enabled;
  final bool hasError;
  final VoidCallback onChanged;
  final VoidCallback onComplete;

  @override
  Widget build(BuildContext context) {
    final t = context.tk;
    final p = t.p;
    return Semantics(
      label: '6 digit code',
      textField: true,
      child: LayoutBuilder(
        builder: (context, box) {
          const gap = 8.0;
          final width = ((box.maxWidth - gap * 5) / 6).clamp(34.0, 58.0).toDouble();
          return GestureDetector(
            behavior: HitTestBehavior.opaque,
            onTap: enabled ? focus.requestFocus : null,
            child: SizedBox(
              height: 62,
              child: Stack(alignment: Alignment.center, children: [
                AnimatedBuilder(
                  animation: Listenable.merge([controller, focus]),
                  builder: (context, _) {
                    final digits = controller.text;
                    return Row(mainAxisAlignment: MainAxisAlignment.center, children: [
                      for (var i = 0; i < 6; i++) ...[
                        if (i > 0) const SizedBox(width: gap),
                        _DigitBox(
                          width: width,
                          digit: i < digits.length ? digits[i] : '',
                          active: focus.hasFocus && enabled && i == (digits.length > 5 ? 5 : digits.length),
                          error: hasError,
                        ),
                      ],
                    ]);
                  },
                ),
                Positioned.fill(
                  child: TextSelectionTheme(
                    data: const TextSelectionThemeData(selectionColor: Colors.transparent, cursorColor: Colors.transparent),
                    child: TextField(
                      key: const Key('signup-code'),
                      controller: controller,
                      focusNode: focus,
                      readOnly: !enabled,
                      keyboardType: TextInputType.number,
                      maxLength: 6,
                      showCursor: false,
                      autofillHints: const [AutofillHints.oneTimeCode],
                      inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                      style: const TextStyle(color: Colors.transparent, fontSize: 24),
                      decoration: const InputDecoration(
                        filled: false,
                        counterText: '',
                        border: InputBorder.none,
                        enabledBorder: InputBorder.none,
                        focusedBorder: InputBorder.none,
                        disabledBorder: InputBorder.none,
                        errorBorder: InputBorder.none,
                        focusedErrorBorder: InputBorder.none,
                        contentPadding: EdgeInsets.zero,
                      ),
                      onChanged: (value) {
                        onChanged();
                        if (value.length == 6) onComplete();
                      },
                    ),
                  ),
                ),
              ]),
            ),
          );
        },
      ),
    );
  }
}

class _DigitBox extends StatelessWidget {
  const _DigitBox({required this.width, required this.digit, required this.active, required this.error});

  final double width;
  final String digit;
  final bool active;
  final bool error;

  @override
  Widget build(BuildContext context) {
    final t = context.tk;
    final p = t.p;
    final color = error ? p.bad : (active ? p.accent : (digit.isNotEmpty ? p.lineStrong : p.line));
    return AnimatedContainer(
      duration: const Duration(milliseconds: 140),
      width: width,
      height: 58,
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: digit.isNotEmpty ? p.surface : p.surface2,
        borderRadius: t.corners,
        border: Border.all(color: color, width: active || error ? 2 : 1),
      ),
      child: Text(digit, style: t.numeric(26)),
    );
  }
}
