import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../app_state.dart';
import '../layout/responsive.dart';
import '../models/business_account.dart';
import '../theme/app_theme.dart';
import '../widgets/ui.dart';
import 'business_account_form_screen.dart';
import 'business_operations_screen.dart';
import 'csv_import_screen.dart';

/// Puts every head account first, each followed by its branches.
List<({BusinessAccount head, List<BusinessAccount> branches})> groupAccounts(List<BusinessAccount> all) {
  final heads = all.where((a) => !a.isBranch).toList();
  final out = <({BusinessAccount head, List<BusinessAccount> branches})>[];
  for (final h in heads) {
    out.add((head: h, branches: all.where((a) => a.parentId == h.id).toList()));
  }
  return out;
}

/// The businesses the customer can move money for through the chat: shortcodes, tills and their Daraja credentials.
class BusinessAccountsScreen extends StatefulWidget {
  const BusinessAccountsScreen({super.key, required this.state});

  final AppState state;

  @override
  State<BusinessAccountsScreen> createState() => _BusinessAccountsScreenState();
}

class _BusinessAccountsScreenState extends State<BusinessAccountsScreen> {
  List<BusinessAccount>? _items;
  String? _error;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load({bool quiet = false}) async {
    if (!quiet) {
      setState(() {
        _loading = true;
        _error = null;
      });
    }
    try {
      final list = await widget.state.api.businessAccounts();
      if (!mounted) return;
      setState(() {
        _items = list;
        _error = null;
        _loading = false;
      });
    } on ApiException catch (e) {
      if (e.unauthorized) await widget.state.sessionExpired();
      if (!mounted) return;
      setState(() {
        _loading = false;
        if (!quiet || _items == null) _error = e.message;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _loading = false;
        if (!quiet || _items == null) _error = 'Your business accounts could not be loaded.';
      });
    }
  }

  Future<void> _open(Widget screen, {String? doneMessage}) async {
    final changed = await Navigator.of(context).push<bool>(MaterialPageRoute<bool>(builder: (_) => screen));
    if (!mounted) return;
    if (changed == true) {
      await _load(quiet: true);
      if (!mounted) return;
      if (doneMessage != null) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(doneMessage)));
    }
  }

  void _add() => _open(BusinessAccountFormScreen(state: widget.state), doneMessage: 'Account saved.');

  void _addBranch(BusinessAccount head) =>
      _open(BusinessAccountFormScreen(state: widget.state, parent: head), doneMessage: 'Branch saved.');

  void _addStore(BusinessAccount head) =>
      _open(BusinessAccountFormScreen(state: widget.state, parent: head, startWithOwnShortcode: true), doneMessage: 'Saved.');

  void _edit(BusinessAccount account) {
    BusinessAccount? parent;
    if (account.parentId != null) {
      for (final a in _items ?? const <BusinessAccount>[]) {
        if (a.id == account.parentId) parent = a;
      }
    }
    _open(BusinessAccountFormScreen(state: widget.state, account: account, parent: parent), doneMessage: 'Done.');
  }

  void _import() => _open(CsvImportScreen(state: widget.state), doneMessage: 'Accounts imported.');

  void _openRequests() => Navigator.of(context).push<void>(MaterialPageRoute<void>(builder: (_) => BusinessOperationsScreen(state: widget.state)));

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Business accounts'),
        actions: [
          IconButton(onPressed: _openRequests, icon: const Icon(Icons.history), tooltip: 'Business requests'),
          IconButton(onPressed: _import, icon: const Icon(Icons.upload_file_outlined), tooltip: 'Upload a CSV file'),
          IconButton(onPressed: _loading ? null : _load, icon: const Icon(Icons.refresh), tooltip: 'Refresh'),
        ],
      ),
      body: SafeArea(top: false, child: _body(context)),
    );
  }

  Widget _body(BuildContext context) {
    final items = _items;
    if (items == null) {
      if (_loading) return const Center(child: CircularProgressIndicator());
      return CenteredList(maxWidth: 560, top: 20, children: [
        Callout(_error ?? 'Your business accounts could not be loaded.', tone: Tone.bad),
        const SizedBox(height: 12),
        FilledButton(onPressed: _load, child: const Text('Try again')),
      ]);
    }
    if (items.isEmpty) {
      return EmptyState(
        title: 'No business accounts yet',
        message: 'Add your Paybill or Buy Goods shortcode with its Daraja credentials, then run STK Push, B2C, balance and other requests from the chat. Many tills? Upload them in one CSV file.',
        children: [
          FilledButton.icon(onPressed: _add, icon: const Icon(Icons.add), label: const Text('Add an account')),
          const SizedBox(height: 8),
          OutlinedButton.icon(onPressed: _import, icon: const Icon(Icons.upload_file_outlined), label: const Text('Upload a CSV file')),
        ],
      );
    }
    final groups = groupAccounts(items);
    return RefreshIndicator(
      onRefresh: () => _load(quiet: true),
      child: CenteredList(
        maxWidth: 640,
        physics: const AlwaysScrollableScrollPhysics(),
        children: [
          if (_error != null) ...[Callout(_error!, tone: Tone.bad), const SizedBox(height: 12)],
          Row(children: [
            Expanded(child: FilledButton.icon(onPressed: _add, icon: const Icon(Icons.add), label: const Text('Add account'))),
            const SizedBox(width: 8),
            Expanded(child: OutlinedButton.icon(onPressed: _import, icon: const Icon(Icons.upload_file_outlined), label: const Text('Upload CSV'))),
          ]),
          const SizedBox(height: 16),
          for (final g in groups) ...[
            _HeadCard(
              head: g.head,
              branches: g.branches,
              onOpen: _edit,
              onAddBranch: () => _addBranch(g.head),
              onAddStore: () => _addStore(g.head),
            ),
            const SizedBox(height: 12),
          ],
        ],
      ),
    );
  }
}

class _HeadCard extends StatelessWidget {
  const _HeadCard({required this.head, required this.branches, required this.onOpen, required this.onAddBranch, required this.onAddStore});

  final BusinessAccount head;
  final List<BusinessAccount> branches;
  final void Function(BusinessAccount) onOpen;
  final VoidCallback onAddBranch;
  final VoidCallback onAddStore;

  static String _childLine(BusinessAccount b) {
    if (b.ownShortcode) {
      final own = b.holdsOwnCredentials ? 'own credentials' : 'head office credentials';
      return '${b.headline}. ${own[0].toUpperCase()}${own.substring(1)}.';
    }
    return b.kind == ShortcodeKind.buyGoods ? 'Till ${b.tillNumber}' : 'Account ${b.accountReference}';
  }

  @override
  Widget build(BuildContext context) {
    final t = context.tk;
    final text = Theme.of(context).textTheme;
    return Panel(
      padding: EdgeInsets.zero,
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        InkWell(
          onTap: () => onOpen(head),
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Row(children: [
                Expanded(child: Text(head.label, style: text.titleMedium, maxLines: 1, overflow: TextOverflow.ellipsis)),
                const Icon(Icons.chevron_right),
              ]),
              const SizedBox(height: 2),
              Text(head.headline, style: text.bodyMedium?.copyWith(color: t.p.ink2)),
              const SizedBox(height: 10),
              Wrap(spacing: 8, runSpacing: 8, children: [
                StatusTag(head.environment.label, tone: head.environment == DarajaEnv.production ? Tone.warn : Tone.neutral),
                StatusTag(head.kind.label),
                StatusTag('${head.readyCount} of ${head.capabilities.length} ready', tone: head.readyCount == 0 ? Tone.neutral : Tone.info),
                if (head.verifiedAt != null) const StatusTag('Checked', tone: Tone.good),
              ]),
            ]),
          ),
        ),
        for (final b in branches) ...[
          Divider(height: 1, color: t.p.line),
          InkWell(
            onTap: () => onOpen(b),
            child: Padding(
              padding: const EdgeInsets.fromLTRB(28, 12, 14, 12),
              child: Row(children: [
                Icon(Icons.subdirectory_arrow_right, size: 18, color: t.p.ink2),
                const SizedBox(width: 8),
                Expanded(
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text(b.label, style: text.bodyLarge, maxLines: 1, overflow: TextOverflow.ellipsis),
                    Text(_childLine(b), style: text.bodySmall),
                  ]),
                ),
                const Icon(Icons.chevron_right, size: 20),
              ]),
            ),
          ),
        ],
        Divider(height: 1, color: t.p.line),
        Wrap(alignment: WrapAlignment.center, children: [
          TextButton.icon(
            onPressed: onAddBranch,
            icon: const Icon(Icons.add, size: 18),
            label: Text(head.kind == ShortcodeKind.buyGoods ? 'Add a till' : 'Add an account reference'),
          ),
          TextButton.icon(
            onPressed: onAddStore,
            icon: const Icon(Icons.add_business_outlined, size: 18),
            label: Text(head.kind == ShortcodeKind.buyGoods ? 'Add a store number' : 'Add a child shortcode'),
          ),
        ]),
      ]),
    );
  }
}
