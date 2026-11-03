import 'dart:math' as math;

import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../chat/chat_device.dart';
import '../models/chat.dart';
import '../theme/app_theme.dart';

/// What a message can ask the screen to do.
class BubbleActions {
  const BubbleActions({
    required this.onButton,
    required this.onRow,
    required this.onFindContact,
    required this.onOpenRequest,
    required this.onLink,
    required this.onError,
    required this.loadFile,
    required this.device,
  });

  final void Function(ChatButton) onButton;
  final void Function(ChatRow) onRow;
  final void Function(String query) onFindContact;
  final void Function(String requestId) onOpenRequest;
  final void Function(String url) onLink;
  final void Function(String message) onError;
  final Future<ChatFile> Function(String mediaId) loadFile;
  final ChatDevice device;
}

String clock(DateTime t) => '${t.hour.toString().padLeft(2, '0')}:${t.minute.toString().padLeft(2, '0')}';

/// One message, on the right when the customer wrote it and on the left when the assistant did.
/// [live] is true when buttons and menus under it can still be used, that is, nothing was said after it.
class MessageBubble extends StatelessWidget {
  const MessageBubble({super.key, required this.message, required this.live, required this.actions, this.startsGroup = true});

  final ChatMessage message;
  final bool live;
  final BubbleActions actions;

  /// False when the message before this one came from the same side, so the two sit closer together.
  final bool startsGroup;

  @override
  Widget build(BuildContext context) {
    final t = context.tk;
    final mine = message.mine;
    final r = t.radius.clamp(0, 18).toDouble();
    final small = t.square ? 0.0 : 4.0;
    final shape = BorderRadius.only(
      topLeft: Radius.circular(r),
      topRight: Radius.circular(r),
      bottomLeft: Radius.circular(mine ? r : small),
      bottomRight: Radius.circular(mine ? small : r),
    );
    // A bubble is at most 84 percent of the pane it sits in, and never wider than 560 points, so lines stay easy to read on a big screen.
    return LayoutBuilder(
      builder: (context, box) => Align(
        alignment: mine ? Alignment.centerRight : Alignment.centerLeft,
        child: Container(
          margin: EdgeInsets.fromLTRB(mine ? 40 : 0, startsGroup ? 8 : 2, mine ? 0 : 40, 0),
          constraints: BoxConstraints(maxWidth: math.min(box.maxWidth * 0.84, 560.0)),
          padding: const EdgeInsets.fromLTRB(12, 9, 12, 6),
          decoration: BoxDecoration(
            color: mine ? t.p.accentSoft : t.p.surface,
            border: Border.all(color: mine ? t.p.accentLine : t.p.line),
            borderRadius: shape,
          ),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, mainAxisSize: MainAxisSize.min, children: [
            _content(context),
            const SizedBox(height: 3),
            Align(alignment: Alignment.centerRight, child: Text(clock(message.at), style: Theme.of(context).textTheme.labelSmall?.copyWith(color: t.p.ink3))),
          ]),
        ),
      ),
    );
  }

  Widget _content(BuildContext context) {
    final m = message;
    final text = Theme.of(context).textTheme;
    switch (m.kind) {
      case 'BUTTONS':
        return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          _words(context, m.text ?? ''),
          if (m.footer != null) Padding(padding: const EdgeInsets.only(top: 4), child: Text(m.footer!, style: text.bodySmall)),
          _openRequest(context),
          const SizedBox(height: 8),
          for (final b in m.buttons)
            Padding(
              padding: const EdgeInsets.only(bottom: 6),
              child: SizedBox(width: double.infinity, child: OutlinedButton(onPressed: live ? () => actions.onButton(b) : null, child: Text(b.title))),
            ),
        ]);
      case 'LIST':
        return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          _words(context, m.text ?? ''),
          if (m.footer != null) Padding(padding: const EdgeInsets.only(top: 4), child: Text(m.footer!, style: text.bodySmall)),
          _openRequest(context),
          const SizedBox(height: 8),
          SizedBox(
            width: double.infinity,
            child: OutlinedButton.icon(
              onPressed: live ? () => _showRows(context) : null,
              icon: const Icon(Icons.list),
              label: Text(m.listTitle ?? 'Choose'),
            ),
          ),
        ]);
      case 'IMAGE':
        return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          if (m.mediaId != null) _ChatImage(mediaId: m.mediaId!, actions: actions),
          if (m.caption != null) Padding(padding: const EdgeInsets.only(top: 6), child: _words(context, m.caption!)),
        ]);
      case 'AUDIO':
        return _AudioBubble(message: m, actions: actions);
      case 'VIDEO':
      case 'DOCUMENT':
        return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          _FileCard(message: m, actions: actions),
          if (m.caption != null) Padding(padding: const EdgeInsets.only(top: 6), child: _words(context, m.caption!)),
        ]);
      case 'LOCATION':
        return _LocationCard(message: m, actions: actions);
      case 'CONTACTS':
        return _ContactCard(summary: m.contactsSummary ?? m.text ?? '');
      case 'CONTACT_LOOKUP':
        return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          _words(context, m.text ?? 'I need a phone number for ${m.query ?? 'this person'}.'),
          const SizedBox(height: 8),
          SizedBox(
            width: double.infinity,
            child: FilledButton.icon(
              onPressed: live ? () => actions.onFindContact(m.query ?? '') : null,
              icon: const Icon(Icons.person_search_outlined),
              label: const Text('Find in contacts'),
            ),
          ),
        ]);
      case 'BUTTON_REPLY':
      case 'LIST_REPLY':
        return _words(context, m.actionTitle ?? m.text ?? '');
      default:
        return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          _words(context, m.text ?? ''),
          _openRequest(context),
        ]);
    }
  }

  Widget _words(BuildContext context, String text) => RichMessageText(text: text, onLink: actions.onLink);

  Widget _openRequest(BuildContext context) {
    final id = message.requestId;
    if (id == null) return const SizedBox.shrink();
    return Padding(
      padding: const EdgeInsets.only(top: 8),
      child: SizedBox(
        width: double.infinity,
        child: FilledButton.icon(onPressed: () => actions.onOpenRequest(id), icon: const Icon(Icons.fact_check_outlined), label: const Text('Open request')),
      ),
    );
  }

  void _showRows(BuildContext context) {
    final text = Theme.of(context).textTheme;
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      constraints: const BoxConstraints(maxWidth: 560),
      builder: (sheet) => SafeArea(
        child: ConstrainedBox(
          constraints: BoxConstraints(maxHeight: MediaQuery.sizeOf(sheet).height * 0.7),
          child: ListView(shrinkWrap: true, padding: const EdgeInsets.fromLTRB(8, 12, 8, 12), children: [
            Padding(padding: const EdgeInsets.fromLTRB(16, 0, 16, 8), child: Text(message.listTitle ?? 'Choose', style: text.titleMedium)),
            for (final row in message.rows)
              ListTile(
                title: Text(row.title),
                subtitle: row.description == null ? null : Text(row.description!),
                onTap: () {
                  Navigator.pop(sheet);
                  actions.onRow(row);
                },
              ),
          ]),
        ),
      ),
    );
  }
}

/// Text with the assistant's light markup: *bold*, _italic_, `code` and links that open when tapped.
class RichMessageText extends StatefulWidget {
  const RichMessageText({super.key, required this.text, required this.onLink});

  final String text;
  final void Function(String url) onLink;

  @override
  State<RichMessageText> createState() => _RichMessageTextState();
}

class _RichMessageTextState extends State<RichMessageText> {
  final List<TapGestureRecognizer> _taps = [];

  void _clear() {
    for (final t in _taps) {
      t.dispose();
    }
    _taps.clear();
  }

  @override
  void dispose() {
    _clear();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    _clear();
    final t = context.tk;
    final base = Theme.of(context).textTheme.bodyMedium ?? const TextStyle();
    final spans = <InlineSpan>[];
    for (final s in parseRich(widget.text)) {
      TapGestureRecognizer? tap;
      if (s.link != null) {
        final url = s.link!;
        tap = TapGestureRecognizer()..onTap = () => widget.onLink(url);
        _taps.add(tap);
      }
      spans.add(TextSpan(
        text: s.text,
        recognizer: tap,
        style: TextStyle(
          fontWeight: s.bold ? FontWeight.w700 : null,
          fontStyle: s.italic ? FontStyle.italic : null,
          fontFamily: s.code ? 'monospace' : null,
          backgroundColor: s.code ? t.p.surface2 : null,
          color: s.link != null ? t.p.accent : null,
          decoration: s.link != null ? TextDecoration.underline : null,
        ),
      ));
    }
    return Text.rich(TextSpan(style: base, children: spans));
  }
}

class _ChatImage extends StatefulWidget {
  const _ChatImage({required this.mediaId, required this.actions});

  final String mediaId;
  final BubbleActions actions;

  @override
  State<_ChatImage> createState() => _ChatImageState();
}

class _ChatImageState extends State<_ChatImage> {
  late final Future<ChatFile> _file = widget.actions.loadFile(widget.mediaId);

  @override
  Widget build(BuildContext context) {
    final t = context.tk;
    return LayoutBuilder(
      builder: (context, box) {
        // As wide as the bubble allows, up to 320 points.
        final width = math.min(box.maxWidth.isFinite ? box.maxWidth : 240.0, 320.0);
        return ClipRRect(
          borderRadius: BorderRadius.circular((t.radius * 0.6).clamp(0, 12).toDouble()),
          child: FutureBuilder<ChatFile>(
            future: _file,
            builder: (context, snap) {
              if (snap.hasData) {
                final bytes = snap.data!.bytes;
                return GestureDetector(
                  onTap: () => Navigator.of(context).push(MaterialPageRoute<void>(
                    builder: (_) => Scaffold(
                      backgroundColor: Colors.black,
                      appBar: AppBar(backgroundColor: Colors.black, foregroundColor: Colors.white),
                      body: Center(child: InteractiveViewer(child: Image.memory(bytes))),
                    ),
                  )),
                  child: Image.memory(bytes, width: width, fit: BoxFit.cover, errorBuilder: (_, __, ___) => _box(context, width, Icons.broken_image_outlined, 'Photo could not be shown')),
                );
              }
              if (snap.hasError) return _box(context, width, Icons.broken_image_outlined, 'Photo could not be loaded');
              return _box(context, width, null, null);
            },
          ),
        );
      },
    );
  }

  Widget _box(BuildContext context, double width, IconData? icon, String? label) {
    final t = context.tk;
    return Container(
      width: width,
      height: 140,
      color: t.p.surface2,
      alignment: Alignment.center,
      child: icon == null
          ? const SizedBox(height: 22, width: 22, child: CircularProgressIndicator(strokeWidth: 2))
          : Column(mainAxisSize: MainAxisSize.min, children: [Icon(icon, color: t.p.ink3), const SizedBox(height: 6), Text(label!, style: Theme.of(context).textTheme.bodySmall)]),
    );
  }
}

class _AudioBubble extends StatefulWidget {
  const _AudioBubble({required this.message, required this.actions});

  final ChatMessage message;
  final BubbleActions actions;

  @override
  State<_AudioBubble> createState() => _AudioBubbleState();
}

class _AudioBubbleState extends State<_AudioBubble> {
  bool _playing = false;
  bool _loading = false;

  Future<void> _toggle() async {
    final a = widget.actions;
    if (_playing) {
      await a.device.stopPlayback();
      if (mounted) setState(() => _playing = false);
      return;
    }
    final id = widget.message.mediaId;
    if (id == null) return;
    setState(() => _loading = true);
    try {
      final file = await a.loadFile(id);
      if (!mounted) return;
      setState(() {
        _loading = false;
        _playing = true;
      });
      await a.device.play(file.bytes, file.mimeType);
    } on Exception catch (e) {
      a.onError('The sound could not be played. $e');
    } finally {
      if (mounted) {
        setState(() {
          _loading = false;
          _playing = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = context.tk;
    final text = Theme.of(context).textTheme;
    final m = widget.message;
    return InkWell(
      onTap: _loading ? null : _toggle,
      child: Row(mainAxisSize: MainAxisSize.min, children: [
        _loading
            ? const SizedBox(height: 28, width: 28, child: Padding(padding: EdgeInsets.all(4), child: CircularProgressIndicator(strokeWidth: 2)))
            : Icon(_playing ? Icons.stop_circle_outlined : Icons.play_circle_outline, size: 32, color: t.p.accent),
        const SizedBox(width: 10),
        Flexible(child: Text(m.voiceNote ? 'Voice note' : (m.fileName ?? 'Audio'), style: text.bodyMedium)),
      ]),
    );
  }
}

class _FileCard extends StatefulWidget {
  const _FileCard({required this.message, required this.actions});

  final ChatMessage message;
  final BubbleActions actions;

  @override
  State<_FileCard> createState() => _FileCardState();
}

class _FileCardState extends State<_FileCard> {
  bool _busy = false;

  Future<void> _open() async {
    final m = widget.message;
    final a = widget.actions;
    if (m.mediaId == null) return;
    setState(() => _busy = true);
    try {
      final file = await a.loadFile(m.mediaId!);
      final name = m.fileName ?? '${m.kind == 'VIDEO' ? 'video' : 'file'}.${extensionFor(file.mimeType)}';
      await a.device.open(file.bytes, name);
    } on ChatDeviceException catch (e) {
      a.onError(e.message);
    } on Exception catch (e) {
      a.onError('The file could not be opened. $e');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = context.tk;
    final m = widget.message;
    final text = Theme.of(context).textTheme;
    return InkWell(
      onTap: _busy ? null : _open,
      child: Container(
        padding: const EdgeInsets.all(10),
        decoration: BoxDecoration(color: t.p.surface2, borderRadius: BorderRadius.circular((t.radius * 0.5).clamp(0, 10).toDouble())),
        child: Row(mainAxisSize: MainAxisSize.min, children: [
          _busy
              ? const SizedBox(height: 24, width: 24, child: CircularProgressIndicator(strokeWidth: 2))
              : Icon(m.kind == 'VIDEO' ? Icons.videocam_outlined : Icons.description_outlined, color: t.p.accent),
          const SizedBox(width: 10),
          Flexible(child: Text(m.fileName ?? (m.kind == 'VIDEO' ? 'Video' : 'Document'), style: text.bodyMedium, overflow: TextOverflow.ellipsis)),
        ]),
      ),
    );
  }
}

class _LocationCard extends StatelessWidget {
  const _LocationCard({required this.message, required this.actions});

  final ChatMessage message;
  final BubbleActions actions;

  @override
  Widget build(BuildContext context) {
    final t = context.tk;
    final text = Theme.of(context).textTheme;
    final lat = message.latitude;
    final lng = message.longitude;
    return InkWell(
      onTap: lat == null || lng == null ? null : () => actions.onLink('https://www.google.com/maps/search/?api=1&query=$lat,$lng'),
      child: Row(mainAxisSize: MainAxisSize.min, children: [
        Icon(Icons.place_outlined, color: t.p.accent),
        const SizedBox(width: 10),
        Flexible(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(message.locationLabel ?? 'Shared location', style: text.bodyMedium),
            if (lat != null && lng != null) Text('${lat.toStringAsFixed(5)}, ${lng.toStringAsFixed(5)}', style: text.bodySmall),
          ]),
        ),
      ]),
    );
  }
}

class _ContactCard extends StatelessWidget {
  const _ContactCard({required this.summary});

  final String summary;

  @override
  Widget build(BuildContext context) {
    final t = context.tk;
    final text = Theme.of(context).textTheme;
    final c = splitContactSummary(summary);
    return Row(mainAxisSize: MainAxisSize.min, children: [
      Icon(Icons.person_outline, color: t.p.accent),
      const SizedBox(width: 10),
      Flexible(
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          if (c.name.isNotEmpty) Text(c.name, style: text.bodyMedium),
          Text(c.phone, style: t.numeric(15, weight: FontWeight.w600)),
        ]),
      ),
    ]);
  }
}

/// The three dots shown while the assistant is working on an answer.
class TypingBubble extends StatefulWidget {
  const TypingBubble({super.key});

  @override
  State<TypingBubble> createState() => _TypingBubbleState();
}

class _TypingBubbleState extends State<TypingBubble> with SingleTickerProviderStateMixin {
  late final AnimationController _c = AnimationController(vsync: this, duration: const Duration(milliseconds: 1200))..repeat();

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  /// Each dot lights up in turn.
  int _alpha(int dot) {
    final phase = (_c.value * 3 - dot) % 3;
    final pulse = phase < 1 ? 1 - phase : 0.0;
    return (90 + 165 * pulse).round();
  }

  @override
  Widget build(BuildContext context) {
    final t = context.tk;
    return Align(
      alignment: Alignment.centerLeft,
      child: Semantics(
        label: 'The assistant is typing',
        child: Container(
          margin: const EdgeInsets.symmetric(vertical: 3),
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
          decoration: BoxDecoration(color: t.p.surface, border: Border.all(color: t.p.line), borderRadius: BorderRadius.circular(t.radius.clamp(0, 18).toDouble())),
          child: AnimatedBuilder(
            animation: _c,
            builder: (_, __) => Row(mainAxisSize: MainAxisSize.min, children: [
              for (var i = 0; i < 3; i++)
                Container(
                  width: 7,
                  height: 7,
                  margin: const EdgeInsets.symmetric(horizontal: 2),
                  decoration: BoxDecoration(
                    color: t.p.ink3.withAlpha(_alpha(i)),
                    shape: t.square ? BoxShape.rectangle : BoxShape.circle,
                  ),
                ),
            ]),
          ),
        ),
      ),
    );
  }
}
