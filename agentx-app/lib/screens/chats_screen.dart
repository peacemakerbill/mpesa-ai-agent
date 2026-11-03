import 'package:flutter/material.dart';

import '../chat/chat_controller.dart';
import '../layout/responsive.dart';
import '../models/chat_session.dart';
import '../theme/app_theme.dart';
import '../widgets/ui.dart';

const double _columnWidth = 760;

/// The customer's chats: open an old one, start a new one, rename, pin and delete. Swipe a chat to the left to delete it.
class ChatsScreen extends StatefulWidget {
  const ChatsScreen({super.key, required this.chat});

  final ChatController chat;

  @override
  State<ChatsScreen> createState() => _ChatsScreenState();
}

class _ChatsScreenState extends State<ChatsScreen> {
  final _search = TextEditingController();

  ChatController get _chat => widget.chat;

  @override
  void initState() {
    super.initState();
    _chat.refreshSessions(force: true);
    _search.addListener(() => setState(() {}));
  }

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  void _toast(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  /// Opens the chat and goes back to the conversation.
  Future<void> _open(ChatSession s) async {
    if (!s.active) {
      final err = await _chat.openChat(s.id);
      if (err != null) {
        _toast(err);
        return;
      }
    }
    if (mounted) Navigator.of(context).pop();
  }

  Future<void> _new() async {
    final err = await _chat.newChat();
    if (err != null) {
      _toast(err);
      return;
    }
    if (mounted) Navigator.of(context).pop();
  }

  Future<void> _rename(ChatSession s) async {
    final name = await showDialog<String>(context: context, builder: (_) => _RenameDialog(current: s.title));
    if (name == null || !mounted) return;
    final err = await _chat.renameChat(s.id, name);
    if (err != null) _toast(err);
  }

  Future<void> _pin(ChatSession s) async {
    final err = await _chat.pinChat(s.id, !s.pinned);
    if (err != null) _toast(err);
  }

  Future<void> _delete(ChatSession s) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (c) => AppDialog(
        title: const Text('Delete this chat?'),
        content: Text(s.active
            ? '"${s.title}" and its messages are deleted for good. A new empty chat takes its place.'
            : '"${s.title}" and its messages are deleted for good.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('Keep')),
          TextButton(onPressed: () => Navigator.pop(c, true), child: const Text('Delete')),
        ],
      ),
    );
    if (ok != true || !mounted) return;
    final err = await _chat.deleteChat(s.id);
    if (err != null) _toast(err);
  }

  Future<void> _deleteAll() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (c) => AppDialog(
        title: const Text('Delete all chats?'),
        content: const Text('Every chat is deleted for good, pinned ones too. Your requests stay in the Requests tab.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('Keep')),
          TextButton(onPressed: () => Navigator.pop(c, true), child: const Text('Delete all')),
        ],
      ),
    );
    if (ok != true || !mounted) return;
    final err = await _chat.deleteAllChats();
    if (err != null) {
      _toast(err);
      return;
    }
    if (mounted) Navigator.of(context).pop();
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: _chat,
      builder: (context, _) => Scaffold(
        appBar: AppBar(
          title: const Text('Chats'),
          actions: [
            IconButton(onPressed: _chat.switching ? null : _new, icon: const Icon(Icons.add_comment_outlined), tooltip: 'New chat'),
            PopupMenuButton<String>(
              tooltip: 'More',
              enabled: !_chat.switching,
              onSelected: (_) => _deleteAll(),
              itemBuilder: (_) => const [PopupMenuItem(value: 'all', child: Text('Delete all chats'))],
            ),
          ],
          bottom: _chat.switching ? const PreferredSize(preferredSize: Size.fromHeight(2), child: LinearProgressIndicator(minHeight: 2)) : null,
        ),
        body: _body(context),
      ),
    );
  }

  Widget _body(BuildContext context) {
    if (!_chat.sessionsSupported) {
      return const EmptyState(
        title: 'One chat only',
        message: 'This server keeps a single chat. Update the Daraja Agent-X API to start new chats and keep the old ones.',
      );
    }
    if (!_chat.sessionsLoaded) return const Center(child: CircularProgressIndicator());
    final shown = [
      for (final s in _chat.sessions)
        if (s.matches(_search.text)) s,
    ];
    return LayoutBuilder(
      builder: (context, box) {
        final side = sidePad(box.maxWidth, maxWidth: _columnWidth);
        return Column(children: [
          if (_chat.sessions.length > 6)
            Padding(
              padding: EdgeInsets.fromLTRB(side, 12, side, 4),
              child: TextField(
                controller: _search,
                textInputAction: TextInputAction.search,
                decoration: const InputDecoration(prefixIcon: Icon(Icons.search), hintText: 'Search chats'),
              ),
            ),
          Expanded(
            child: shown.isEmpty
                ? const Center(child: Text('No chat matches that.'))
                : ListView.builder(
              padding: EdgeInsets.fromLTRB(side, 8, side, 24),
              itemCount: shown.length,
              itemBuilder: (context, i) => _tile(context, shown[i]),
            ),
          ),
        ]);
      },
    );
  }

  Widget _tile(BuildContext context, ChatSession s) {
    final t = context.tk;
    final text = Theme.of(context).textTheme;
    final when = chatWhen(s.updatedAt, DateTime.now());
    return Dismissible(
      key: ValueKey(s.id),
      direction: DismissDirection.endToStart,
      confirmDismiss: (_) async {
        await _delete(s);
        return false;   // the list redraws from the server's answer, the row is never removed by the swipe itself
      },
      background: Container(
        alignment: Alignment.centerRight,
        padding: const EdgeInsets.only(right: 20),
        margin: const EdgeInsets.symmetric(vertical: 3),
        decoration: BoxDecoration(color: t.p.bad, borderRadius: t.corners),
        child: const Icon(Icons.delete_outline, color: Colors.white),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 3),
        child: Material(
          color: s.active ? t.p.accentSoft : t.p.surface,
          shape: t.shape(),
          clipBehavior: Clip.antiAlias,
          child: ListTile(
            onTap: () => _open(s),
            leading: Icon(s.pinned ? Icons.push_pin : Icons.chat_bubble_outline, color: s.pinned ? t.p.accent : t.p.ink2),
            title: Row(children: [
              Expanded(child: Text(s.title, maxLines: 1, overflow: TextOverflow.ellipsis, style: text.titleSmall)),
              const SizedBox(width: 8),
              Text(when, style: text.labelSmall?.copyWith(color: t.p.ink3)),
            ]),
            subtitle: Text(s.preview.isEmpty ? 'No messages yet' : s.preview, maxLines: 1, overflow: TextOverflow.ellipsis),
            trailing: PopupMenuButton<String>(
              tooltip: 'Options for ${s.title}',
              enabled: !_chat.switching,
              onSelected: (v) {
                if (v == 'rename') {
                  _rename(s);
                } else if (v == 'pin') {
                  _pin(s);
                } else {
                  _delete(s);
                }
              },
              itemBuilder: (_) => [
                const PopupMenuItem(value: 'rename', child: Text('Rename')),
                PopupMenuItem(value: 'pin', child: Text(s.pinned ? 'Unpin' : 'Pin to the top')),
                const PopupMenuItem(value: 'delete', child: Text('Delete')),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _RenameDialog extends StatefulWidget {
  const _RenameDialog({required this.current});

  final String current;

  @override
  State<_RenameDialog> createState() => _RenameDialogState();
}

class _RenameDialogState extends State<_RenameDialog> {
  late final TextEditingController _name = TextEditingController(text: widget.current);

  @override
  void dispose() {
    _name.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AppDialog(
      title: const Text('Rename chat'),
      content: TextField(
        controller: _name,
        autofocus: true,
        maxLength: 80,
        textInputAction: TextInputAction.done,
        onSubmitted: (v) => Navigator.pop(context, v),
        decoration: const InputDecoration(hintText: 'Chat name', helperText: 'Leave it empty to use the first message'),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
        TextButton(onPressed: () => Navigator.pop(context, _name.text), child: const Text('Save')),
      ],
    );
  }
}