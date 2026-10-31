import 'dart:async';

import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../app_state.dart';
import '../chat/chat_controller.dart';
import '../chat/chat_device.dart';
import '../contacts/contact_models.dart';
import '../contacts/contact_services.dart';
import '../models/chat.dart';
import '../theme/app_theme.dart';
import '../widgets/chat_bubbles.dart';
import '../widgets/ui.dart';
import 'action_screen.dart';
import 'contact_finder_screen.dart';

/// Something the customer attached and has not sent yet.
class _Pending {
  const _Pending.file(ChatAttachment f)
      : file = f,
        contact = null,
        point = null;
  const _Pending.contact(PickedContact c)
      : file = null,
        contact = c,
        point = null;
  const _Pending.point(GeoPoint p)
      : file = null,
        contact = null,
        point = p;

  final ChatAttachment? file;
  final PickedContact? contact;
  final GeoPoint? point;

  String get label {
    if (file != null) return file!.kind == 'AUDIO' ? 'Voice note' : file!.fileName;
    if (contact != null) return contact!.name;
    return 'Location';
  }

  IconData get icon {
    final f = file;
    if (f != null) {
      switch (f.kind) {
        case 'IMAGE':
          return Icons.image_outlined;
        case 'VIDEO':
          return Icons.videocam_outlined;
        case 'AUDIO':
          return Icons.mic_none;
        default:
          return Icons.description_outlined;
      }
    }
    return contact != null ? Icons.person_outline : Icons.place_outlined;
  }

  /// A voice note, a contact or a location is sent without the typed words as a caption.
  bool get takesCaption => file != null && file!.kind != 'AUDIO';
}

class ChatScreen extends StatefulWidget {
  const ChatScreen({super.key, required this.state, required this.chat, required this.device, required this.contacts});

  final AppState state;
  final ChatController chat;
  final ChatDevice device;
  final ContactServices contacts;

  @override
  State<ChatScreen> createState() => _ChatScreenState();
}

class _ChatScreenState extends State<ChatScreen> {
  final _text = TextEditingController();
  final _focus = FocusNode();
  final List<_Pending> _tray = [];
  final Map<String, Future<ChatFile>> _files = {};
  bool _recording = false;
  DateTime? _recordStart;
  Timer? _tick;

  ChatController get _chat => widget.chat;

  @override
  void initState() {
    super.initState();
    _text.addListener(() => setState(() {}));
  }

  @override
  void dispose() {
    _tick?.cancel();
    if (_recording) widget.device.cancelVoice();
    _text.dispose();
    _focus.dispose();
    super.dispose();
  }

  void _toast(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  Future<ChatFile> _loadFile(String id) => _files.putIfAbsent(id, () => widget.state.api.chatFile(id));

  Future<void> _openLink(String url) async {
    final id = requestIdIn(url);
    if (id != null) {
      _openRequest(id);
      return;
    }
    if (!await widget.state.launcher.openUrl(url)) _toast('That link could not be opened.');
  }

  void _openRequest(String id) {
    Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => ActionScreen(state: widget.state, actionId: id)));
  }

  Future<void> _findContact(String query) async {
    final picked = await Navigator.of(context).push<PickedContact>(MaterialPageRoute(
      builder: (_) => ContactFinderScreen(services: widget.contacts, title: 'Find in contacts', query: query),
    ));
    if (picked != null) await _chat.sendContact(picked.name, picked.phone);
  }

  late final BubbleActions _actions = BubbleActions(
    onButton: (b) => _chat.sendButton(b),
    onRow: (r) => _chat.sendRow(r),
    onFindContact: _findContact,
    onOpenRequest: _openRequest,
    onLink: _openLink,
    onError: _toast,
    loadFile: _loadFile,
    device: widget.device,
  );

  // ------------------------------------------------------------ sending

  Future<void> _send() async {
    final text = _text.text.trim();
    if (text.isEmpty && _tray.isEmpty) return;
    final items = List<_Pending>.of(_tray);
    setState(() => _tray.clear());
    _text.clear();
    if (items.isEmpty) {
      await _chat.sendText(text);
      return;
    }
    var captionUsed = false;
    for (final item in items) {
      if (item.file != null) {
        final caption = !captionUsed && item.takesCaption ? text : null;
        if (caption != null && caption.isNotEmpty) captionUsed = true;
        await _chat.sendAttachment(item.file!, caption: caption);
      } else if (item.contact != null) {
        await _chat.sendContact(item.contact!.name, item.contact!.phone);
      } else if (item.point != null) {
        await _chat.sendLocation(item.point!.latitude, item.point!.longitude);
      }
    }
    if (text.isNotEmpty && !captionUsed) await _chat.sendText(text);
  }

  Future<void> _attach(Future<ChatAttachment?> Function() pick) async {
    try {
      final a = await pick();
      if (a != null && mounted) setState(() => _tray.add(_Pending.file(a)));
    } on ChatDeviceException catch (e) {
      _toast(e.message);
    } on Exception catch (e) {
      _toast('That could not be attached. $e');
    }
  }

  Future<void> _attachContact() async {
    final picked = await Navigator.of(context).push<PickedContact>(MaterialPageRoute(
      builder: (_) => ContactFinderScreen(services: widget.contacts, title: 'Share a contact'),
    ));
    if (picked != null && mounted) setState(() => _tray.add(_Pending.contact(picked)));
  }

  Future<void> _attachLocation() async {
    try {
      final p = await widget.device.location();
      if (p != null && mounted) setState(() => _tray.add(_Pending.point(p)));
    } on ChatDeviceException catch (e) {
      _toast(e.message);
    } on Exception catch (e) {
      _toast('Your location could not be found. $e');
    }
  }

  void _showAttachMenu() {
    final d = widget.device;
    final entries = <(IconData, String, VoidCallback)>[
      (Icons.photo_outlined, 'Photo', () => _attach(d.photoFromGallery)),
      (Icons.photo_camera_outlined, 'Camera', () => _attach(d.photoFromCamera)),
      (Icons.video_library_outlined, 'Video', () => _attach(d.videoFromGallery)),
      (Icons.videocam_outlined, 'Record video', () => _attach(d.videoFromCamera)),
      (Icons.description_outlined, 'Document', () => _attach(d.document)),
      (Icons.person_outline, 'Contact', _attachContact),
      (Icons.place_outlined, 'Location', _attachLocation),
    ];
    showModalBottomSheet<void>(
      context: context,
      builder: (sheet) {
        final t = sheet.tk;
        return SafeArea(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
            child: Wrap(spacing: 8, runSpacing: 12, children: [
              for (final e in entries)
                SizedBox(
                  width: (MediaQuery.sizeOf(sheet).width - 32 - 16) / 3,
                  child: InkWell(
                    borderRadius: t.corners,
                    onTap: () {
                      Navigator.pop(sheet);
                      e.$3();
                    },
                    child: Padding(
                      padding: const EdgeInsets.symmetric(vertical: 10),
                      child: Column(children: [
                        Container(
                          width: 52,
                          height: 52,
                          decoration: BoxDecoration(
                            color: t.p.accentSoft,
                            border: Border.all(color: t.p.accentLine),
                            shape: t.square ? BoxShape.rectangle : BoxShape.circle,
                          ),
                          child: Icon(e.$1, color: t.p.ink),
                        ),
                        const SizedBox(height: 6),
                        Text(e.$2, style: Theme.of(sheet).textTheme.bodySmall, textAlign: TextAlign.center),
                      ]),
                    ),
                  ),
                ),
            ]),
          ),
        );
      },
    );
  }

  // ------------------------------------------------------------ voice

  Future<void> _startVoice() async {
    try {
      await widget.device.startVoice();
    } on ChatDeviceException catch (e) {
      _toast(e.message);
      return;
    } on Exception catch (e) {
      _toast('The microphone could not start. $e');
      return;
    }
    _recordStart = DateTime.now();
    _tick = Timer.periodic(const Duration(seconds: 1), (_) => setState(() {}));
    setState(() => _recording = true);
  }

  void _stopTicking() {
    _tick?.cancel();
    _tick = null;
    _recordStart = null;
  }

  Future<void> _cancelVoice() async {
    _stopTicking();
    setState(() => _recording = false);
    await widget.device.cancelVoice();
  }

  Future<void> _sendVoice() async {
    _stopTicking();
    setState(() => _recording = false);
    ChatAttachment? note;
    try {
      note = await widget.device.stopVoice();
    } on Exception catch (e) {
      _toast('The recording could not be saved. $e');
      return;
    }
    if (note == null) {
      _toast('That was too short. Tap the microphone and speak for a moment.');
      return;
    }
    await _chat.sendAttachment(note);
  }

  String _elapsed() {
    final s = _recordStart == null ? 0 : DateTime.now().difference(_recordStart!).inSeconds;
    return '${s ~/ 60}:${(s % 60).toString().padLeft(2, '0')}';
  }

  // ------------------------------------------------------------ menu

  Future<void> _clear() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (c) => AppDialog(
        title: const Text('Clear this chat?'),
        content: const Text('The conversation and the files in it are deleted. Your requests stay in the Requests tab.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('Keep')),
          TextButton(onPressed: () => Navigator.pop(c, true), child: const Text('Clear')),
        ],
      ),
    );
    if (ok == true) await _chat.clear();
  }

  Future<void> _signOut() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (c) => AppDialog(
        title: const Text('Sign out of this phone?'),
        content: const Text('You will need a new code by SMS to sign in again.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('Stay signed in')),
          TextButton(onPressed: () => Navigator.pop(c, true), child: const Text('Sign out')),
        ],
      ),
    );
    if (ok == true) await widget.state.signOut();
  }

  // ------------------------------------------------------------ build

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: _chat,
      builder: (context, _) => Scaffold(
        appBar: AppBar(
          title: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const Text('M-Ganji'),
            if (_chat.typing) Text('typing', style: Theme.of(context).textTheme.labelSmall?.copyWith(color: context.tk.p.ink2)),
          ]),
          actions: [
            IconButton(onPressed: () => showThemeSheet(context, widget.state), icon: const Icon(Icons.palette_outlined), tooltip: 'Appearance'),
            PopupMenuButton<String>(
              onSelected: (v) => v == 'clear' ? _clear() : _signOut(),
              itemBuilder: (_) => const [
                PopupMenuItem(value: 'clear', child: Text('Clear chat')),
                PopupMenuItem(value: 'out', child: Text('Sign out')),
              ],
            ),
          ],
        ),
        body: SafeArea(
          child: Column(children: [
            Expanded(child: _messages(context)),
            if (_chat.error != null) Padding(padding: const EdgeInsets.fromLTRB(12, 4, 12, 0), child: Callout(_chat.error!, tone: Tone.bad)),
            if (_tray.isNotEmpty) _trayView(context),
            _composer(context),
          ]),
        ),
      ),
    );
  }

  Widget _messages(BuildContext context) {
    if (!_chat.loaded) return const Center(child: CircularProgressIndicator());
    final list = _chat.messages;
    if (list.isEmpty) return _welcome(context);
    var lastMine = 0;
    for (final m in list) {
      if (m.mine && m.id > lastMine) lastMine = m.id;
    }
    final extra = _chat.typing ? 1 : 0;
    return ListView.builder(
      reverse: true,
      padding: const EdgeInsets.fromLTRB(12, 8, 12, 8),
      itemCount: list.length + extra,
      itemBuilder: (context, i) {
        if (extra == 1 && i == 0) return const TypingBubble();
        final m = list[list.length - 1 - (i - extra)];
        return MessageBubble(key: ValueKey(m.id), message: m, live: m.id > lastMine, actions: _actions);
      },
    );
  }

  Widget _welcome(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final t = context.tk;
    const ideas = ['Send 500 to Mama', 'Pay till 123456', 'What can you do?'];
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(28),
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          const BrandMark(size: 44),
          const SizedBox(height: 16),
          Text('Ask in your own words', style: text.titleLarge),
          const SizedBox(height: 8),
          Text(
            'Type, send a photo of a till or paybill, or record a voice note. Nothing moves until you approve it.',
            textAlign: TextAlign.center,
            style: text.bodyMedium?.copyWith(color: t.p.ink2),
          ),
          const SizedBox(height: 16),
          Wrap(spacing: 8, runSpacing: 8, alignment: WrapAlignment.center, children: [
            for (final idea in ideas)
              ActionChip(
                label: Text(idea),
                backgroundColor: t.p.surface,
                side: BorderSide(color: t.p.line),
                shape: t.shape(outline: false, scale: 0.7),
                onPressed: () {
                  _text.text = idea;
                  _text.selection = TextSelection.collapsed(offset: idea.length);
                  _focus.requestFocus();
                },
              ),
          ]),
        ]),
      ),
    );
  }

  Widget _trayView(BuildContext context) {
    final t = context.tk;
    return SizedBox(
      height: 48,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.fromLTRB(12, 6, 12, 4),
        itemCount: _tray.length,
        separatorBuilder: (_, __) => const SizedBox(width: 8),
        itemBuilder: (_, i) => InputChip(
          avatar: Icon(_tray[i].icon, size: 18, color: t.p.ink),
          label: ConstrainedBox(constraints: const BoxConstraints(maxWidth: 160), child: Text(_tray[i].label, overflow: TextOverflow.ellipsis)),
          onDeleted: () => setState(() => _tray.removeAt(i)),
          backgroundColor: t.p.accentSoft,
          side: BorderSide(color: t.p.accentLine),
          shape: t.shape(outline: false, scale: 0.7),
        ),
      ),
    );
  }

  Widget _composer(BuildContext context) {
    final t = context.tk;
    if (_recording) {
      return Container(
        padding: const EdgeInsets.fromLTRB(8, 6, 8, 8),
        decoration: BoxDecoration(color: t.p.surface, border: Border(top: BorderSide(color: t.p.line))),
        child: Row(children: [
          IconButton(onPressed: _cancelVoice, icon: const Icon(Icons.delete_outline), tooltip: 'Discard recording'),
          Icon(Icons.fiber_manual_record, size: 14, color: t.p.bad),
          const SizedBox(width: 8),
          Text('Recording  ${_elapsed()}', style: t.numeric(16, weight: FontWeight.w600)),
          const Spacer(),
          IconButton.filled(onPressed: _sendVoice, icon: const Icon(Icons.send), tooltip: 'Send voice note'),
        ]),
      );
    }
    final hasWords = _text.text.trim().isNotEmpty || _tray.isNotEmpty;
    return Container(
      padding: const EdgeInsets.fromLTRB(4, 6, 8, 8),
      decoration: BoxDecoration(color: t.p.surface, border: Border(top: BorderSide(color: t.p.line))),
      child: Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
        IconButton(onPressed: _showAttachMenu, icon: const Icon(Icons.add_circle_outline), tooltip: 'Attach'),
        Expanded(
          child: TextField(
            controller: _text,
            focusNode: _focus,
            minLines: 1,
            maxLines: 5,
            keyboardType: TextInputType.multiline,
            textCapitalization: TextCapitalization.sentences,
            decoration: InputDecoration(hintText: _tray.isEmpty ? 'Message' : 'Add a caption', isDense: true),
          ),
        ),
        const SizedBox(width: 6),
        if (hasWords)
          IconButton.filled(onPressed: _chat.sending ? null : _send, icon: const Icon(Icons.send), tooltip: 'Send')
        else
          IconButton.filled(onPressed: _startVoice, icon: const Icon(Icons.mic), tooltip: 'Record a voice note'),
      ]),
    );
  }
}
