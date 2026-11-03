import 'dart:io';
import 'dart:typed_data';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:open_filex/open_filex.dart';
import 'package:path_provider/path_provider.dart';

import '../api/api_client.dart';
import '../app_state.dart';
import '../layout/responsive.dart';
import '../models/business_account.dart';
import '../theme/app_theme.dart';
import '../widgets/ui.dart';

/// A CSV file the customer chose.
typedef PickedCsv = ({String name, Uint8List bytes});

/// Lets the customer choose a CSV file. Returns null when they cancel.
typedef CsvPicker = Future<PickedCsv?> Function();

/// Opens the example file in an app that can edit it.
typedef TemplateOpener = Future<void> Function(String csv);

const int maxCsvBytes = 1024 * 1024;

Future<PickedCsv?> pickCsvFile() async {
  final result = await FilePicker.platform.pickFiles(type: FileType.custom, allowedExtensions: const ['csv', 'txt'], withData: true);
  final file = result?.files.firstOrNull;
  final bytes = file?.bytes;
  if (file == null || bytes == null) return null;
  return (name: file.name, bytes: bytes);
}

Future<void> openTemplateFile(String csv) async {
  final dir = await getTemporaryDirectory();
  final file = File('${dir.path}/agentx-business-accounts-template.csv');
  await file.writeAsString(csv, flush: true);
  final result = await OpenFilex.open(file.path);
  if (result.type != ResultType.done) {
    throw Exception(result.type == ResultType.noAppToOpen ? 'No app on this device can open a CSV file.' : 'The file could not be opened.');
  }
}

/// Adds many business accounts at once from a CSV file.
///
/// The file is checked first without saving anything. The customer sees what would be created or changed, and every problem
/// with its row, and saves only when the file is clean.
class CsvImportScreen extends StatefulWidget {
  const CsvImportScreen({super.key, required this.state, this.pick = pickCsvFile, this.openTemplate = openTemplateFile});

  final AppState state;
  final CsvPicker pick;
  final TemplateOpener openTemplate;

  @override
  State<CsvImportScreen> createState() => _CsvImportScreenState();
}

class _CsvImportScreenState extends State<CsvImportScreen> {
  PickedCsv? _file;
  ImportResult? _result;
  String? _error;
  String? _note;
  bool _busy = false;

  Future<void> _fail(Object e, String fallback) async {
    if (e is ApiException) {
      if (e.unauthorized) await widget.state.sessionExpired();
      if (!mounted) return;
      setState(() => _error = e.message);
    } else {
      if (!mounted) return;
      setState(() => _error = fallback);
    }
  }

  Future<String> _template() => widget.state.api.businessCsvTemplate();

  Future<void> _copyTemplate() async {
    setState(() {
      _error = null;
      _note = null;
    });
    try {
      final csv = await _template();
      await Clipboard.setData(ClipboardData(text: csv));
      if (!mounted) return;
      setState(() => _note = 'The example file is copied. Paste it into a spreadsheet or a text editor and save it as a .csv file.');
    } catch (e) {
      await _fail(e, 'The example file could not be loaded.');
    }
  }

  Future<void> _openTemplate() async {
    setState(() {
      _error = null;
      _note = null;
    });
    try {
      await widget.openTemplate(await _template());
    } catch (e) {
      await _fail(e is Exception && e is! ApiException ? ApiException(0, e.toString().replaceFirst('Exception: ', '')) : e, 'The example file could not be opened. Use Copy example instead.');
    }
  }

  Future<void> _choose() async {
    setState(() {
      _error = null;
      _note = null;
    });
    final PickedCsv? picked;
    try {
      picked = await widget.pick();
    } catch (_) {
      if (!mounted) return;
      setState(() => _error = 'The file chooser could not be opened on this device.');
      return;
    }
    if (picked == null || !mounted) return;
    if (picked.bytes.length > maxCsvBytes) {
      setState(() => _error = 'That file is larger than 1 MB. Split it into smaller files of up to 500 accounts.');
      return;
    }
    setState(() {
      _file = picked;
      _result = null;
    });
    await _send(dryRun: true);
  }

  Future<void> _send({required bool dryRun}) async {
    final file = _file;
    if (file == null || _busy) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final result = await widget.state.api.importBusinessCsv(file.bytes, fileName: file.name, dryRun: dryRun);
      if (!mounted) return;
      if (!dryRun && result.saved) {
        Navigator.of(context).pop(true);
        return;
      }
      setState(() {
        _busy = false;
        _result = result;
      });
    } catch (e) {
      if (mounted) setState(() => _busy = false);
      await _fail(e, 'The file could not be checked. Please try again.');
    }
  }

  @override
  Widget build(BuildContext context) {
    final result = _result;
    return Scaffold(
      appBar: AppBar(title: const Text('Upload a CSV file')),
      body: SafeArea(
        top: false,
        child: CenteredList(maxWidth: 640, children: [
          _instructions(context),
          const SizedBox(height: 16),
          Wrap(spacing: 8, runSpacing: 8, children: [
            OutlinedButton.icon(onPressed: _busy ? null : _copyTemplate, icon: const Icon(Icons.copy_outlined), label: const Text('Copy example')),
            OutlinedButton.icon(onPressed: _busy ? null : _openTemplate, icon: const Icon(Icons.open_in_new), label: const Text('Open example')),
          ]),
          if (_note != null) ...[const SizedBox(height: 12), Callout(_note!, tone: Tone.good)],
          const SizedBox(height: 20),
          FilledButton.icon(
            onPressed: _busy ? null : _choose,
            icon: _busy && result == null ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2)) : const Icon(Icons.upload_file_outlined),
            label: Text(_file == null ? 'Choose a CSV file' : 'Choose another file'),
          ),
          if (_file != null) ...[
            const SizedBox(height: 8),
            Text(_file!.name, style: Theme.of(context).textTheme.bodySmall),
          ],
          if (_error != null) ...[const SizedBox(height: 12), Callout(_error!, tone: Tone.bad)],
          if (result != null) ..._preview(context, result),
        ]),
      ),
    );
  }

  Widget _instructions(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final t = context.tk;
    Widget step(String n, String body) => Padding(
          padding: const EdgeInsets.only(bottom: 6),
          child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
            SizedBox(width: 22, child: Text(n, style: text.bodyMedium?.copyWith(color: t.p.ink2))),
            Expanded(child: Text(body, style: text.bodyMedium)),
          ]),
        );
    return Panel(
      padding: const EdgeInsets.all(14),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text('How it works', style: text.titleSmall),
        const SizedBox(height: 8),
        step('1', 'Copy or open the example file and replace the PASTE_ values with your own.'),
        step('2', 'Save it as a .csv file. Up to 500 accounts and 1 MB.'),
        step('3', 'Choose the file here. Nothing is saved yet, you first see what would change.'),
        step('4', 'Fix any problem it lists, choose the file again, then save.'),
        const SizedBox(height: 6),
        Text('Rules', style: text.titleSmall),
        const SizedBox(height: 6),
        for (final rule in const [
          'The first line is the header. The order of columns does not matter, and you can leave out columns you do not use.',
          'A head row has the shortcode and the credentials. One head per shortcode and environment.',
          'A shortcode with many tills or branches: add one more row per till. Leave shortcode empty and write the head shortcode in parent_shortcode. Buy Goods branches fill till_number, Paybill branches fill account_reference.',
          'A store number or child shortcode has its own number: write it in shortcode, and the head office in parent_shortcode. A Buy Goods store number also fills till_number (the till linked to it). A Paybill child has no till.',
          'A row without a shortcode is a till or branch that shares its head office\'s shortcode and credentials, so it carries none. A store number that went live on its own can carry its own credentials. Leave out the ones it does not have: the key and secret, and the initiator, then come from the head office. The passkey is only ever its own.',
          'A row for an account that is already saved updates it. Leave consumer_secret empty to keep the saved one.',
          'Commas, semicolons or tabs can separate the columns. Use UTF-8 text.',
          'If any row has a problem, nothing at all is saved.',
        ])
          Padding(
            padding: const EdgeInsets.only(bottom: 4),
            child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Padding(padding: const EdgeInsets.only(top: 7, right: 8), child: Icon(Icons.circle, size: 5, color: t.p.ink2)),
              Expanded(child: Text(rule, style: text.bodySmall)),
            ]),
          ),
        Theme(
          data: Theme.of(context).copyWith(dividerColor: Colors.transparent),
          child: ExpansionTile(
            tilePadding: EdgeInsets.zero,
            childrenPadding: EdgeInsets.zero,
            title: Text('The columns', style: text.titleSmall),
            children: [
              for (final c in businessCsvColumns)
                Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Row(children: [
                      Text(c.name, style: text.bodyMedium?.copyWith(fontWeight: FontWeight.w700, fontFamily: 'monospace')),
                      const SizedBox(width: 8),
                      Flexible(child: Text(c.need, style: text.labelMedium?.copyWith(color: t.p.ink2))),
                    ]),
                    Text(c.help, style: text.bodySmall),
                  ]),
                ),
            ],
          ),
        ),
      ]),
    );
  }

  List<Widget> _preview(BuildContext context, ImportResult r) {
    final text = Theme.of(context).textTheme;
    final t = context.tk;
    final n = r.total;
    return [
      const SizedBox(height: 20),
      SectionTitle(r.clean ? 'Ready to save' : 'Fix these first'),
      Text(
        r.clean
            ? '$n ${n == 1 ? 'account' : 'accounts'}: ${r.created} to add, ${r.updated} to update.'
            : '${r.issues.length} ${r.issues.length == 1 ? 'problem' : 'problems'} in the file. Nothing has been saved.',
        style: text.bodyMedium,
      ),
      if (r.ignoredColumns.isNotEmpty) ...[
        const SizedBox(height: 10),
        Callout('These columns were not recognised and were ignored: ${r.ignoredColumns.join(', ')}.', tone: Tone.warn),
      ],
      if (!r.clean) ...[
        const SizedBox(height: 10),
        for (final i in r.issues)
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: Callout('${i.where}${i.field.isEmpty ? '' : ', ${i.field}'}: ${i.message}', tone: Tone.bad),
          ),
      ],
      if (r.rows.isNotEmpty) ...[
        const SizedBox(height: 12),
        Panel(
          padding: EdgeInsets.zero,
          child: Column(children: [
            for (var i = 0; i < r.rows.length; i++) ...[
              if (i > 0) Divider(height: 1, color: t.p.line),
              _PreviewRow(row: r.rows[i]),
            ],
          ]),
        ),
      ],
      if (r.clean && n > 0) ...[
        const SizedBox(height: 16),
        FilledButton(
          onPressed: _busy ? null : () => _send(dryRun: false),
          child: _busy ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2)) : Text('Save $n ${n == 1 ? 'account' : 'accounts'}'),
        ),
      ],
    ];
  }
}

class _PreviewRow extends StatelessWidget {
  const _PreviewRow({required this.row});

  final ImportRow row;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final t = context.tk;
    final ownCode = row.parent.isNotEmpty && row.shortcode.isNotEmpty && row.shortcode != row.parent;
    final what = ownCode
        ? '${row.tillNumber.isNotEmpty ? 'Store ${row.shortcode}, till ${row.tillNumber}' : 'Child shortcode ${row.shortcode}'} under ${row.parent}'
        : row.parent.isNotEmpty
            ? '${row.tillNumber.isNotEmpty ? 'Till ${row.tillNumber}' : 'Account ${row.accountReference}'} under ${row.shortcode.isNotEmpty ? row.shortcode : row.parent}'
            : '${row.kind == 'buy_goods' ? 'Buy Goods' : 'Paybill'} ${row.shortcode}';
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      child: Row(children: [
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(row.label.isEmpty ? what : row.label, style: text.bodyLarge, maxLines: 1, overflow: TextOverflow.ellipsis),
            Text('Row ${row.row}. $what${row.environment.isEmpty ? '' : ', ${row.environment}'}', style: text.bodySmall?.copyWith(color: t.p.ink2)),
          ]),
        ),
        const SizedBox(width: 8),
        StatusTag(row.action == 'update' ? 'Update' : 'Add', tone: row.action == 'update' ? Tone.warn : Tone.good),
      ]),
    );
  }
}
