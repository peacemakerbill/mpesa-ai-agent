import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../app_state.dart';
import '../models/progress.dart';
import '../models/wallet_action.dart';
import '../theme/app_theme.dart';
import '../widgets/ui.dart';
import 'action_screen.dart';

class ActionsScreen extends StatefulWidget {
  const ActionsScreen({super.key, required this.state});

  final AppState state;

  @override
  State<ActionsScreen> createState() => _ActionsScreenState();
}

class _ActionsScreenState extends State<ActionsScreen> {
  late Future<List<WalletAction>> _future = _load();

  Future<List<WalletAction>> _load() async {
    try {
      return await widget.state.api.actions();
    } on ApiException catch (e) {
      if (e.unauthorized) await widget.state.sessionExpired();
      rethrow;
    }
  }

  void _refresh() => setState(() => _future = _load());

  Future<void> _open(WalletAction a) async {
    await Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => ActionScreen(state: widget.state, actionId: a.id)));
    if (mounted) _refresh();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Requests'),
        actions: [
          IconButton(onPressed: () => showThemeSheet(context, widget.state), icon: const Icon(Icons.palette_outlined), tooltip: 'Appearance'),
          IconButton(onPressed: _refresh, icon: const Icon(Icons.refresh), tooltip: 'Refresh'),
        ],
      ),
      body: FutureBuilder<List<WalletAction>>(
        future: _future,
        builder: (context, snap) {
          if (snap.connectionState != ConnectionState.done) return const Center(child: CircularProgressIndicator());
          if (snap.hasError) {
            return ListView(padding: const EdgeInsets.all(20), children: [
              Callout('${snap.error}', tone: Tone.bad),
              const SizedBox(height: 12),
              FilledButton(onPressed: _refresh, child: const Text('Try again')),
            ]);
          }
          final items = snap.data ?? const <WalletAction>[];
          if (items.isEmpty) return const _Empty();
          final waiting = items.where((a) => a.isOpen).toList();
          final earlier = items.where((a) => !a.isOpen).toList();
          return RefreshIndicator(
            onRefresh: () async => _refresh(),
            child: ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
              children: [
                if (waiting.isNotEmpty) ...[
                  const _GroupTitle('Needs you'),
                  for (final a in waiting) _RequestCard(a, onTap: () => _open(a)),
                ],
                if (earlier.isNotEmpty) ...[
                  const _GroupTitle('Earlier'),
                  for (final a in earlier) _RequestCard(a, onTap: () => _open(a)),
                ],
              ],
            ),
          );
        },
      ),
    );
  }
}

class _GroupTitle extends StatelessWidget {
  const _GroupTitle(this.text);

  final String text;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.fromLTRB(4, 16, 4, 8),
        child: Text(text, style: Theme.of(context).textTheme.labelLarge?.copyWith(color: context.tk.p.ink2)),
      );
}

class _RequestCard extends StatelessWidget {
  const _RequestCard(this.a, {required this.onTap});

  final WalletAction a;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final t = context.tk;
    final text = Theme.of(context).textTheme;
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: Semantics(
        button: true,
        label: '${a.summary}. ${a.statusText}',
        child: Panel(
          onTap: onTap,
          edge: a.isOpen ? t.p.accent : null,
          child: Row(crossAxisAlignment: CrossAxisAlignment.center, children: [
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(typeLabel(a.type), style: text.labelMedium),
                const SizedBox(height: 4),
                Text(a.summary, style: text.titleMedium),
                const SizedBox(height: 10),
                StatusTag(a.statusText, tone: toneForStatus(a.status)),
              ]),
            ),
            const SizedBox(width: 12),
            Column(crossAxisAlignment: CrossAxisAlignment.end, children: [
              Text('KES', style: text.labelSmall),
              Text(formatKes(a.amount), style: t.numeric(22)),
            ]),
          ]),
        ),
      ),
    );
  }
}

class _Empty extends StatelessWidget {
  const _Empty();

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          const BrandMark(size: 44),
          const SizedBox(height: 16),
          Text('No requests yet', style: text.titleLarge),
          const SizedBox(height: 8),
          Text(
            'Ask the assistant in the Chat tab, for example "send 500 to Mama" or "pay Till 123456". The request shows up here to approve.',
            textAlign: TextAlign.center,
            style: text.bodyMedium?.copyWith(color: context.tk.p.ink2),
          ),
        ]),
      ),
    );
  }
}
