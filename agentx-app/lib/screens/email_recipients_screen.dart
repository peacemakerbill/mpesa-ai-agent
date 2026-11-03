import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../app_state.dart';
import '../layout/responsive.dart';
import '../models/profile.dart';
import '../profile/signup_flow.dart';
import '../theme/app_theme.dart';
import '../widgets/ui.dart';
import 'csv_import_screen.dart';

/// The email addresses the customer sends reports to. They can type one, paste a list, or upload a CSV or text file.
///
/// In the chat they can also say "send this report to accounts@example.com", or "send it to my email" (the verified address on
/// their profile, which does not need to be on this list).
class EmailRecipientsScreen extends StatefulWidget {
  const EmailRecipientsScreen({super.key, required this.state, this.pick = pickCsvFile});

  final AppState state;

  /// Lets the customer choose a file. Tests pass a fake.
  final CsvPicker pick;

  @override
  State<EmailRecipientsScreen> createState() => _EmailRecipientsScreenState();
}

class _EmailRecipientsScreenState extends State<EmailRecipientsScreen> {
  List<EmailRecipient>? _items;
  String? _error;
  String? _result;
  bool _busy = false;

  ApiClient get _api => widget.state.api;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() => _error = null);
    try {
      final list = await _api.emailRecipients();
      if (mounted) setState(() => _items = list);
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.message);
    }
  }

  void _say(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  Future<void> _add() async {
    final entry = await showDialog<({String email, String name})>(
      context: context,
      builder: (c) => const _AddDialog(),
    );
    if (entry == null) return;
    setState(() => _busy = true);
    try {
      await _api.addEmailRecipient(entry.email, name: entry.name);
      setState(() => _result = null);
      await _load();
    } on ApiException catch (e) {
      _say(e.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _paste() async {
    final text = await showDialog<String>(context: context, builder: (c) => const _PasteDialog());
    if (text == null || text.trim().isEmpty) return;
    setState(() => _busy = true);
    try {
      final done = await _api.pasteEmailRecipients(text);
      if (mounted) setState(() => _result = done.summary);
      await _load();
    } on ApiException catch (e) {
      _say(e.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _upload() async {
    final PickedCsv? file;
    try {
      file = await widget.pick();
    } on Exception {
      _say('The file could not be opened.');
      return;
    }
    if (file == null) return;
    if (file.bytes.length > maxCsvBytes) {
      _say('That file is bigger than 1 MB. Split it into smaller files.');
      return;
    }
    setState(() => _busy = true);
    try {
      final done = await _api.importEmailRecipients(file.bytes, fileName: file.name);
      if (mounted) setState(() => _result = done.summary);
      await _load();
    } on ApiException catch (e) {
      _say(e.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _remove(EmailRecipient r) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (c) => AppDialog(
        title: const Text('Remove this address?'),
        content: Text(r.label),
        actions: [
          TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('Keep it')),
          TextButton(onPressed: () => Navigator.pop(c, true), child: const Text('Remove')),
        ],
      ),
    );
    if (ok != true) return;
    try {
      await _api.removeEmailRecipient(r.id);
      await _load();
    } on ApiException catch (e) {
      _say(e.message);
    }
  }

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final t = context.tk;
    final items = _items;
    return Scaffold(
      appBar: AppBar(title: const Text('Email addresses')),
      body: SafeArea(
        top: false,
        child: items == null
            ? (_error != null
                ? EmptyState(title: 'Could not load your addresses', message: _error!, children: [
                    FilledButton(onPressed: _load, child: const Text('Try again')),
                  ])
                : const Center(child: CircularProgressIndicator()))
            : CenteredList(maxWidth: 560, children: [
                Text(
                  'People you send reports to. Say in the chat, for example, "send this report to my accountant as Excel". '
                  '"My email" always means the verified address on your profile.',
                  style: text.bodyMedium?.copyWith(color: t.p.ink2),
                ),
                const SizedBox(height: 14),
                Wrap(spacing: 8, runSpacing: 8, children: [
                  FilledButton.icon(onPressed: _busy ? null : _add, icon: const Icon(Icons.add), label: const Text('Add one')),
                  OutlinedButton.icon(onPressed: _busy ? null : _paste, icon: const Icon(Icons.content_paste), label: const Text('Paste a list')),
                  OutlinedButton.icon(onPressed: _busy ? null : _upload, icon: const Icon(Icons.upload_file), label: const Text('Upload CSV or text')),
                ]),
                if (_result != null) ...[
                  const SizedBox(height: 12),
                  Callout(_result!, tone: Tone.good),
                ],
                const SizedBox(height: 16),
                if (items.isEmpty)
                  const Callout('No addresses yet. Add one, paste a list, or upload a file with one address on each line.')
                else
                  Panel(
                    padding: EdgeInsets.zero,
                    child: Column(children: [
                      for (var i = 0; i < items.length; i++) ...[
                        if (i > 0) Divider(height: 1, color: t.p.line),
                        ListTile(
                          title: Text(items[i].name.isEmpty ? items[i].email : items[i].name),
                          subtitle: items[i].name.isEmpty ? null : Text(items[i].email),
                          trailing: IconButton(
                            onPressed: () => _remove(items[i]),
                            icon: const Icon(Icons.delete_outline),
                            tooltip: 'Remove ${items[i].email}',
                          ),
                        ),
                      ],
                    ]),
                  ),
              ]),
      ),
    );
  }
}

class _AddDialog extends StatefulWidget {
  const _AddDialog();

  @override
  State<_AddDialog> createState() => _AddDialogState();
}

class _AddDialogState extends State<_AddDialog> {
  final _email = TextEditingController();
  final _name = TextEditingController();
  String? _error;

  @override
  void dispose() {
    _email.dispose();
    _name.dispose();
    super.dispose();
  }

  void _submit() {
    if (!looksLikeEmail(_email.text)) {
      setState(() => _error = 'That email address does not look right.');
      return;
    }
    Navigator.pop(context, (email: _email.text.trim(), name: _name.text.trim()));
  }

  @override
  Widget build(BuildContext context) => AppDialog(
        title: const Text('Add an address'),
        content: Column(mainAxisSize: MainAxisSize.min, children: [
          TextField(
            controller: _email,
            autofocus: true,
            keyboardType: TextInputType.emailAddress,
            textInputAction: TextInputAction.next,
            decoration: InputDecoration(labelText: 'Email address', errorText: _error),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _name,
            textCapitalization: TextCapitalization.words,
            decoration: const InputDecoration(labelText: 'Name (optional)', hintText: 'Accountant'),
            onSubmitted: (_) => _submit(),
          ),
        ]),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
          TextButton(onPressed: _submit, child: const Text('Save')),
        ],
      );
}

class _PasteDialog extends StatefulWidget {
  const _PasteDialog();

  @override
  State<_PasteDialog> createState() => _PasteDialogState();
}

class _PasteDialogState extends State<_PasteDialog> {
  final _text = TextEditingController();

  @override
  void dispose() {
    _text.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => AppDialog(
        title: const Text('Paste a list'),
        content: TextField(
          controller: _text,
          autofocus: true,
          minLines: 4,
          maxLines: 8,
          keyboardType: TextInputType.multiline,
          decoration: const InputDecoration(
            hintText: 'One address on each line, or separated by commas. Anything that is not an email address is skipped.',
            alignLabelWithHint: true,
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
          TextButton(onPressed: () => Navigator.pop(context, _text.text), child: const Text('Save addresses')),
        ],
      );
}
