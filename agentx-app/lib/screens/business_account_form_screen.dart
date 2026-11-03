import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../api/api_client.dart';
import '../app_state.dart';
import '../layout/responsive.dart';
import '../models/business_account.dart';
import '../theme/app_theme.dart';
import '../widgets/ui.dart';

final RegExp _shortcodePattern = RegExp(r'^\d{5,7}$');
final RegExp _noSpace = RegExp(r'^\S+$');

/// Adds a business account or changes one.
///
/// A head account holds the shortcode and the Daraja credentials. A branch is a Buy Goods till or a Paybill account
/// reference that belongs to a head and uses the head's credentials, so they are typed once. Pops with true when
/// something was saved or removed, so the list reads again.
class BusinessAccountFormScreen extends StatefulWidget {
  const BusinessAccountFormScreen({super.key, required this.state, this.account, this.parent});

  final AppState state;

  /// The account being changed, or null to add one.
  final BusinessAccount? account;

  /// The head a new branch is added to, or the head of the branch being changed.
  final BusinessAccount? parent;

  @override
  State<BusinessAccountFormScreen> createState() => _BusinessAccountFormScreenState();
}

class _BusinessAccountFormScreenState extends State<BusinessAccountFormScreen> {
  final _form = GlobalKey<FormState>();

  late DarajaEnv _env = widget.account?.environment ?? widget.parent?.environment ?? DarajaEnv.sandbox;
  late ShortcodeKind _kind = widget.account?.kind ?? widget.parent?.kind ?? ShortcodeKind.buyGoods;

  late final _label = TextEditingController(text: widget.account?.label ?? '');
  late final _shortcode = TextEditingController(text: widget.account?.shortcode ?? '');
  late final _till = TextEditingController(text: widget.account?.tillNumber ?? '');
  late final _reference = TextEditingController(text: widget.account?.accountReference ?? '');
  final _key = TextEditingController();
  final _secret = TextEditingController();
  final _passkey = TextEditingController();
  late final _initiatorName = TextEditingController(text: widget.account?.initiatorName ?? '');
  final _initiatorPassword = TextEditingController();

  bool _busy = false;
  bool _checking = false;
  String? _error;
  VerifyOutcome? _outcome;

  bool get _editing => widget.account != null;
  bool get _branch => widget.parent != null || (widget.account?.isBranch ?? false);

  @override
  void dispose() {
    for (final c in [_label, _shortcode, _till, _reference, _key, _secret, _passkey, _initiatorName, _initiatorPassword]) {
      c.dispose();
    }
    super.dispose();
  }

  // ------------------------------------------------------------ actions

  Future<void> _save() async {
    if (_busy) return;
    if (!(_form.currentState?.validate() ?? false)) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await widget.state.api.saveBusinessAccount(_fields(), id: widget.account?.id);
      if (!mounted) return;
      Navigator.of(context).pop(true);
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
        _error = 'The account could not be saved. Please try again.';
      });
    }
  }

  /// Only what was typed is sent. A secret left empty on an existing account keeps the saved one.
  Map<String, dynamic> _fields() {
    String? text(TextEditingController c) {
      final v = c.text.trim();
      return v.isEmpty ? null : v;
    }

    final out = <String, dynamic>{'label': _label.text.trim()};
    if (_branch) {
      out['parentId'] = widget.parent?.id ?? widget.account?.parentId;
      if (_kind == ShortcodeKind.buyGoods) {
        out['tillNumber'] = _till.text.trim();
      } else {
        out['accountReference'] = _reference.text.trim();
      }
      return out;
    }
    out['environment'] = _env.wire;
    out['shortcodeType'] = _kind.wire;
    out['shortcode'] = _shortcode.text.trim();
    final extras = <String, String?>{
      'consumerKey': text(_key),
      'consumerSecret': text(_secret),
      'passkey': text(_passkey),
      'initiatorName': text(_initiatorName),
      'initiatorPassword': text(_initiatorPassword),
    };
    extras.forEach((k, v) {
      if (v != null) out[k] = v;
    });
    return out;
  }

  Future<void> _check() async {
    final account = widget.account;
    if (account == null || _checking) return;
    setState(() {
      _checking = true;
      _outcome = null;
      _error = null;
    });
    try {
      final result = await widget.state.api.verifyBusinessAccount(account.id);
      if (!mounted) return;
      setState(() {
        _checking = false;
        _outcome = result;
      });
    } on ApiException catch (e) {
      if (e.unauthorized) await widget.state.sessionExpired();
      if (!mounted) return;
      setState(() {
        _checking = false;
        _error = e.message;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _checking = false;
        _error = 'Safaricom could not be reached. Please try again.';
      });
    }
  }

  Future<void> _remove() async {
    final account = widget.account;
    if (account == null) return;
    final ok = await showDialog<bool>(
      context: context,
      builder: (c) => AppDialog(
        title: Text(account.isBranch ? 'Remove this branch?' : 'Remove this account?'),
        content: Text(account.isBranch
            ? 'The saved numbers for "${account.label}" are deleted. The head account stays.'
            : 'The saved credentials for "${account.label}" and all of its branches are deleted from Daraja Agent-X. This does not change anything on Daraja.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('Keep')),
          TextButton(onPressed: () => Navigator.pop(c, true), child: const Text('Remove')),
        ],
      ),
    );
    if (ok != true || !mounted) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await widget.state.api.deleteBusinessAccount(account.id);
      if (!mounted) return;
      Navigator.of(context).pop(true);
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
        _error = 'The account could not be removed. Please try again.';
      });
    }
  }

  // ------------------------------------------------------------ build

  @override
  Widget build(BuildContext context) {
    final title = _editing
        ? (_branch ? 'Edit branch' : 'Edit account')
        : (_branch ? 'Add a branch' : 'Add a business account');
    return Scaffold(
      appBar: AppBar(title: Text(title)),
      body: SafeArea(
        top: false,
        child: Form(
          key: _form,
          child: CenteredList(maxWidth: 560, children: [
            if (_editing) ..._summary(context),
            if (_branch) _parentNote(context),
            _basics(context),
            if (!_branch) ..._credentials(context),
            const SizedBox(height: 16),
            if (_error != null) ...[Callout(_error!, tone: Tone.bad), const SizedBox(height: 12)],
            FilledButton(
              onPressed: _busy ? null : _save,
              child: _busy
                  ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2))
                  : Text(_editing ? 'Save changes' : 'Save account'),
            ),
            if (_editing) ...[
              const SizedBox(height: 8),
              OutlinedButton(onPressed: _busy ? null : _remove, child: Text(_branch ? 'Remove this branch' : 'Remove this account')),
            ],
          ]),
        ),
      ),
    );
  }

  Widget _parentNote(BuildContext context) {
    final head = widget.parent;
    final text = head == null
        ? 'This branch uses the credentials of its head account.'
        : 'This branch uses the credentials of "${head.label}" (${head.headline}, ${head.environment.label}). You only add the ${_kind == ShortcodeKind.buyGoods ? 'till number' : 'account reference'} here.';
    return Padding(padding: const EdgeInsets.only(bottom: 16), child: Callout(text, tone: Tone.info));
  }

  Widget _basics(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final locked = _branch;
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      TextFormField(
        controller: _label,
        textCapitalization: TextCapitalization.sentences,
        maxLength: 60,
        decoration: const InputDecoration(labelText: 'Name', helperText: 'A name you will recognise, for example "Main shop".'),
        validator: (v) => (v ?? '').trim().isEmpty ? 'Give the account a name.' : null,
      ),
      const SizedBox(height: 8),
      Text('Environment', style: text.labelLarge),
      const SizedBox(height: 6),
      SegmentedButton<DarajaEnv>(
        segments: const [
          ButtonSegment(value: DarajaEnv.sandbox, label: Text('Sandbox')),
          ButtonSegment(value: DarajaEnv.production, label: Text('Production')),
        ],
        selected: {_env},
        onSelectionChanged: locked ? null : (s) => setState(() => _env = s.first),
      ),
      const SizedBox(height: 16),
      Text('Shortcode type', style: text.labelLarge),
      const SizedBox(height: 6),
      SegmentedButton<ShortcodeKind>(
        segments: const [
          ButtonSegment(value: ShortcodeKind.buyGoods, label: Text('Buy Goods')),
          ButtonSegment(value: ShortcodeKind.paybill, label: Text('Paybill')),
        ],
        selected: {_kind},
        onSelectionChanged: locked ? null : (s) => setState(() => _kind = s.first),
      ),
      const SizedBox(height: 16),
      TextFormField(
        controller: _shortcode,
        enabled: !locked,
        keyboardType: TextInputType.number,
        inputFormatters: [FilteringTextInputFormatter.digitsOnly, LengthLimitingTextInputFormatter(7)],
        decoration: InputDecoration(
          labelText: _kind == ShortcodeKind.paybill ? 'Paybill number' : 'Shortcode (head office)',
          helperText: locked
              ? 'Taken from the head account.'
              : _kind == ShortcodeKind.paybill
                  ? '5 to 7 digits.'
                  : '5 to 7 digits. The head office number you registered on Daraja. Tills are added as branches.',
        ),
        validator: (v) => locked || _shortcodePattern.hasMatch((v ?? '').trim()) ? null : 'Enter 5 to 7 digits.',
      ),
      if (_branch && _kind == ShortcodeKind.buyGoods) ...[
        const SizedBox(height: 16),
        TextFormField(
          controller: _till,
          keyboardType: TextInputType.number,
          inputFormatters: [FilteringTextInputFormatter.digitsOnly, LengthLimitingTextInputFormatter(7)],
          decoration: const InputDecoration(labelText: 'Till number', helperText: '5 to 7 digits.'),
          validator: (v) => _shortcodePattern.hasMatch((v ?? '').trim()) ? null : 'Enter the 5 to 7 digit till number.',
        ),
      ],
      if (_branch && _kind == ShortcodeKind.paybill) ...[
        const SizedBox(height: 16),
        TextFormField(
          controller: _reference,
          textCapitalization: TextCapitalization.characters,
          inputFormatters: [FilteringTextInputFormatter.allow(RegExp(r'[A-Za-z0-9]')), LengthLimitingTextInputFormatter(12)],
          decoration: const InputDecoration(labelText: 'Account reference', helperText: 'Letters and numbers, up to 12. For example the branch code.'),
          validator: (v) => (v ?? '').trim().isEmpty ? 'Enter the account reference for this branch.' : null,
        ),
      ],
    ]);
  }

  List<Widget> _credentials(BuildContext context) {
    final saved = widget.account;
    final text = Theme.of(context).textTheme;
    return [
      const SizedBox(height: 20),
      const SectionTitle('Daraja credentials'),
      const Callout(
        'Secrets are encrypted on the server and are never shown again. The security credential is made for you from the initiator password, you do not need to create it.',
        tone: Tone.info,
        icon: Icons.lock_outline,
      ),
      const SizedBox(height: 16),
      _SecretField(
        controller: _key,
        label: 'Consumer key',
        savedNote: saved != null && saved.hasKey ? 'Saved, ends in ${saved.consumerKeyHint}. Leave empty to keep it.' : null,
        mandatory: !_editing,
        minLength: 8,
      ),
      const SizedBox(height: 16),
      _SecretField(
        controller: _secret,
        label: 'Consumer secret',
        savedNote: saved != null && saved.hasConsumerSecret ? 'Saved. Leave empty to keep it.' : null,
        mandatory: !_editing,
        minLength: 8,
      ),
      const SizedBox(height: 16),
      _SecretField(
        controller: _passkey,
        label: 'STK Push passkey',
        savedNote: saved != null && saved.hasPasskey ? 'Saved. Leave empty to keep it.' : null,
        help: 'Needed for STK Push and STK Push query.',
        minLength: 20,
      ),
      const SizedBox(height: 20),
      Text('Initiator', style: text.titleSmall),
      const SizedBox(height: 4),
      Text('Needed for B2C, B2B, Pochi la Biashara, account balance, transaction status and reversals.', style: text.bodySmall),
      const SizedBox(height: 12),
      TextFormField(
        controller: _initiatorName,
        autocorrect: false,
        enableSuggestions: false,
        inputFormatters: [FilteringTextInputFormatter.deny(RegExp(r'\s'))],
        decoration: const InputDecoration(labelText: 'Initiator name', helperText: 'The API operator username.'),
        validator: (v) {
          final value = (v ?? '').trim();
          if (value.isEmpty) return null;
          return value.length >= 2 && value.length <= 50 ? null : 'Use 2 to 50 characters.';
        },
      ),
      const SizedBox(height: 16),
      _SecretField(
        controller: _initiatorPassword,
        label: 'Initiator password',
        savedNote: saved != null && saved.hasInitiatorPassword ? 'Saved. Leave empty to keep it.' : null,
        minLength: 6,
        extra: (v) => v.isEmpty && _initiatorName.text.trim().isNotEmpty && !(saved?.hasInitiatorPassword ?? false)
            ? 'Add the password for this initiator.'
            : null,
      ),
    ];
  }

  List<Widget> _summary(BuildContext context) {
    final account = widget.account!;
    final t = context.tk;
    final text = Theme.of(context).textTheme;
    final outcome = _outcome;
    return [
      Panel(
        padding: const EdgeInsets.all(14),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Wrap(spacing: 8, runSpacing: 8, children: [
            StatusTag(account.environment.label, tone: account.environment == DarajaEnv.production ? Tone.warn : Tone.neutral),
            StatusTag(account.kind.label),
            StatusTag(account.verifiedAt == null ? 'Not checked yet' : 'Checked with Safaricom', tone: account.verifiedAt == null ? Tone.neutral : Tone.good),
          ]),
          const SizedBox(height: 12),
          Text('What this account can do', style: text.titleSmall),
          const SizedBox(height: 8),
          for (final c in account.capabilities)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 3),
              child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Icon(c.ready ? Icons.check_circle_outline : Icons.radio_button_unchecked, size: 18, color: c.ready ? t.p.good : t.p.ink2),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(c.ready ? c.label : '${c.label}. Needs the ${c.needs}.', style: text.bodyMedium?.copyWith(color: c.ready ? t.p.ink : t.p.ink2)),
                ),
              ]),
            ),
          const SizedBox(height: 12),
          OutlinedButton.icon(
            onPressed: _checking || _busy ? null : _check,
            icon: _checking ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)) : const Icon(Icons.verified_outlined),
            label: Text(_checking ? 'Checking' : 'Check with Safaricom'),
          ),
          if (outcome != null) ...[
            const SizedBox(height: 12),
            Callout(outcome.message, tone: outcome.ok ? Tone.good : Tone.bad),
            if (outcome.securityCredentialMessage.isNotEmpty) ...[
              const SizedBox(height: 8),
              Callout(outcome.securityCredentialMessage, tone: outcome.securityCredentialReady ? Tone.good : Tone.warn),
            ],
          ],
        ]),
      ),
      const SizedBox(height: 20),
    ];
  }
}

/// A text box for a secret. It hides the characters until the eye is pressed, and never offers suggestions.
class _SecretField extends StatefulWidget {
  const _SecretField({required this.controller, required this.label, this.savedNote, this.help, this.mandatory = false, this.minLength = 1, this.extra});

  final TextEditingController controller;
  final String label;

  /// Shown instead of [help] when a value is already saved.
  final String? savedNote;
  final String? help;
  final bool mandatory;
  final int minLength;
  final String? Function(String value)? extra;

  @override
  State<_SecretField> createState() => _SecretFieldState();
}

class _SecretFieldState extends State<_SecretField> {
  bool _hidden = true;

  @override
  Widget build(BuildContext context) {
    return TextFormField(
      controller: widget.controller,
      obscureText: _hidden,
      autocorrect: false,
      enableSuggestions: false,
      keyboardType: TextInputType.visiblePassword,
      inputFormatters: [FilteringTextInputFormatter.deny(RegExp(r'\s'))],
      decoration: InputDecoration(
        labelText: widget.label,
        helperText: widget.savedNote ?? widget.help,
        helperMaxLines: 2,
        suffixIcon: IconButton(
          tooltip: _hidden ? 'Show' : 'Hide',
          icon: Icon(_hidden ? Icons.visibility_outlined : Icons.visibility_off_outlined),
          onPressed: () => setState(() => _hidden = !_hidden),
        ),
      ),
      validator: (v) {
        final value = (v ?? '').trim();
        final extra = widget.extra?.call(value);
        if (extra != null) return extra;
        if (value.isEmpty) return widget.mandatory ? 'This is required.' : null;
        if (value.startsWith('PASTE_')) return 'Replace the placeholder with the real value.';
        if (!_noSpace.hasMatch(value) || value.length < widget.minLength) return 'Check you copied all of it, at least ${widget.minLength} characters.';
        return null;
      },
    );
  }
}
