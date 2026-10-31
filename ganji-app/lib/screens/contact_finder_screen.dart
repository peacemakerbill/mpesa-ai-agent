import 'package:flutter/material.dart';

import '../contacts/contact_models.dart';
import '../contacts/contact_services.dart';
import '../contacts/finder_controller.dart';
import '../theme/app_theme.dart';
import '../widgets/ui.dart';

/// Search for a person in this phone, a Google or Microsoft account, or a file, or type a number.
/// Pops with a [PickedContact]. Nothing here is sent to our server.
class ContactFinderScreen extends StatefulWidget {
  const ContactFinderScreen({super.key, required this.services, this.title = 'Find a contact', this.query = ''});

  final ContactServices services;
  final String title;
  final String query;

  @override
  State<ContactFinderScreen> createState() => _ContactFinderScreenState();
}

class _ContactFinderScreenState extends State<ContactFinderScreen> {
  late final ContactFinderController _c = widget.services.newFinder(query: widget.query);
  late final TextEditingController _query = TextEditingController(text: widget.query);
  final _manualName = TextEditingController();
  final _manualPhone = TextEditingController();
  String? _manualError;

  @override
  void initState() {
    super.initState();
    // Search at once when the assistant gave us a name to look for.
    if (widget.query.trim().isNotEmpty) WidgetsBinding.instance.addPostFrameCallback((_) => _c.search());
  }

  @override
  void dispose() {
    _c.dispose();
    _query.dispose();
    _manualName.dispose();
    _manualPhone.dispose();
    super.dispose();
  }

  void _pick(String name, String number) {
    final phone = kenyanPhone(number);
    if (phone.isEmpty) return;
    Navigator.of(context).pop(PickedContact(name, phone));
  }

  Future<void> _systemChooser() async {
    final person = await _c.chooseWithSystemPicker();
    if (!mounted || person == null) return;
    if (person.phones.length == 1) {
      _pick(person.name, person.phones.first.number);
    } else {
      final number = await _askNumber(person);
      if (number != null) _pick(person.name, number);
    }
  }

  Future<String?> _askNumber(ContactEntry person) => showDialog<String>(
        context: context,
        builder: (c) => SimpleDialog(
          backgroundColor: c.tk.p.surface,
          surfaceTintColor: Colors.transparent,
          shape: c.tk.shape(scale: 1.4),
          title: Text('Which number for ${person.name}?'),
          children: [
            for (final p in person.phones)
              SimpleDialogOption(onPressed: () => Navigator.pop(c, p.number), child: Text(p.label.isEmpty ? p.number : '${p.number}  (${p.label})')),
          ],
        ),
      );

  void _useManual() {
    final phone = kenyanPhone(_manualPhone.text);
    if (!looksLikeNumber(phone)) {
      setState(() => _manualError = 'Enter a phone number, for example 0712 345 678.');
      return;
    }
    _pick(_manualName.text.trim().isEmpty ? phone : _manualName.text.trim(), phone);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(widget.title)),
      body: SafeArea(
        child: ListenableBuilder(
          listenable: _c,
          builder: (context, _) => Column(children: [
            _sources(context),
            if (_c.source == ContactSourceKind.manual) Expanded(child: _manual(context)) else ...[
              _searchBar(context),
              Expanded(child: _results(context)),
            ],
          ]),
        ),
      ),
    );
  }

  Widget _sources(BuildContext context) {
    final t = context.tk;
    final kinds = [
      for (final k in ContactSourceKind.values)
        if (k != ContactSourceKind.microsoft || _c.microsoftConfigured) k,
    ];
    return SizedBox(
      height: 56,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.fromLTRB(16, 10, 16, 6),
        itemCount: kinds.length,
        separatorBuilder: (_, __) => const SizedBox(width: 8),
        itemBuilder: (_, i) {
          final k = kinds[i];
          final on = k == _c.source;
          return ChoiceChip(
            label: Text(k.label),
            selected: on,
            onSelected: (_) => _c.selectSource(k),
            selectedColor: t.p.accentSoft,
            backgroundColor: t.p.surface,
            side: BorderSide(color: on ? t.p.accentLine : t.p.line),
            shape: t.shape(outline: false, scale: 0.7),
            showCheckmark: false,
          );
        },
      ),
    );
  }

  Widget _searchBar(BuildContext context) {
    final network = _c.source == ContactSourceKind.google || _c.source == ContactSourceKind.microsoft;
    final file = _c.source == ContactSourceKind.file;
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 4, 16, 8),
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        if (file)
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: OutlinedButton.icon(
              onPressed: _c.importFile,
              icon: const Icon(Icons.upload_file_outlined),
              label: Text(_c.fileName == null ? 'Choose a .vcf or .csv file' : 'Change file (${_c.fileName})'),
            ),
          ),
        TextField(
          controller: _query,
          textInputAction: TextInputAction.search,
          onChanged: _c.setQuery,
          onSubmitted: (_) => _c.search(),
          decoration: InputDecoration(
            labelText: 'Name or number',
            prefixIcon: const Icon(Icons.search),
            suffixIcon: network || _c.source == ContactSourceKind.phone
                ? IconButton(onPressed: _c.busy ? null : _c.search, icon: const Icon(Icons.arrow_forward), tooltip: 'Search')
                : null,
          ),
        ),
      ]),
    );
  }

  Widget _results(BuildContext context) {
    final t = context.tk;
    final text = Theme.of(context).textTheme;
    if (_c.busy) return const Center(child: CircularProgressIndicator());
    final children = <Widget>[];
    if (_c.error != null) {
      children.add(Callout(_c.error!, tone: Tone.bad));
      children.add(const SizedBox(height: 10));
    }
    if (_c.source == ContactSourceKind.phone) {
      children.add(Align(
        alignment: Alignment.centerLeft,
        child: TextButton.icon(onPressed: _systemChooser, icon: const Icon(Icons.contacts_outlined), label: const Text('Use the phone\'s own chooser')),
      ));
    }
    if (_c.results.isEmpty && _c.error == null) {
      children.add(Padding(
        padding: const EdgeInsets.only(top: 24),
        child: Text(_hint(), textAlign: TextAlign.center, style: text.bodyMedium?.copyWith(color: t.p.ink2)),
      ));
    }
    for (final person in _c.results) {
      children.add(Padding(
        padding: const EdgeInsets.only(bottom: 8),
        child: Panel(
          padding: const EdgeInsets.symmetric(vertical: 8),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 4, 16, 2),
              child: Text(person.name, style: text.titleMedium),
            ),
            if (person.from != null)
              Padding(padding: const EdgeInsets.symmetric(horizontal: 16), child: Text(person.from!, style: text.bodySmall)),
            for (final p in person.phones)
              InkWell(
                onTap: () => _pick(person.name, p.number),
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                  child: Row(children: [
                    Icon(Icons.phone_outlined, size: 18, color: t.p.ink2),
                    const SizedBox(width: 10),
                    Expanded(child: Text(kenyanPhone(p.number), style: t.numeric(16, weight: FontWeight.w600))),
                    if (p.label.isNotEmpty) Text(p.label, style: text.bodySmall),
                  ]),
                ),
              ),
          ]),
        ),
      ));
    }
    return ListView(padding: const EdgeInsets.fromLTRB(16, 0, 16, 24), children: children);
  }

  String _hint() {
    switch (_c.source) {
      case ContactSourceKind.phone:
        return _c.searched ? 'Nobody on this phone matches.' : 'Press search to look in this phone\'s contacts.';
      case ContactSourceKind.google:
        return _c.searched ? 'Nobody in your Google contacts matches.' : 'Search, then sign in to Google when asked. We only read contacts, and only while you search.';
      case ContactSourceKind.microsoft:
        return _c.searched ? 'Nobody in your Outlook contacts matches.' : 'Search, then sign in to Microsoft when asked.';
      case ContactSourceKind.file:
        return _c.hasFile ? 'Nobody in the file matches.' : 'Export your contacts from any address book as .vcf or .csv, then choose the file here.';
      case ContactSourceKind.manual:
        return '';
    }
  }

  Widget _manual(BuildContext context) {
    final text = Theme.of(context).textTheme;
    return ListView(padding: const EdgeInsets.fromLTRB(16, 8, 16, 24), children: [
      TextField(controller: _manualName, textCapitalization: TextCapitalization.words, decoration: const InputDecoration(labelText: 'Name (optional)')),
      const SizedBox(height: 12),
      TextField(
        controller: _manualPhone,
        keyboardType: TextInputType.phone,
        decoration: const InputDecoration(labelText: 'Phone number', hintText: '0712 345 678'),
        onSubmitted: (_) => _useManual(),
      ),
      if (_manualError != null) ...[const SizedBox(height: 8), Callout(_manualError!, tone: Tone.bad)],
      const SizedBox(height: 16),
      FilledButton(onPressed: _useManual, child: const Text('Use this number')),
      const SizedBox(height: 12),
      Text('Contacts you search stay on this phone. Only the person you choose is used.', style: text.bodySmall),
    ]);
  }
}
