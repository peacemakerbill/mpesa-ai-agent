import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/foundation.dart';

import '../api/api_client.dart';
import '../models/chat.dart';

/// A file the customer attached: a photo, a document, a video or a voice note.
class ChatAttachment {
  const ChatAttachment({required this.kind, required this.bytes, required this.mimeType, required this.fileName, this.voiceNote = false});

  /// IMAGE, DOCUMENT, AUDIO or VIDEO.
  final String kind;
  final Uint8List bytes;
  final String mimeType;
  final String fileName;
  final bool voiceNote;

  /// The kind that suits a MIME type, for files chosen from the phone.
  static String kindFor(String mimeType) {
    final m = mimeType.toLowerCase();
    if (m.startsWith('image/')) return 'IMAGE';
    if (m.startsWith('audio/')) return 'AUDIO';
    if (m.startsWith('video/')) return 'VIDEO';
    return 'DOCUMENT';
  }
}

/// The conversation with the assistant: reads new messages every second or two while the chat is open and sends
/// what the customer writes, taps or attaches. It holds no widgets so it can be tested with a fake server.
class ChatController extends ChangeNotifier {
  ChatController({
    required this.api,
    required this.onSessionExpired,
    this.pollEvery = const Duration(milliseconds: 1500),
    String Function()? newId,
  }) : _newId = newId ?? _defaultId;

  final ApiClient api;
  final Future<void> Function() onSessionExpired;
  final Duration pollEvery;
  final String Function() _newId;

  static int _counter = 0;
  static String _defaultId() => 'app-${DateTime.now().microsecondsSinceEpoch}-${_counter++}';

  final List<ChatMessage> messages = [];
  bool typing = false;

  /// False until the first answer from the server, so the screen can show a spinner and not an empty chat.
  bool loaded = false;
  bool sending = false;

  /// A sentence for the customer when the last read or send failed, or null.
  String? error;

  Timer? _timer;
  bool _polling = false;
  bool _disposed = false;

  /// The highest message number seen. Not the server's lastId, which can run ahead of what was returned.
  int get cursor => messages.isEmpty ? 0 : messages.map((m) => m.id).reduce((a, b) => a > b ? a : b);

  void start() {
    _timer?.cancel();
    poll();
    _timer = Timer.periodic(pollEvery, (_) => poll());
  }

  /// Stops reading while the chat is not on screen.
  void pause() {
    _timer?.cancel();
    _timer = null;
  }

  Future<void> poll() async {
    if (_polling || _disposed) return;
    _polling = true;
    try {
      final page = await api.chatMessages(after: cursor);
      var changed = !loaded || page.typing != typing;
      final known = {for (final m in messages) m.id};
      for (final m in page.messages) {
        if (known.add(m.id)) {
          messages.add(m);
          changed = true;
        }
      }
      messages.sort((a, b) => a.id.compareTo(b.id));
      typing = page.typing;
      loaded = true;
      if (error != null) {
        error = null;
        changed = true;
      }
      if (changed) _notify();
    } on ApiException catch (e) {
      if (e.unauthorized) {
        pause();
        await onSessionExpired();
        return;
      }
      loaded = true;
      error = e.message;
      _notify();
    } on Exception {
      loaded = true;
      error = 'Could not reach the assistant. Check your internet connection.';
      _notify();
    } finally {
      _polling = false;
    }
  }

  // ------------------------------------------------------------ sending

  Future<bool> sendText(String text) {
    final t = text.trim();
    if (t.isEmpty) return Future.value(false);
    return _post({'kind': 'TEXT', 'text': t});
  }

  Future<bool> sendButton(ChatButton b) => _post({'kind': 'BUTTON_REPLY', 'actionId': b.id, 'actionTitle': b.title});

  Future<bool> sendRow(ChatRow r) => _post({'kind': 'LIST_REPLY', 'actionId': r.id, 'actionTitle': r.title});

  Future<bool> sendContact(String name, String phone) {
    final summary = name.trim().isEmpty ? phone.trim() : '${name.trim()} ${phone.trim()}';
    return _post({'kind': 'CONTACTS', 'contactsSummary': summary});
  }

  Future<bool> sendLocation(double latitude, double longitude, {String? label}) =>
      _post({'kind': 'LOCATION', 'latitude': latitude, 'longitude': longitude, if (label != null && label.trim().isNotEmpty) 'locationLabel': label.trim()});

  /// Uploads the file, then sends a message that names it. Returns false when either step failed.
  Future<bool> sendAttachment(ChatAttachment a, {String? caption}) async {
    sending = true;
    _notify();
    try {
      final id = await api.uploadChatFile(a.bytes, mimeType: a.mimeType, fileName: a.fileName);
      final fields = <String, dynamic>{
        'kind': a.kind,
        'mediaId': id,
        'mimeType': a.mimeType,
        if (a.kind == 'AUDIO') 'voiceNote': a.voiceNote,
        if (a.kind == 'DOCUMENT') 'fileName': a.fileName,
        if (caption != null && caption.trim().isNotEmpty && a.kind != 'AUDIO') 'caption': caption.trim(),
      };
      return await _post(fields, alreadySending: true);
    } on ApiException catch (e) {
      return await _failed(e, '${a.fileName} was not uploaded');
    } on Exception {
      error = '${a.fileName} was not uploaded. Check your internet connection.';
      sending = false;
      _notify();
      return false;
    }
  }

  Future<bool> clear() async {
    try {
      await api.clearChat();
      messages.clear();
      typing = false;
      error = null;
      _notify();
      return true;
    } on ApiException catch (e) {
      return _failed(e, 'The chat was not cleared');
    }
  }

  Future<bool> _post(Map<String, dynamic> fields, {bool alreadySending = false}) async {
    if (!alreadySending) {
      sending = true;
      _notify();
    }
    try {
      await api.sendChat({'clientMessageId': _newId(), ...fields});
      sending = false;
      error = null;
      _notify();
      await poll();
      return true;
    } on ApiException catch (e) {
      return _failed(e, 'The message was not sent');
    } on Exception {
      error = 'The message was not sent. Check your internet connection.';
      sending = false;
      _notify();
      return false;
    }
  }

  Future<bool> _failed(ApiException e, String what) async {
    sending = false;
    if (e.unauthorized) {
      pause();
      _notify();
      await onSessionExpired();
      return false;
    }
    error = '$what. ${e.message}';
    _notify();
    return false;
  }

  void _notify() {
    if (!_disposed) notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    _timer?.cancel();
    super.dispose();
  }
}
