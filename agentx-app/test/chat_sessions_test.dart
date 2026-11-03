import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:agentx_app/api/api_client.dart';
import 'package:agentx_app/chat/chat_controller.dart';
import 'package:agentx_app/models/chat_session.dart';

/// A small stand-in for the chat list endpoints of the Daraja Agent-X API: one live chat and the others kept aside.
class FakeChatsServer {
  final List<Map<String, dynamic>> chats = [];
  final Map<String, List<Map<String, dynamic>>> kept = {};
  List<Map<String, dynamic>> live = [];
  String active = '';
  bool supported = true;
  String? busy;
  final List<String> calls = [];
  int _seq = 0;
  int _chatNumber = 0;

  FakeChatsServer() {
    active = _addChat('New chat');
  }

  String _addChat(String title) {
    final id = 'c${++_chatNumber}';
    chats.add({'id': id, 'title': title, 'pinned': false});
    return id;
  }

  /// A chat that is already in the list. The first one added with [live] true is the chat the assistant is in.
  void addKept(String title, List<String> texts) {
    final id = _addChat(title);
    kept[id] = [for (final t in texts) _message(t)];
  }

  void setLive(String title, List<String> texts) {
    chats.firstWhere((c) => c['id'] == active)['title'] = title;
    live = [for (final t in texts) _message(t)];
  }

  Map<String, dynamic> _message(String text) => {'id': ++_seq, 'dir': 'out', 'kind': 'TEXT', 'at': 1700000000000 + _seq, 'text': text};

  List<Map<String, dynamic>> _messagesOf(String id) => id == active ? live : (kept[id] ?? []);

  Map<String, dynamic> listing() {
    final out = [
      for (final c in chats)
        {
          'id': c['id'],
          'title': c['title'],
          'pinned': c['pinned'],
          'active': c['id'] == active,
          'createdAt': 1700000000000,
          'updatedAt': 1700000000000,
          'preview': _messagesOf(c['id'] as String).isEmpty ? '' : _messagesOf(c['id'] as String).last['text'],
          'messages': _messagesOf(c['id'] as String).length,
        },
    ]..sort((a, b) => (b['pinned'] == true ? 1 : 0).compareTo(a['pinned'] == true ? 1 : 0));
    return {'activeId': active, 'chats': out};
  }

  http.Response _json(Object body, [int status = 200]) => http.Response(jsonEncode(body), status);

  Future<http.Response> handle(http.Request req) async {
    final path = req.url.path;
    calls.add('${req.method} $path');
    if (req.method == 'GET' && path == '/api/chat/messages') {
      final after = int.parse(req.url.queryParameters['after']!);
      return _json({'messages': live.where((m) => (m['id'] as int) > after).toList(), 'typing': false, 'lastId': _seq});
    }
    if (req.method == 'DELETE' && path == '/api/chat/messages') {
      live = [];
      return _json({});
    }
    if (!path.startsWith('/api/chat/sessions')) return _json({}, 404);
    if (!supported) return _json({'status': 404, 'message': 'Not found'}, 404);
    if (req.method != 'GET' && busy != null) return _json({'status': 409, 'message': busy}, 409);

    final parts = path.split('/').where((p) => p.isNotEmpty).toList();   // api, chat, sessions, [id], [action]
    final id = parts.length > 3 ? parts[3] : null;
    final action = parts.length > 4 ? parts[4] : null;
    if (id != null && !chats.any((c) => c['id'] == id)) return _json({'status': 404, 'message': 'That chat no longer exists.'}, 404);

    if (req.method == 'GET' && id == null) return _json(listing());
    if (req.method == 'POST' && id == null) {
      if (live.isNotEmpty) {
        kept[active] = live;
        live = [];
        active = _addChat('New chat');
      }
      return _json(listing());
    }
    if (req.method == 'POST' && action == 'open') {
      if (id != active) {
        kept[active] = live;
        live = [for (final m in kept.remove(id) ?? <Map<String, dynamic>>[]) {...m, 'id': ++_seq}];
        active = id!;
      }
      return _json(listing());
    }
    final body = req.body.isEmpty ? <String, dynamic>{} : jsonDecode(req.body) as Map<String, dynamic>;
    if (req.method == 'PUT' && action == 'title') {
      chats.firstWhere((c) => c['id'] == id)['title'] = (body['title'] as String).trim().isEmpty ? 'Automatic name' : body['title'];
      return _json(listing());
    }
    if (req.method == 'PUT' && action == 'pin') {
      chats.firstWhere((c) => c['id'] == id)['pinned'] = body['pinned'];
      return _json(listing());
    }
    if (req.method == 'DELETE' && id != null) {
      chats.removeWhere((c) => c['id'] == id);
      kept.remove(id);
      if (id == active) {
        live = [];
        active = _addChat('New chat');
      }
      return _json(listing());
    }
    if (req.method == 'DELETE') {
      chats.clear();
      kept.clear();
      live = [];
      active = _addChat('New chat');
      return _json(listing());
    }
    return _json({}, 404);
  }
}

({ChatController chat, FakeChatsServer server}) _setup() {
  final server = FakeChatsServer();
  final api = ApiClient(baseUrl: 'http://x.test', client: MockClient(server.handle), tokenProvider: () async => 'tok');
  final chat = ChatController(api: api, onSessionExpired: () async {}, pollEvery: const Duration(hours: 1));
  return (chat: chat, server: server);
}

/// Lets the reads that start on their own finish.
Future<void> settle() => Future<void>.delayed(const Duration(milliseconds: 40));

List<String?> texts(ChatController c) => c.messages.map((m) => m.text).toList();

void main() {
  group('the chat model', () {
    test('a chat is read from the server answer, with a name when none is sent', () {
      final s = ChatSession.fromJson({'id': 'a1', 'title': '  ', 'pinned': true, 'active': true, 'createdAt': 1700000000000, 'updatedAt': 1700000100000, 'preview': 'Hi', 'messages': 2});

      expect(s.id, 'a1');
      expect(s.title, 'New chat');
      expect(s.pinned, isTrue);
      expect(s.active, isTrue);
      expect(s.preview, 'Hi');
      expect(s.messages, 2);
      expect(s.isEmpty, isFalse);
      expect(s.updatedAt.millisecondsSinceEpoch, 1700000100000);
    });

    test('a list keeps what is readable and ignores the rest', () {
      final list = ChatList.fromJson({
        'activeId': 'a1',
        'chats': [
          {'id': 'a1', 'title': 'One'},
          'broken',
          {'id': 'a2', 'title': 'Two'},
        ],
      });

      expect(list.activeId, 'a1');
      expect(list.chats.map((c) => c.title), ['One', 'Two']);
      expect(ChatList.fromJson(const {}).chats, isEmpty);
    });

    test('searching looks in the name and the last message and ignores capitals', () {
      final s = ChatSession.fromJson({'id': 'a', 'title': 'Rent and bills', 'preview': 'Sent KES 500 to Mama'});

      expect(s.matches(''), isTrue);
      expect(s.matches('  RENT '), isTrue);
      expect(s.matches('mama'), isTrue);
      expect(s.matches('airtime'), isFalse);
    });

    test('the time is shown for today, then Yesterday, then the date', () {
      final now = DateTime(2026, 11, 4, 15, 30);

      expect(chatWhen(DateTime(2026, 11, 4, 9, 5), now), '09:05');
      expect(chatWhen(DateTime(2026, 11, 3, 23, 59), now), 'Yesterday');
      expect(chatWhen(DateTime(2026, 10, 20), now), '20 Oct');
      expect(chatWhen(DateTime(2025, 1, 2), now), '2 Jan 2025');
    });
  });

  group('the list of chats', () {
    test('the list is read and the active chat is known', () async {
      final s = _setup();
      s.server.setLive('Wallet', ['Hello']);
      s.server.addKept('Rent', ['Pay rent']);

      await s.chat.refreshSessions(force: true);

      expect(s.chat.sessionsLoaded, isTrue);
      expect(s.chat.sessions.map((c) => c.title), ['Wallet', 'Rent']);
      expect(s.chat.activeSession?.title, 'Wallet');
      s.chat.dispose();
    });

    test('a new chat starts empty and the old one stays in the list', () async {
      final s = _setup();
      s.server.setLive('Wallet', ['Hello', 'How can I help?']);
      await s.chat.poll();
      await s.chat.refreshSessions(force: true);
      expect(texts(s.chat), ['Hello', 'How can I help?']);

      expect(await s.chat.newChat(), isNull);
      await settle();

      expect(s.chat.messages, isEmpty);
      expect(s.chat.loaded, isTrue);
      expect(s.chat.sessions.length, 2);
      expect(s.chat.activeSession?.title, 'New chat');
      expect(s.chat.sessions.firstWhere((c) => c.title == 'Wallet').messages, 2);
      s.chat.dispose();
    });

    test('opening an old chat brings its messages back and starts reading from the beginning', () async {
      final s = _setup();
      s.server.setLive('Wallet', ['Wallet one']);
      s.server.addKept('Rent', ['Rent one', 'Rent two']);
      await s.chat.poll();
      await s.chat.refreshSessions(force: true);
      final rent = s.chat.sessions.firstWhere((c) => c.title == 'Rent');

      expect(await s.chat.openChat(rent.id), isNull);
      await settle();

      expect(s.chat.activeSessionId, rent.id);
      expect(texts(s.chat), ['Rent one', 'Rent two']);
      expect(s.chat.activeSession?.title, 'Rent');
      expect(s.chat.switching, isFalse);
      s.chat.dispose();
    });

    test('opening the chat that is already open asks the server for nothing', () async {
      final s = _setup();
      s.server.setLive('Wallet', ['Hello']);
      await s.chat.refreshSessions(force: true);
      s.server.calls.clear();

      expect(await s.chat.openChat(s.chat.activeSessionId!), isNull);

      expect(s.server.calls, isEmpty);
      s.chat.dispose();
    });

    test('renaming, pinning and deleting redraw from the answer', () async {
      final s = _setup();
      s.server.setLive('Wallet', ['Hello']);
      s.server.addKept('Rent', ['Pay rent']);
      await s.chat.poll();
      await s.chat.refreshSessions(force: true);
      final rent = s.chat.sessions.firstWhere((c) => c.title == 'Rent');

      expect(await s.chat.renameChat(rent.id, 'Rent and bills'), isNull);
      expect(s.chat.sessions.map((c) => c.title), contains('Rent and bills'));

      expect(await s.chat.pinChat(rent.id, true), isNull);
      expect(s.chat.sessions.first.id, rent.id);
      expect(s.chat.sessions.first.pinned, isTrue);

      expect(await s.chat.deleteChat(rent.id), isNull);
      expect(s.chat.sessions.map((c) => c.title), ['Wallet']);
      expect(texts(s.chat), ['Hello']);   // nothing on screen changed: the open chat was not the one deleted
      s.chat.dispose();
    });

    test('deleting the open chat leaves a new empty one on screen', () async {
      final s = _setup();
      s.server.setLive('Wallet', ['Hello']);
      s.server.addKept('Rent', ['Pay rent']);
      await s.chat.poll();
      await s.chat.refreshSessions(force: true);
      expect(texts(s.chat), ['Hello']);

      expect(await s.chat.deleteChat(s.chat.activeSessionId!), isNull);
      await settle();

      expect(s.chat.messages, isEmpty);
      expect(s.chat.activeSession?.title, 'New chat');
      expect(s.chat.sessions.map((c) => c.title), unorderedEquals(['Rent', 'New chat']));
      s.chat.dispose();
    });

    test('deleting all chats leaves one empty chat', () async {
      final s = _setup();
      s.server.setLive('Wallet', ['Hello']);
      s.server.addKept('Rent', ['Pay rent']);
      await s.chat.poll();
      await s.chat.refreshSessions(force: true);

      expect(await s.chat.deleteAllChats(), isNull);
      await settle();

      expect(s.chat.sessions.length, 1);
      expect(s.chat.messages, isEmpty);
      s.chat.dispose();
    });

    test('a refusal comes back as a sentence and the screen stays as it was', () async {
      final s = _setup();
      s.server.setLive('Wallet', ['Hello']);
      await s.chat.poll();
      await s.chat.refreshSessions(force: true);
      s.server.busy = 'The assistant is still replying. Try again in a moment.';

      final err = await s.chat.newChat();

      expect(err, 'The assistant is still replying. Try again in a moment.');
      expect(texts(s.chat), ['Hello']);
      expect(s.chat.switching, isFalse);
      s.chat.dispose();
    });

    test('a chat that was deleted somewhere else is refused and the list is read again', () async {
      final s = _setup();
      s.server.setLive('Wallet', ['Hello']);
      s.server.addKept('Rent', ['Pay rent']);
      await s.chat.refreshSessions(force: true);
      final rent = s.chat.sessions.firstWhere((c) => c.title == 'Rent');
      s.server.chats.removeWhere((c) => c['id'] == rent.id);

      final err = await s.chat.openChat(rent.id);
      await settle();

      expect(err, 'That chat no longer exists.');
      expect(s.chat.sessions.map((c) => c.title), ['Wallet']);
      s.chat.dispose();
    });

    test('a chat opened on another device replaces what is on screen at the next read of the list', () async {
      final s = _setup();
      s.server.setLive('Wallet', ['Wallet one']);
      s.server.addKept('Rent', ['Rent one']);
      await s.chat.poll();
      await s.chat.refreshSessions(force: true);
      expect(texts(s.chat), ['Wallet one']);
      final rent = s.chat.sessions.firstWhere((c) => c.title == 'Rent');

      // The other device opens Rent
      s.server.kept[s.server.active] = s.server.live;
      s.server.live = s.server.kept.remove(rent.id)!;
      s.server.active = rent.id;
      await s.chat.refreshSessions(force: true);
      await settle();

      expect(s.chat.activeSessionId, rent.id);
      expect(texts(s.chat), ['Rent one']);
      s.chat.dispose();
    });

    test('an older server without chat lists keeps working with the one chat', () async {
      final s = _setup();
      s.server.supported = false;
      s.server.setLive('Wallet', ['Hello']);

      await s.chat.poll();
      await settle();

      expect(texts(s.chat), ['Hello']);
      expect(s.chat.sessionsSupported, isFalse);
      s.server.calls.clear();
      await s.chat.refreshSessions(force: true);
      expect(s.server.calls, isEmpty);   // it is not asked again
      s.chat.dispose();
    });

    test('clearing the chat reads the list again so the name can go back to New chat', () async {
      final s = _setup();
      s.server.setLive('Wallet', ['Hello']);
      await s.chat.poll();
      await s.chat.refreshSessions(force: true);
      s.server.calls.clear();

      expect(await s.chat.clear(), isTrue);
      await settle();

      expect(s.chat.messages, isEmpty);
      expect(s.server.calls, contains('GET /api/chat/sessions'));
      s.chat.dispose();
    });
  });
}
