import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../app_state.dart';
import '../contacts/contact_models.dart';
import '../layout/responsive.dart';
import '../models/handoff.dart';
import '../models/progress.dart';
import '../models/sms_match.dart';
import '../models/wallet_action.dart';
import '../services/approval.dart';
import '../theme/app_theme.dart';
import '../widgets/ui.dart';
import 'contact_finder_screen.dart';

/// One request from start to finish: review, approve on this phone, finish inside M-Pesa, say how it went.
class ActionScreen extends StatefulWidget {
  const ActionScreen({super.key, required this.state, required this.actionId});

  final AppState state;
  final String actionId;

  @override
  State<ActionScreen> createState() => _ActionScreenState();
}

class _ActionScreenState extends State<ActionScreen> with WidgetsBindingObserver {
  WalletAction? _action;
  String? _error;
  bool _loading = true;
  bool _busy = false;
  PickedContact? _contact;

  AppState get s => widget.state;

  bool _checking = false;
  String? _notice;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _load();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  /// Back from M-Pesa: look for its message, but only if the customer already allowed it. Never asks by itself.
  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    final a = _action;
    if (state == AppLifecycleState.resumed && a != null && a.canCheckSms && a.handedOff) {
      _checkSms(automatic: true);
    }
  }

  Future<T?> _guard<T>(Future<T> Function() call) async {
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      return await call();
    } on ApiException catch (e) {
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
    final a = await _guard(() => s.api.action(widget.actionId));
    if (mounted) {
      setState(() {
        _action = a ?? _action;
        _loading = false;
      });
    }
  }

  void _set(WalletAction? a) {
    if (a != null && mounted) setState(() => _action = a);
  }

  Future<void> _pickContact() async {
    final chosen = await Navigator.of(context).push<PickedContact>(MaterialPageRoute(
      builder: (_) => ContactFinderScreen(services: s.contacts, title: 'Choose the contact'),
    ));
    if (!mounted || chosen == null) return;
    setState(() => _contact = chosen);
  }

  Future<void> _approve() async {
    final a = _action!;
    final result = await s.approval.ask('Confirm that you want to: ${a.summary}');
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
        _set(await _guard(() => s.api.approve(a.id, biometric: result == ApprovalResult.approvedBiometric)));
    }
  }

  Future<void> _decline() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (c) => AppDialog(
        title: const Text('Decline this request?'),
        content: const Text('Nothing will be sent.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('Back')),
          TextButton(onPressed: () => Navigator.pop(c, true), child: const Text('Decline')),
        ],
      ),
    );
    if (ok == true) _set(await _guard(() => s.api.decline(_action!.id)));
  }

  /// Marks the request as handed off, then opens M-Pesa. The customer enters the PIN there, not here.
  Future<void> _handOff({required bool app}) async {
    var a = _action!;
    if (a.approved) {
      final updated = await _guard(() => s.api.handOff(a.id));
      if (updated == null) return;
      _set(updated);
      a = updated;
    }
    final plan = buildHandoff(a, s.config, contactPhone: _contact?.phone);
    final opened = app
        ? await s.launcher.openMpesaApp(androidPackage: s.config.mpesaAndroidPackage, iosUrl: s.config.mpesaIosUrl)
        : await s.launcher.dial(plan.dialUri);
    if (!opened && mounted) {
      setState(() => _error = app
          ? 'Could not open the M-Pesa app. Use the dial button, or open M-Pesa yourself and follow the steps below.'
          : 'Could not open the dialler. Open M-Pesa yourself and follow the steps below.');
    }
  }

  Future<bool> _askSmsConsent() async {
    if (await s.store.smsConsent()) return true;
    if (!mounted) return false;
    final ok = await showDialog<bool>(
      context: context,
      builder: (c) => AppDialog(
        title: const Text('Let the app look at your M-Pesa message?'),
        content: const Text(
            'When you tap the button, the app looks only at recent messages from "MPESA" on this phone and finds the one that matches this request. '
            'It sends us just the code, the amount and who it was paid to. It does not send or keep the message text, '
            'does not read other messages, and never runs in the background.\n\n'
            'It only shows that a message on your phone matches. Safaricom has not confirmed it to us. You can turn this off in your phone settings.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('Not now')),
          TextButton(onPressed: () => Navigator.pop(c, true), child: const Text('Allow')),
        ],
      ),
    );
    if (ok == true) await s.store.setSmsConsent(true);
    return ok == true;
  }

  /// Finds the M-Pesa message for this request on the phone and sends the parsed fields to the server.
  Future<void> _checkSms({bool automatic = false}) async {
    final a = _action;
    if (a == null || _checking || !s.sms.supported) return;
    if (automatic && !(await s.store.smsConsent() && await s.sms.hasPermission())) return;
    if (!automatic) {
      if (!await _askSmsConsent()) return;
      if (!await s.sms.hasPermission() && !await s.sms.requestPermission()) {
        if (mounted) {
          setState(() => _error = 'The app was not allowed to read messages. You can allow it in your phone settings, or send the M-Pesa message to the assistant in the Chat tab.');
        }
        return;
      }
    }
    setState(() {
      _checking = true;
      _error = null;
      _notice = null;
    });
    try {
      final since = (a.approvedAt ?? DateTime.now().subtract(const Duration(minutes: 30))).subtract(smsEarlier);
      final inbox = await s.sms.recent(since: since);
      final check = findMatchingSms(a, inbox, contactPhone: _contact?.phone);
      if (check.problem != null) {
        if (mounted) setState(() => _error = check.problem);
        return;
      }
      if (!check.isFound) {
        if (!automatic && mounted) {
          setState(() => _notice = 'No matching M-Pesa message yet. It can take a moment to arrive. Try again shortly.');
        }
        return;
      }
      final updated = await _guard(() => s.api.smsEvidence(a.id, check.sms!));
      if (updated != null) {
        _set(updated);
        if (mounted) setState(() => _notice = 'Found ${describeSms(check.sms!)}.');
      }
    } finally {
      if (mounted) setState(() => _checking = false);
    }
  }

  Future<void> _report({required bool done}) async {
    String? receipt;
    if (done) {
      final controller = TextEditingController();
      final ok = await showDialog<bool>(
        context: context,
        builder: (c) => AppDialog(
          title: const Text('M-Pesa confirmation code'),
          content: Column(mainAxisSize: MainAxisSize.min, children: [
            const Text('Optional. It is in the M-Pesa message, like SGL1234567. You can also send that message to the assistant in the Chat tab.'),
            const SizedBox(height: 12),
            TextField(
              controller: controller,
              textCapitalization: TextCapitalization.characters,
              maxLength: 12,
              decoration: const InputDecoration(labelText: 'Code'),
            ),
          ]),
          actions: [
            TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('Cancel')),
            TextButton(onPressed: () => Navigator.pop(c, true), child: const Text('Save')),
          ],
        ),
      );
      final text = controller.text.trim().toUpperCase();
      controller.dispose();
      if (ok != true) return;
      if (text.isNotEmpty && !RegExp(r'^[A-Z0-9]{8,12}$').hasMatch(text)) {
        setState(() => _error = 'That code does not look right. Leave it empty if you are not sure.');
        return;
      }
      receipt = text.isEmpty ? null : text;
    }
    _set(await _guard(() => s.api.report(_action!.id, done: done, receipt: receipt)));
  }

  @override
  Widget build(BuildContext context) {
    final a = _action;
    return Scaffold(
      appBar: AppBar(title: Text(a == null ? 'Request' : typeLabel(a.type))),
      body: SafeArea(
        child: _loading
            ? const Center(child: CircularProgressIndicator())
            : a == null
                ? _failure()
                : CenteredList(maxWidth: 640, top: 20, bottom: 32, children: _content(a)),
      ),
    );
  }

  Widget _failure() => CenteredList(
        maxWidth: 560,
        top: 20,
        children: [
          Callout(_error ?? 'Could not open this request.', tone: Tone.bad),
          const SizedBox(height: 12),
          FilledButton(
            onPressed: () {
              setState(() => _loading = true);
              _load();
            },
            child: const Text('Try again'),
          ),
        ],
      );

  List<Widget> _content(WalletAction a) {
    final t = context.tk;
    final text = Theme.of(context).textTheme;
    final widgets = <Widget>[
      Panel(
        padding: const EdgeInsets.fromLTRB(20, 18, 20, 18),
        edge: a.isOpen ? t.p.accent : null,
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          AmountHero(amount: a.amount, summary: a.summary),
          const SizedBox(height: 14),
          StatusTag(a.statusText, tone: toneForStatus(a.status)),
        ]),
      ),
      const SizedBox(height: 16),
    ];

    if (a.isOpen) {
      for (final w in a.warnings) {
        widgets
          ..add(Callout(w, tone: Tone.warn))
          ..add(const SizedBox(height: 8));
      }
    }

    widgets
      ..add(const SizedBox(height: 4))
      ..add(Panel(child: ProgressTimeline(steps: progressFor(a))))
      ..add(const SizedBox(height: 16));

    final rows = <MapEntry<String, String>>[
      for (final e in a.details.entries) MapEntry(niceKey(e.key), e.value),
      if (a.receipt != null && a.receipt!.isNotEmpty) MapEntry('M-Pesa code', a.receipt!),
    ];
    if (rows.isNotEmpty) {
      widgets
        ..add(KeyValueList(rows))
        ..add(const SizedBox(height: 16));
    }

    final verification = a.verificationText;
    if (verification != null) {
      widgets
        ..add(Callout(verification, tone: Tone.info, icon: Icons.sms_outlined))
        ..add(const SizedBox(height: 6))
        ..add(Text('A match means a message on this phone fits the request. It is not a confirmation from Safaricom.', style: text.bodySmall))
        ..add(const SizedBox(height: 16));
    }

    if (a.needsContact && a.isOpen) {
      widgets
        ..add(OutlinedButton.icon(
          onPressed: _busy ? null : _pickContact,
          icon: const Icon(Icons.contacts_outlined),
          label: Text(_contact == null ? 'Choose the contact' : '${_contact!.name}  ${_contact!.phone}'),
        ))
        ..add(Padding(
          padding: const EdgeInsets.only(top: 6, bottom: 12),
          child: Text('Your contacts stay on this phone. They are not sent to us.', style: text.bodySmall),
        ));
    }

    if (a.awaitingApproval) {
      widgets
        ..add(FilledButton.icon(
          onPressed: _busy || (a.needsContact && _contact == null) ? null : _approve,
          icon: const Icon(Icons.fingerprint),
          label: const Text('Approve on this phone'),
        ))
        ..add(Padding(
          padding: const EdgeInsets.only(top: 6, bottom: 4),
          child: Text('You confirm with your fingerprint, face or phone lock. Nothing is sent before that.', style: text.bodySmall, textAlign: TextAlign.center),
        ))
        ..add(const SizedBox(height: 8))
        ..add(OutlinedButton(onPressed: _busy ? null : _decline, child: const Text('Decline')));
    } else if (a.canFinishInMpesa) {
      widgets.addAll(_finishInMpesa(a));
    } else if (a.status == 'DONE_REPORTED') {
      widgets
        ..add(Callout(
          a.smsBacked
              ? 'Thank you. This is recorded as done.'
              : 'Thank you. This is recorded as done, as you reported it. M-Pesa sends you its own confirmation message.',
          tone: Tone.good,
        ))
        ..addAll(_smsSection(a));
    } else {
      widgets
        ..add(Text('Nothing more to do here. Ask the assistant in the Chat tab if you need a new request.', style: text.bodyMedium?.copyWith(color: t.p.ink2)))
        ..addAll(a.status == 'NOT_DONE' ? _smsSection(a) : const <Widget>[]);
    }

    if (_notice != null) {
      widgets
        ..add(const SizedBox(height: 12))
        ..add(Callout(_notice!, tone: Tone.info));
    }
    if (_error != null) {
      widgets
        ..add(const SizedBox(height: 12))
        ..add(Callout(_error!, tone: Tone.bad));
    }
    if (_busy) widgets.add(const Padding(padding: EdgeInsets.only(top: 12), child: LinearProgressIndicator()));
    return widgets;
  }

  List<Widget> _finishInMpesa(WalletAction a) {
    final text = Theme.of(context).textTheme;
    final plan = buildHandoff(a, s.config, contactPhone: _contact?.phone);
    final canOpenApp = s.config.mpesaAndroidPackage.isNotEmpty || s.config.mpesaIosUrl.isNotEmpty;
    return [
      const SectionTitle('Finish in M-Pesa'),
      Text('Your PIN is typed only inside M-Pesa. This app and the assistant never see it.', style: text.bodyMedium?.copyWith(color: context.tk.p.ink2)),
      const SizedBox(height: 12),
      Panel(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
        child: Column(children: [
          for (var i = 0; i < plan.steps.length; i++)
            _StepRow(
              number: i + 1,
              text: plan.steps[i].text,
              value: plan.steps[i].copy,
              copyLabel: plan.steps[i].copyLabel,
              onCopy: plan.steps[i].copy == null
                  ? null
                  : () async {
                      await s.launcher.copy(plan.steps[i].copy!);
                      if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Copied')));
                    },
            ),
        ]),
      ),
      const SizedBox(height: 14),
      FilledButton.icon(
        onPressed: _busy ? null : () => _handOff(app: false),
        icon: const Icon(Icons.dialpad),
        label: Text(plan.prefilled ? 'Dial with details filled in' : 'Dial the M-Pesa menu'),
      ),
      if (canOpenApp) ...[
        const SizedBox(height: 8),
        OutlinedButton.icon(
          onPressed: _busy ? null : () => _handOff(app: true),
          icon: const Icon(Icons.open_in_new),
          label: const Text('Open the M-Pesa app'),
        ),
      ],
      const SizedBox(height: 8),
      const Divider(height: 32),
      ..._smsSection(a),
      Text('When you are back, tell us how it went.', style: text.bodyMedium),
      const SizedBox(height: 10),
      FilledButton.tonal(onPressed: _busy ? null : () => _report(done: true), child: const Text('I completed it in M-Pesa')),
      const SizedBox(height: 8),
      OutlinedButton(onPressed: _busy ? null : () => _report(done: false), child: const Text('I did not complete it')),
    ];
  }

  /// The M-Pesa message check. On iPhone the app cannot read messages, so it says what to do instead.
  List<Widget> _smsSection(WalletAction a) {
    if (!a.canCheckSms) return const [];
    final text = Theme.of(context).textTheme;
    if (!s.sms.supported) {
      return const [
        SizedBox(height: 12),
        Callout('This phone cannot let the app read messages. Send the M-Pesa confirmation message to the assistant in the Chat tab and it will be kept with this request.'),
        SizedBox(height: 12),
      ];
    }
    return [
      const SizedBox(height: 4),
      OutlinedButton.icon(
        onPressed: _busy || _checking ? null : () => _checkSms(),
        icon: _checking
            ? const SizedBox(height: 18, width: 18, child: CircularProgressIndicator(strokeWidth: 2))
            : const Icon(Icons.sms_outlined),
        label: const Text('Check my M-Pesa message'),
      ),
      const SizedBox(height: 6),
      Text('The app looks only at messages from MPESA and sends us the code and amount, not the message.', style: text.bodySmall),
      const SizedBox(height: 14),
    ];
  }
}

/// Shows [build] when [value] has text, otherwise nothing.
Widget ifText(String? value, Widget Function(String) build) =>
    value == null || value.isEmpty ? const SizedBox.shrink() : build(value);


/// One step of the M-Pesa hand-off: a number, what to do, and a copy button when there is a value to type.
class _StepRow extends StatelessWidget {
  const _StepRow({required this.number, required this.text, this.value, this.copyLabel, this.onCopy});

  final int number;
  final String text;
  final String? value;
  final String? copyLabel;
  final VoidCallback? onCopy;

  @override
  Widget build(BuildContext context) {
    final t = context.tk;
    final style = Theme.of(context).textTheme;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(crossAxisAlignment: CrossAxisAlignment.center, children: [
        Container(
          width: 26,
          height: 26,
          alignment: Alignment.center,
          decoration: BoxDecoration(
            color: t.p.accentSoft,
            shape: t.square ? BoxShape.rectangle : BoxShape.circle,
            border: Border.all(color: t.p.accentLine),
          ),
          child: Text('$number', style: style.labelMedium?.copyWith(color: t.p.ink)),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(text, style: style.bodyMedium),
            if (value != null) Padding(padding: const EdgeInsets.only(top: 2), child: Text(value!, style: t.numeric(17, weight: FontWeight.w600))),
          ]),
        ),
        if (onCopy != null)
          IconButton(
            icon: const Icon(Icons.copy, size: 20),
            tooltip: 'Copy ${copyLabel ?? 'value'}',
            onPressed: onCopy,
          ),
      ]),
    );
  }
}
