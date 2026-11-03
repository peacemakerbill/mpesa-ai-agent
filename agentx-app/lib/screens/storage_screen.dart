import 'package:flutter/material.dart';

import '../chat/media_cache.dart';
import '../layout/responsive.dart';
import '../theme/app_theme.dart';
import '../widgets/ui.dart';

/// Photos, voice notes and documents from the chat: how much of this phone they use, and where else they are kept.
class StorageScreen extends StatefulWidget {
  const StorageScreen({super.key, this.cache});

  /// For tests. The app uses [MediaCache.shared].
  final MediaCache? cache;

  @override
  State<StorageScreen> createState() => _StorageScreenState();
}

class _StorageScreenState extends State<StorageScreen> {
  MediaCache? _cache;
  int _used = 0;
  int _count = 0;
  int _limit = MediaCache.defaultLimit;
  bool _loading = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final cache = widget.cache ?? await MediaCache.shared();
      final used = await cache.usedBytes();
      final count = await cache.fileCount();
      final limit = await cache.limitBytes();
      if (!mounted) return;
      setState(() {
        _cache = cache;
        _used = used;
        _count = count;
        _limit = limit;
        _loading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _loading = false;
        _error = 'The storage on this phone could not be read.';
      });
    }
  }

  Future<void> _setLimit(int bytes) async {
    final cache = _cache;
    if (cache == null) return;
    await cache.setLimitBytes(bytes);
    await _load();
  }

  Future<void> _clear() async {
    final cache = _cache;
    if (cache == null) return;
    final ok = await showDialog<bool>(
      context: context,
      builder: (c) => AppDialog(
        title: const Text('Remove files from this phone?'),
        content: const Text('Photos and documents from the chat are removed from this phone only. They stay on the server until they expire, and open again when you tap them.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('Keep them')),
          TextButton(onPressed: () => Navigator.pop(c, true), child: const Text('Remove')),
        ],
      ),
    );
    if (ok != true) return;
    await cache.clear();
    await _load();
    if (mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Files removed from this phone.')));
  }

  static String _limitLabel(int bytes) => bytes == 0 ? 'Off' : formatBytes(bytes).replaceAll('.0', '');

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final t = context.tk;
    return Scaffold(
      appBar: AppBar(title: const Text('Files and storage')),
      body: SafeArea(
        top: false,
        child: _loading
            ? const Center(child: CircularProgressIndicator())
            : CenteredList(maxWidth: 560, children: [
                if (_error != null) Panel(edge: t.p.bad, child: Text(_error!, style: text.bodyMedium)),
                if (_error == null) ...[
                  const SectionTitle('On this phone'),
                  KeyValueList([
                    MapEntry('Files', '$_count'),
                    MapEntry('Space used', formatBytes(_used)),
                    MapEntry('Limit', _limitLabel(_limit)),
                  ]),
                  const SizedBox(height: 12),
                  Text('Files you open in the chat are kept here so they open at once. When the limit is reached the ones you opened longest ago are removed first. Choose Off to keep nothing on this phone.', style: text.bodySmall),
                  const SizedBox(height: 12),
                  Wrap(spacing: 8, runSpacing: 8, children: [
                    for (final choice in MediaCache.limitChoices)
                      ChoiceChip(label: Text(_limitLabel(choice)), selected: _limit == choice, onSelected: (_) => _setLimit(choice)),
                  ]),
                  const SizedBox(height: 12),
                  Align(
                    alignment: Alignment.centerLeft,
                    child: OutlinedButton.icon(
                      onPressed: _count == 0 ? null : _clear,
                      icon: const Icon(Icons.delete_outline),
                      label: const Text('Remove files from this phone'),
                    ),
                  ),
                  const SizedBox(height: 24),
                  const SectionTitle('On the server'),
                  Panel(
                    child: Text(
                      'Everything you send or receive in the chat is also stored on the Daraja Agent-X server, so it is there when you sign in on another phone. '
                      'It is kept for the time your provider has set (30 days unless changed) and then removed. Only you can open your files. '
                      'Removing files from this phone does not remove them from the server.',
                      style: text.bodyMedium,
                    ),
                  ),
                  const SizedBox(height: 12),
                  Text('Files are stored in the app\'s private space on the phone and are protected by the phone lock, not separately encrypted. They are removed when you sign out.', style: text.bodySmall),
                ],
              ]),
      ),
    );
  }
}
