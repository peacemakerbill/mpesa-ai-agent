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

  /// Select mode: the finished requests ticked so far. An open request cannot be cleared, so it cannot be ticked.
  bool _selecting = false;
  final Set<String> _picked = {};
  bool _busy = false;

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

  void _toast(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  List<WalletAction> get _finished => [
        for (final a in _items ?? const <WalletAction>[])
          if (!a.isOpen) a,
      ];

  void _startSelecting([WalletAction? first]) {
    setState(() {
      _selecting = true;
      _picked.clear();
      if (first != null && !first.isOpen) _picked.add(first.id);
    });
  }

  void _stopSelecting() {
    setState(() {
      _selecting = false;
      _picked.clear();
    });
  }

  void _toggle(WalletAction a) {
    if (a.isOpen) {
      _toast('Finish or cancel this request before clearing it.');
      return;
    }
    setState(() {
      if (!_picked.remove(a.id)) _picked.add(a.id);
    });
  }

  void _selectAll() {
    setState(() {
      _picked
        ..clear()
        ..addAll(_finished.map((a) => a.id));
    });
  }

  Future<bool> _confirmClear(String title, String detail) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (c) => AppDialog(
        title: Text(title),
        content: Text('$detail They leave this list. The records are kept, so your reports still include them.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('Keep')),
          TextButton(onPressed: () => Navigator.pop(c, true), child: const Text('Clear')),
        ],
      ),
    );
    return ok == true && mounted;
  }

  /// Runs one clear call, says what happened in a sentence, and reads the list again.
  Future<void> _run(Future<ClearResult> Function() call) async {
    setState(() => _busy = true);
    try {
      final result = await call();
      _toast(result.sentence);
      await _load(quiet: true);
    } on ApiException catch (e) {
      if (e.unauthorized) await widget.state.sessionExpired();
      _toast(e.message);
    } catch (_) {
      _toast('The requests could not be cleared. Check your internet connection and try again.');
    } finally {
      if (mounted) {
        setState(() {
          _busy = false;
          _selecting = false;
          _picked.clear();
        });
      }
    }
  }

  Future<void> _clearOne(WalletAction a) async {
    if (!await _confirmClear('Clear this request?', '"${a.summary}".')) return;
    await _run(() => widget.state.api.clearAction(a.id));
  }

  Future<void> _clearPicked() async {
    final ids = [
      for (final a in _finished)
        if (_picked.contains(a.id)) a.id,
    ];
    if (ids.isEmpty) return;
    final what = ids.length == 1 ? 'The request you ticked.' : 'The ${ids.length} requests you ticked.';
    if (!await _confirmClear(ids.length == 1 ? 'Clear this request?' : 'Clear ${ids.length} requests?', what)) return;
    await _run(() => widget.state.api.clearActions(ids));
  }

  Future<void> _clearAllFinished() async {
    final count = _finished.length;
    if (count == 0) {
      _toast('There are no finished requests to clear.');
      return;
    }
    final what = count == 1 ? 'The one finished request.' : 'All $count finished requests.';
    if (!await _confirmClear('Clear finished requests?', '$what Requests that still need you stay.')) return;
    await _run(widget.state.api.clearFinishedActions);
  }

  Future<void> _open(WalletAction a) async {
    await Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => ActionScreen(state: widget.state, actionId: a.id)));
    if (mounted) _load(quiet: true);
  }

  @override
  Widget build(BuildContext context) {
    final hasFinished = _finished.isNotEmpty;
    return Scaffold(
      appBar: _selecting
          ? AppBar(
              leading: IconButton(onPressed: _busy ? null : _stopSelecting, icon: const Icon(Icons.close), tooltip: 'Cancel selection'),
              title: Text(_picked.isEmpty ? 'Select requests' : '${_picked.length} selected'),
              actions: [
                IconButton(onPressed: _busy ? null : _selectAll, icon: const Icon(Icons.select_all), tooltip: 'Select all finished'),
                IconButton(onPressed: _busy || _picked.isEmpty ? null : _clearPicked, icon: const Icon(Icons.delete_outline), tooltip: 'Clear selected'),
              ],
            )
          : AppBar(
              title: const Text('Requests'),
              actions: [
                IconButton(onPressed: () => showThemeSheet(context, widget.state), icon: const Icon(Icons.palette_outlined), tooltip: 'Appearance'),
                IconButton(onPressed: _loading ? null : _load, icon: const Icon(Icons.refresh), tooltip: 'Refresh'),
                PopupMenuButton<String>(
                  tooltip: 'More',
                  enabled: !_busy,
                  onSelected: (v) => v == 'select' ? _startSelecting() : _clearAllFinished(),
                  itemBuilder: (_) => [
                    PopupMenuItem(value: 'select', enabled: hasFinished, child: const Text('Select requests')),
                    PopupMenuItem(value: 'all', enabled: hasFinished, child: const Text('Clear finished requests')),
                  ],
                ),
              ],
              bottom: _busy ? const PreferredSize(preferredSize: Size.fromHeight(2), child: LinearProgressIndicator(minHeight: 2)) : null,
            ),
      body: SafeArea(top: false, child: _body(context)),
    );
  }

  /// One request. Normally a tap opens it. A finished one has a small clear button, and a long press starts select mode.
  /// In select mode a tap ticks a finished request, and an open one is shown dimmed because it cannot be cleared.
  Widget _card(WalletAction a) {
    if (!_selecting) {
      return _RequestCard(
        a,
        onTap: () => _open(a),
        onClear: a.isOpen || _busy ? null : () => _clearOne(a),
        onLongPress: a.isOpen ? null : () => _startSelecting(a),
      );
    }
    final card = _RequestCard(a, onTap: () => _toggle(a));
    if (a.isOpen) return Opacity(opacity: 0.5, child: card);
    return Row(crossAxisAlignment: CrossAxisAlignment.center, children: [
      Checkbox(value: _picked.contains(a.id), onChanged: _busy ? null : (_) => _toggle(a)),
      Expanded(child: card),
    ]);
  }

  Widget _body(BuildContext context) {
    final items = _items;
    if (items == null) {
      if (_loading) return const BrandLoader();
      return CenteredList(maxWidth: 560, top: 20, children: [
        Callout(_error ?? 'Your requests could not be loaded.', tone: Tone.bad),
        const SizedBox(height: 12),
        FilledButton(onPressed: _load, child: const Text('Try again')),
      ]);
    }
    if (items.isEmpty) {
      return const EmptyState(
        title: 'No requests yet',
        message: 'Ask Daraja Agent-X in the chat, for example "send 500 to Mama" or "pay Till 123456". The request shows up here, ready to approve.',
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
            for (final a in waiting) _card(a),
          ],
          if (earlier.isNotEmpty) ...[
            _GroupTitle('Earlier', count: earlier.length, quiet: true),
            for (final a in earlier) _card(a),
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
  const _RequestCard(this.a, {required this.onTap, this.onClear, this.onLongPress});

  final WalletAction a;
  final VoidCallback onTap;

  /// Shown as a small close button on a finished request.
  final VoidCallback? onClear;
  final VoidCallback? onLongPress;

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
            onLongPress: onLongPress,
            highlight: a.isOpen,
            stubWidth: box.maxWidth < 360 ? 92 : 116,
            main: Column(crossAxisAlignment: CrossAxisAlignment.start, mainAxisAlignment: MainAxisAlignment.center, children: [
              Row(children: [
                Expanded(child: Text(typeLabel(a.type), style: text.labelMedium)),
                if (onClear != null)
                  SizedBox(
                    width: 28,
                    height: 28,
                    child: IconButton(padding: EdgeInsets.zero, iconSize: 18, tooltip: 'Clear this request', onPressed: onClear, icon: const Icon(Icons.close)),
                  ),
              ]),
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
