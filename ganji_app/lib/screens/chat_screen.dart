import 'dart:async';

import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../app_state.dart';
import '../chat/chat_controller.dart';
import '../chat/chat_device.dart';
import '../contacts/contact_models.dart';
import '../contacts/contact_services.dart';
import '../layout/responsive.dart';
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

/// One line of the message list: a message, a day heading, or the typing dots.
class _Entry {
  const _Entry.message(ChatMessage this.message, {this.startsGroup = true}) : day = null;
  const _Entry.day(DateTime this.day)
      : message = null,
        startsGroup = false;
  const _Entry.typing()
      : message = null,
        day = null,
        startsGroup = false;

  final ChatMessage? message;
  final DateTime? day;
  final bool startsGroup;
}

const double _columnWidth = 760;

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
    // On a wide window the request opens in the right pane, next to the conversation.
    final inPane = RequestPane.openerOf(context);
    if (inPane != null) {
      inPane(id);
      return;
    }
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
      constraints: const BoxConstraints(maxWidth: 560),
      builder: (sheet) {
        final t = sheet.tk;
        return SafeArea(
          child: SingleChildScrollView(
            padding: const EdgeInsets.fromLTRB(16, 4, 16, 20),
            child: Wrap(alignment: WrapAlignment.center, spacing: 12, runSpacing: 12, children: [
              for (final e in entries)
                SizedBox(
                  width: 92,
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
        title: const Text('Sign out of this device?'),
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
        appBar: _appBar(context),
        body: SafeArea(
          top: false,
          child: Column(children: [
            Expanded(child: _messages(context)),
            if (_chat.error != null) _padded(Callout(_chat.error!, tone: Tone.bad), top: 4),
            if (_tray.isNotEmpty) _trayView(context),
            _composer(context),
          ]),
        ),
      ),
    );
  }

  /// Keeps what sits under the message list at the same width as the messages.
  Widget _padded(Widget child, {double top = 0, double bottom = 0}) {
    return LayoutBuilder(
      builder: (context, box) {
        final side = sidePad(box.maxWidth, maxWidth: _columnWidth);
        return Padding(padding: EdgeInsets.fromLTRB(side, top, side, bottom), child: child);
      },
    );
  }

  PreferredSizeWidget _appBar(BuildContext context) {
    final t = context.tk;
    final text = Theme.of(context).textTheme;
    final inkOnHead = t.p.headInk;
    return AppBar(
      titleSpacing: 16,
      title: Row(children: [
        Container(
          width: 38,
          height: 38,
          alignment: Alignment.center,
          decoration: BoxDecoration(
            color: inkOnHead.withAlpha(24),
            border: Border.all(color: inkOnHead.withAlpha(60)),
            borderRadius: t.square ? null : BorderRadius.circular(t.radius * 0.9),
          ),
          child: BrandMark(size: 24, ink: inkOnHead, accent: t.p.headDark ? inkOnHead : t.p.accent),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, mainAxisSize: MainAxisSize.min, children: [
            const Text('M-Ganji', maxLines: 1, overflow: TextOverflow.ellipsis),
            Text(
              _chat.typing ? 'typing' : 'Your M-Pesa assistant',
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: text.labelSmall?.copyWith(color: inkOnHead.withAlpha(190)),
            ),
          ]),
        ),
      ]),
      actions: [
        IconButton(onPressed: () => showThemeSheet(context, widget.state), icon: const Icon(Icons.palette_outlined), tooltip: 'Appearance'),
        PopupMenuButton<String>(
          tooltip: 'More',
          onSelected: (v) => v == 'clear' ? _clear() : _signOut(),
          itemBuilder: (_) => const [
            PopupMenuItem(value: 'clear', child: Text('Clear chat')),
            PopupMenuItem(value: 'out', child: Text('Sign out')),
          ],
        ),
      ],
    );
  }

  bool _sameDay(DateTime a, DateTime b) => a.year == b.year && a.month == b.month && a.day == b.day;

  String _dayLabel(DateTime d) {
    final now = DateTime.now();
    if (_sameDay(d, now)) return 'Today';
    if (_sameDay(d, now.subtract(const Duration(days: 1)))) return 'Yesterday';
    const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    return '${d.day} ${months[d.month - 1]}${d.year == now.year ? '' : ' ${d.year}'}';
  }

  /// Newest first, because the list is drawn from the bottom up.
  List<_Entry> _entries(List<ChatMessage> list) {
    final out = <_Entry>[];
    if (_chat.typing) out.add(const _Entry.typing());
    for (var i = list.length - 1; i >= 0; i--) {
      final m = list[i];
      final prev = i > 0 ? list[i - 1] : null;
      final newDay = prev == null || !_sameDay(prev.at, m.at);
      final startsGroup = prev == null || newDay || prev.mine != m.mine;
      out.add(_Entry.message(m, startsGroup: startsGroup));
      if (newDay) out.add(_Entry.day(m.at));
    }
    return out;
  }

  Widget _messages(BuildContext context) {
    if (!_chat.loaded) return const Center(child: CircularProgressIndicator());
    final list = _chat.messages;
    return Stack(fit: StackFit.expand, children: [
      const Positioned.fill(child: BrandPattern()),
      if (list.isEmpty) _welcome(context) else _list(context, list),
    ]);
  }

  Widget _list(BuildContext context, List<ChatMessage> list) {
    var lastMine = 0;
    for (final m in list) {
      if (m.mine && m.id > lastMine) lastMine = m.id;
    }
    final entries = _entries(list);
    return LayoutBuilder(
      builder: (context, box) {
        final side = sidePad(box.maxWidth, maxWidth: _columnWidth);
        return ListView.builder(
          reverse: true,
          padding: EdgeInsets.fromLTRB(side, 10, side, 10),
          itemCount: entries.length,
          itemBuilder: (context, i) {
            final e = entries[i];
            if (e.day != null) return _DayChip(_dayLabel(e.day!));
            final m = e.message;
            if (m == null) return const TypingBubble();
            return MessageBubble(key: ValueKey(m.id), message: m, live: m.id > lastMine, actions: _actions, startsGroup: e.startsGroup);
          },
        );
      },
    );
  }

  Widget _welcome(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final t = context.tk;
    const ideas = [
      (Icons.send_outlined, 'Send 500 to Mama'),
      (Icons.storefront_outlined, 'Pay till 123456'),
      (Icons.help_outline, 'What can you do?'),
    ];
    return LayoutBuilder(
      builder: (context, box) => SingleChildScrollView(
        child: ConstrainedBox(
          constraints: BoxConstraints(minHeight: box.maxHeight),
          child: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 460),
              child: Padding(
                padding: const EdgeInsets.all(28),
                child: Column(mainAxisSize: MainAxisSize.min, children: [
                  Container(
                    width: 76,
                    height: 76,
                    alignment: Alignment.center,
                    decoration: BoxDecoration(
                      color: t.p.surface,
                      border: Border.all(color: t.p.accentLine),
                      borderRadius: t.square ? null : BorderRadius.circular(t.radius * 1.5),
                    ),
                    child: const BrandMark(size: 44),
                  ),
                  const SizedBox(height: 20),
                  Text('Ask in your own words', style: text.titleLarge, textAlign: TextAlign.center),
                  const SizedBox(height: 8),
                  Text(
                    'Type, send a photo of a till or paybill, or record a voice note. Nothing moves until you approve it.',
                    textAlign: TextAlign.center,
                    style: text.bodyMedium?.copyWith(color: t.p.ink2),
                  ),
                  const SizedBox(height: 20),
                  Wrap(spacing: 8, runSpacing: 8, alignment: WrapAlignment.center, children: [
                    for (final idea in ideas)
                      ActionChip(
                        avatar: Icon(idea.$1, size: 18, color: t.p.ink),
                        label: Text(idea.$2),
                        backgroundColor: t.p.surface,
                        side: BorderSide(color: t.p.line),
                        shape: t.shape(outline: false, scale: 0.7),
                        onPressed: () {
                          _text.text = idea.$2;
                          _text.selection = TextSelection.collapsed(offset: idea.$2.length);
                          _focus.requestFocus();
                        },
                      ),
                  ]),
                ]),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _trayView(BuildContext context) {
    final t = context.tk;
    return _padded(
      SizedBox(
        height: 48,
        child: ListView.separated(
          scrollDirection: Axis.horizontal,
          padding: const EdgeInsets.only(top: 6, bottom: 4),
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
      ),
    );
  }

  /// The message box floats above the pattern as one rounded bar, at the same width as the messages.
  Widget _composer(BuildContext context) {
    final t = context.tk;
    final bar = t.square ? BorderRadius.zero : BorderRadius.circular(28);
    final decoration = BoxDecoration(color: t.p.surface, border: Border.all(color: t.p.lineStrong), borderRadius: bar);
    final Widget content;
    if (_recording) {
      content = Row(children: [
        IconButton(onPressed: _cancelVoice, icon: const Icon(Icons.delete_outline), tooltip: 'Discard recording'),
        Icon(Icons.fiber_manual_record, size: 14, color: t.p.bad),
        const SizedBox(width: 8),
        Expanded(child: Text('Recording  ${_elapsed()}', style: t.numeric(16, weight: FontWeight.w600))),
        IconButton.filled(onPressed: _sendVoice, icon: const Icon(Icons.send), tooltip: 'Send voice note'),
        const SizedBox(width: 4),
      ]);
    } else {
      final hasWords = _text.text.trim().isNotEmpty || _tray.isNotEmpty;
      content = Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
        IconButton(onPressed: _showAttachMenu, icon: const Icon(Icons.add_circle_outline), tooltip: 'Attach'),
        Expanded(
          child: TextField(
            controller: _text,
            focusNode: _focus,
            minLines: 1,
            maxLines: 5,
            keyboardType: TextInputType.multiline,
            textCapitalization: TextCapitalization.sentences,
            decoration: InputDecoration(
              hintText: _tray.isEmpty ? 'Message' : 'Add a caption',
              isDense: true,
              filled: false,
              border: InputBorder.none,
              enabledBorder: InputBorder.none,
              focusedBorder: InputBorder.none,
              contentPadding: const EdgeInsets.symmetric(horizontal: 4, vertical: 14),
            ),
          ),
        ),
        const SizedBox(width: 4),
        AnimatedSwitcher(
          duration: const Duration(milliseconds: 150),
          child: hasWords
              ? IconButton.filled(key: const ValueKey('send'), onPressed: _chat.sending ? null : _send, icon: const Icon(Icons.send), tooltip: 'Send')
              : IconButton.filled(key: const ValueKey('mic'), onPressed: _startVoice, icon: const Icon(Icons.mic), tooltip: 'Record a voice note'),
        ),
        const SizedBox(width: 4),
      ]);
    }
    return _padded(Container(decoration: decoration, child: content), top: 6, bottom: 8);
  }
}

class _DayChip extends StatelessWidget {
  const _DayChip(this.label);

  final String label;

  @override
  Widget build(BuildContext context) {
    final t = context.tk;
    return Center(
      child: Container(
        margin: const EdgeInsets.symmetric(vertical: 10),
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
        decoration: BoxDecoration(
          color: t.p.surface,
          border: Border.all(color: t.p.line),
          borderRadius: BorderRadius.circular(t.square ? 0 : 20),
        ),
        child: Text(label, style: Theme.of(context).textTheme.labelMedium),
      ),
    );
  }
}
