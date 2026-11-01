import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../app_state.dart';
import '../layout/responsive.dart';
import '../models/progress.dart';
import '../models/wallet_action.dart';
import '../theme/app_theme.dart';
import '../widgets/ui.dart';
import 'action_screen.dart';

/// Every request the assistant prepared, the ones that need the customer first.
class ActionsScreen extends StatefulWidget {
  const ActionsScreen({super.key, required this.state, this.refresh});

  final AppState state;

  /// When this changes the list is read again, without showing the spinner. The home screen uses it when a new request arrives.
  final Listenable? refresh;

  @override
  State<ActionsScreen> createState() => _ActionsScreenState();
}

class _ActionsScreenState extends State<ActionsScreen> {
  List<WalletAction>? _items;
  String? _error;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    widget.refresh?.addListener(_quietRefresh);
    _load();
  }

  @override
  void didUpdateWidget(covariant ActionsScreen old) {
    super.didUpdateWidget(old);
    if (old.refresh != widget.refresh) {
      old.refresh?.removeListener(_quietRefresh);
      widget.refresh?.addListener(_quietRefresh);
    }
  }

  @override
  void dispose() {
    widget.refresh?.removeListener(_quietRefresh);
    super.dispose();
  }

  void _quietRefresh() {
    if (mounted) _load(quiet: true);
  }

  Future<void> _load({bool quiet = false}) async {
    if (!quiet) {
      setState(() {
        _loading = true;
        _error = null;
      });
    }
    try {
      final list = await widget.state.api.actions();
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

  Future<void> _open(WalletAction a) async {
    await Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => ActionScreen(state: widget.state, actionId: a.id)));
    if (mounted) _load(quiet: true);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Requests'),
        actions: [
          IconButton(onPressed: () => showThemeSheet(context, widget.state), icon: const Icon(Icons.palette_outlined), tooltip: 'Appearance'),
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
        Callout(_error ?? 'Your requests could not be loaded.', tone: Tone.bad),
        const SizedBox(height: 12),
        FilledButton(onPressed: _load, child: const Text('Try again')),
      ]);
    }
    if (items.isEmpty) {
      return const EmptyState(
        title: 'No requests yet',
        message: 'Ask M-Ganji in the chat, for example "send 500 to Mama" or "pay Till 123456". The request shows up here, ready to approve.',
      );
    }
    final waiting = items.where((a) => a.isOpen).toList();
    final earlier = items.where((a) => !a.isOpen).toList();
    return RefreshIndicator(
      onRefresh: () => _load(quiet: true),
      child: CenteredList(
        maxWidth: 640,
        top: 8,
        physics: const AlwaysScrollableScrollPhysics(),
        children: [
          if (_error != null) ...[
            const SizedBox(height: 8),
            Callout(_error!, tone: Tone.bad),
          ],
          if (waiting.isNotEmpty) ...[
            _GroupTitle('Needs you', count: waiting.length),
            for (final a in waiting) _RequestCard(a, onTap: () => _open(a)),
          ],
          if (earlier.isNotEmpty) ...[
            _GroupTitle('Earlier', count: earlier.length, quiet: true),
            for (final a in earlier) _RequestCard(a, onTap: () => _open(a)),
          ],
        ],
      ),
    );
  }
}

class _GroupTitle extends StatelessWidget {
  const _GroupTitle(this.text, {required this.count, this.quiet = false});

  final String text;
  final int count;
  final bool quiet;

  @override
  Widget build(BuildContext context) {
    final t = context.tk;
    return Padding(
      padding: const EdgeInsets.fromLTRB(4, 18, 4, 10),
      child: Row(children: [
        Text(text, style: Theme.of(context).textTheme.titleMedium),
        const SizedBox(width: 8),
        if (quiet)
          Text('$count', style: Theme.of(context).textTheme.labelMedium?.copyWith(color: t.p.ink3))
        else
          CountBadge(count),
      ]),
    );
  }
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
      padding: const EdgeInsets.only(bottom: 12),
      child: LayoutBuilder(
        builder: (context, box) => Semantics(
          button: true,
          label: '${a.summary}. ${a.statusText}',
          child: TicketCard(
            onTap: onTap,
            highlight: a.isOpen,
            stubWidth: box.maxWidth < 360 ? 92 : 116,
            main: Column(crossAxisAlignment: CrossAxisAlignment.start, mainAxisAlignment: MainAxisAlignment.center, children: [
              Text(typeLabel(a.type), style: text.labelMedium),
              const SizedBox(height: 4),
              Text(a.summary, style: text.titleMedium, maxLines: 3, overflow: TextOverflow.ellipsis),
              const SizedBox(height: 10),
              StatusTag(a.statusText, tone: toneForStatus(a.status)),
            ]),
            stub: Column(mainAxisAlignment: MainAxisAlignment.center, crossAxisAlignment: CrossAxisAlignment.end, children: [
              Text('KES', style: text.labelSmall),
              const SizedBox(height: 2),
              FittedBox(fit: BoxFit.scaleDown, alignment: Alignment.centerRight, child: Text(formatKes(a.amount), style: t.numeric(24))),
            ]),
          ),
        ),
      ),
    );
  }
}
