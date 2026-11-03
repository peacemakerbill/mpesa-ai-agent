import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../app_state.dart';
import '../layout/responsive.dart';
import '../models/business_operation.dart';
import '../models/progress.dart';
import '../theme/app_theme.dart';
import '../widgets/ui.dart';
import 'business_operation_screen.dart';

/// What was asked on the business accounts from the chat, newest first. Tap one to see it, or to approve it if it is waiting.
class BusinessOperationsScreen extends StatefulWidget {
  const BusinessOperationsScreen({super.key, required this.state});

  final AppState state;

  @override
  State<BusinessOperationsScreen> createState() => _BusinessOperationsScreenState();
}

class _BusinessOperationsScreenState extends State<BusinessOperationsScreen> {
  List<BusinessOperation>? _items;
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
      final list = await widget.state.api.businessOperations();
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
        if (!quiet || _items == null) _error = 'Your requests could not be loaded.';
      });
    }
  }

  Future<void> _open(BusinessOperation op) async {
    await Navigator.of(context).push<void>(MaterialPageRoute<void>(builder: (_) => BusinessOperationScreen(state: widget.state, operationId: op.id)));
    if (mounted) await _load(quiet: true);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Business requests'),
        actions: [IconButton(onPressed: _loading ? null : _load, icon: const Icon(Icons.refresh), tooltip: 'Refresh')],
      ),
      body: SafeArea(top: false, child: _body(context)),
    );
  }

  Widget _body(BuildContext context) {
    final items = _items;
    if (items == null) {
      if (_loading) return const Center(child: CircularProgressIndicator());
      return CenteredList(maxWidth: 560, top: 20, children: [
        Callout(_error ?? 'Your requests could not be loaded.', tone: Tone.bad),
        const SizedBox(height: 12),
        FilledButton(onPressed: _load, child: const Text('Try again')),
      ]);
    }
    if (items.isEmpty) {
      return const EmptyState(
        title: 'Nothing here yet',
        message: 'Ask the assistant in the chat, for example "Check the balance on Main shop" or "@Main shop STK push 500 to 0712345678". What you ask shows here.',
      );
    }
    final t = context.tk;
    final text = Theme.of(context).textTheme;
    return RefreshIndicator(
      onRefresh: () => _load(quiet: true),
      child: CenteredList(
        maxWidth: 640,
        physics: const AlwaysScrollableScrollPhysics(),
        children: [
          if (_error != null) ...[Callout(_error!, tone: Tone.bad), const SizedBox(height: 12)],
          for (final op in items) ...[
            Panel(
              onTap: () => _open(op),
              edge: op.stage == OperationStage.waiting ? t.p.warn : null,
              child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Expanded(
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text(op.summary, style: text.titleSmall, maxLines: 2, overflow: TextOverflow.ellipsis),
                    const SizedBox(height: 4),
                    Text(
                      [
                        if (op.amount > 0) 'KES ${formatKes(op.amount)}',
                        op.accountLabel,
                        if (op.createdAt != null) clockTime(op.createdAt!),
                      ].join(', '),
                      style: text.bodySmall?.copyWith(color: t.p.ink2),
                    ),
                  ]),
                ),
                const SizedBox(width: 8),
                StatusTag(op.stageLabel, tone: toneForOperation(op.stage)),
              ]),
            ),
            const SizedBox(height: 8),
          ],
        ],
      ),
    );
  }
}
