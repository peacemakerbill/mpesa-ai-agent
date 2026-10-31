import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:ganji_app/api/api_client.dart';
import 'package:ganji_app/chat/chat_controller.dart';
import 'package:ganji_app/models/chat.dart';

/// A small stand-in for the M-Ganji API chat endpoints.
class FakeChatServer {
  final List<Map<String, dynamic>> stored = [];
  final List<Map<String, dynamic>> posted = [];
  final List<String> afterValues = [];
  final List<String> uploadHeaders = [];
  int status = 200;
  bool typing = false;
  bool offline = false;
  int _next = 1;

  void say(String text, {String dir = 'out', String kind = 'TEXT'}) {
    stored.add({'id': _next++, 'dir': dir, 'kind': kind, 'at': 1700000000000 + _next, 'text': text});
  }

  Future<http.Response> handle(http.Request req) async {
    if (offline) throw http.ClientException('offline');
    if (status != 200) return http.Response(jsonEncode({'status': status, 'message': 'No.'}), status);
    final path = req.url.path;
    if (req.method == 'GET' && path == '/api/chat/messages') {
      final after = int.parse(req.url.queryParameters['after']!);
      afterValues.add('$after');
      final fresh = stored.where((m) => (m['id'] as int) > after).toList();
      return http.Response(jsonEncode({'messages': fresh, 'typing': typing, 'lastId': _next - 1}), 200);
    }
    if (req.method == 'POST' && path == '/api/chat/messages') {
      final body = jsonDecode(req.body) as Map<String, dynamic>;
      posted.add(body);
      stored.add({'id': _next++, 'dir': 'in', 'at': 1700000000000, ...body});
      return http.Response('{}', 200);
    }
    if (req.method == 'POST' && path == '/api/chat/media') {
      uploadHeaders.add('${req.headers['Content-Type']}|${req.headers['X-File-Name']}');
      return http.Response(jsonEncode({'mediaId': '11111111-1111-1111-1111-111111111111', 'mimeType': 'image/png', 'size': req.bodyBytes.length}), 200);
    }
    if (req.method == 'DELETE' && path == '/api/chat/messages') {
      stored.clear();
      return http.Response('{}', 200);
    }
    return http.Response('{}', 404);
  }
}

({ChatController chat, FakeChatServer server, List<String> expired}) _setup() {
  final server = FakeChatServer();
  final expired = <String>[];
  final api = ApiClient(baseUrl: 'http://x.test', client: MockClient(server.handle), tokenProvider: () async => 'tok');
  var n = 0;
  final chat = ChatController(
    api: api,
    onSessionExpired: () async => expired.add('expired'),
    pollEvery: const Duration(hours: 1),
    newId: () => 'id-${n++}',
  );
  return (chat: chat, server: server, expired: expired);
}

void main() {
  test('the first read fills the chat and later reads ask only for newer messages', () async {
    final s = _setup();
    s.server
      ..say('Hello, how can I help?')
      ..say('Ask me anything.');

    await s.chat.poll();
    expect(s.chat.loaded, isTrue);
    expect(s.chat.messages.map((m) => m.text), ['Hello, how can I help?', 'Ask me anything.']);
    expect(s.chat.cursor, 2);

    s.server.say('A new one');
    await s.chat.poll();
    expect(s.server.afterValues, ['0', '2']);
    expect(s.chat.messages.length, 3);
    s.chat.dispose();
  });

  test('the same message is never shown twice', () async {
    final s = _setup();
    s.server.say('One');
    await s.chat.poll();
    // The server repeating a message, as it would after a retry, must not double it.
    s.chat.messages.clear();
    await s.chat.poll();
    await s.chat.poll();
    expect(s.chat.messages.length, 1);
    s.chat.dispose();
  });

  test('typing follows the server', () async {
    final s = _setup();
    s.server.typing = true;
    await s.chat.poll();
    expect(s.chat.typing, isTrue);
    s.server.typing = false;
    await s.chat.poll();
    expect(s.chat.typing, isFalse);
    s.chat.dispose();
  });

  test('sending text posts it with a message id and shows the answer', () async {
    final s = _setup();
    expect(await s.chat.sendText('  send 500 to Mama  '), isTrue);
    expect(s.server.posted.single, {'clientMessageId': 'id-0', 'kind': 'TEXT', 'text': 'send 500 to Mama'});
    expect(s.chat.messages.single.mine, isTrue);
    expect(s.chat.sending, isFalse);
    s.chat.dispose();
  });

  test('empty text is not sent', () async {
    final s = _setup();
    expect(await s.chat.sendText('   '), isFalse);
    expect(s.server.posted, isEmpty);
    s.chat.dispose();
  });

  test('buttons, menu rows and contacts have the shapes the server expects', () async {
    final s = _setup();
    await s.chat.sendButton(const ChatButton('yes', 'Yes, send it'));
    await s.chat.sendRow(const ChatRow('r1', 'Send money'));
    await s.chat.sendContact('Mama Wanjiku', '0712345678');
    await s.chat.sendLocation(-1.2921, 36.8219, label: ' Nairobi ');
    expect(s.server.posted.map((m) => m['kind']), ['BUTTON_REPLY', 'LIST_REPLY', 'CONTACTS', 'LOCATION']);
    expect(s.server.posted[0]['actionId'], 'yes');
    expect(s.server.posted[0]['actionTitle'], 'Yes, send it');
    expect(s.server.posted[1]['actionId'], 'r1');
    expect(s.server.posted[2]['contactsSummary'], 'Mama Wanjiku 0712345678');
    expect(s.server.posted[3]['latitude'], -1.2921);
    expect(s.server.posted[3]['locationLabel'], 'Nairobi');
    s.chat.dispose();
  });

  test('a photo is uploaded first and then named in the message', () async {
    final s = _setup();
    final ok = await s.chat.sendAttachment(
      ChatAttachment(kind: 'IMAGE', bytes: Uint8List.fromList([1, 2, 3]), mimeType: 'image/png', fileName: 'receipt.png'),
      caption: ' my receipt ',
    );
    expect(ok, isTrue);
    expect(s.server.uploadHeaders.single, 'image/png|receipt.png');
    final m = s.server.posted.single;
    expect(m['kind'], 'IMAGE');
    expect(m['mediaId'], '11111111-1111-1111-1111-111111111111');
    expect(m['caption'], 'my receipt');
    expect(m.containsKey('fileName'), isFalse);
    s.chat.dispose();
  });

  test('a voice note is marked as one and has no caption', () async {
    final s = _setup();
    await s.chat.sendAttachment(
      ChatAttachment(kind: 'AUDIO', bytes: Uint8List(10), mimeType: 'audio/mp4', fileName: 'Voice note.m4a', voiceNote: true),
      caption: 'ignored',
    );
    final m = s.server.posted.single;
    expect(m['voiceNote'], true);
    expect(m.containsKey('caption'), isFalse);
    s.chat.dispose();
  });

  test('a document keeps its file name, and non ASCII letters are replaced in the header', () async {
    final s = _setup();
    await s.chat.sendAttachment(ChatAttachment(kind: 'DOCUMENT', bytes: Uint8List(4), mimeType: 'application/pdf', fileName: 'Statement é.pdf'));
    expect(s.server.posted.single['fileName'], 'Statement é.pdf');
    expect(s.server.uploadHeaders.single, 'application/pdf|Statement _.pdf');
    s.chat.dispose();
  });

  test('the kind is chosen from the MIME type', () {
    expect(ChatAttachment.kindFor('image/jpeg'), 'IMAGE');
    expect(ChatAttachment.kindFor('audio/mpeg'), 'AUDIO');
    expect(ChatAttachment.kindFor('video/mp4'), 'VIDEO');
    expect(ChatAttachment.kindFor('application/pdf'), 'DOCUMENT');
  });

  test('an ended sign-in stops the chat and tells the app', () async {
    final s = _setup();
    s.server.status = 401;
    await s.chat.poll();
    expect(s.expired, ['expired']);
    s.server.status = 200;
    expect(s.chat.error, isNull);
    s.chat.dispose();
  });

  test('a failed send shows a sentence and keeps the chat usable', () async {
    final s = _setup();
    s.server.status = 429;
    expect(await s.chat.sendText('hi'), isFalse);
    expect(s.chat.error, contains('The message was not sent'));
    expect(s.chat.sending, isFalse);
    s.server.status = 200;
    expect(await s.chat.sendText('hi again'), isTrue);
    expect(s.chat.error, isNull);
    s.chat.dispose();
  });

  test('losing the network shows a sentence and the next read recovers', () async {
    final s = _setup();
    s.server.offline = true;
    await s.chat.poll();
    expect(s.chat.error, contains('Could not reach'));
    expect(s.chat.loaded, isTrue);
    s.server
      ..offline = false
      ..say('Back');
    await s.chat.poll();
    expect(s.chat.error, isNull);
    expect(s.chat.messages.single.text, 'Back');
    s.chat.dispose();
  });

  test('clearing the chat empties it', () async {
    final s = _setup();
    s.server.say('Old');
    await s.chat.poll();
    expect(await s.chat.clear(), isTrue);
    expect(s.chat.messages, isEmpty);
    s.chat.dispose();
  });
}
