import 'package:flutter_test/flutter_test.dart';
import 'package:ganji_app/models/chat.dart';

void main() {
  group('ChatMessage.fromJson', () {
    test('reads a plain text message from the customer', () {
      final m = ChatMessage.fromJson({'id': 3, 'dir': 'in', 'kind': 'TEXT', 'at': 1761770000000, 'text': 'send 500 to Mama'});
      expect(m.id, 3);
      expect(m.mine, isTrue);
      expect(m.kind, 'TEXT');
      expect(m.text, 'send 500 to Mama');
      expect(m.at.millisecondsSinceEpoch, 1761770000000);
      expect(m.requestId, isNull);
    });

    test('reads buttons and menu rows', () {
      final b = ChatMessage.fromJson({
        'id': 4, 'dir': 'out', 'kind': 'BUTTONS', 'at': 1, 'text': 'Send KES 500?', 'footer': 'Nothing is sent yet',
        'buttons': [{'id': 'WCAN:abc', 'title': 'Cancel'}, {'bad': true}],
      });
      expect(b.mine, isFalse);
      expect(b.buttons.map((x) => x.title), ['Cancel']);
      expect(b.footer, 'Nothing is sent yet');
      final l = ChatMessage.fromJson({
        'id': 5, 'dir': 'out', 'kind': 'LIST', 'at': 1, 'text': 'Pick one', 'listTitle': 'Money',
        'rows': [{'id': 'MENU:send', 'title': 'Send money', 'description': 'To a number'}, {'id': 'MENU:till', 'title': 'Pay a Till'}],
      });
      expect(l.rows, hasLength(2));
      expect(l.rows.first.description, 'To a number');
      expect(l.rows.last.description, isNull);
    });

    test('reads media, location and contact fields', () {
      final m = ChatMessage.fromJson({'id': 6, 'dir': 'in', 'kind': 'AUDIO', 'at': 1, 'mediaId': 'm1', 'mimeType': 'audio/mp4', 'voiceNote': true});
      expect(m.isMedia, isTrue);
      expect(m.voiceNote, isTrue);
      final loc = ChatMessage.fromJson({'id': 7, 'dir': 'in', 'kind': 'LOCATION', 'at': 1, 'latitude': -1.28, 'longitude': 36.81, 'locationLabel': 'Nairobi'});
      expect(loc.latitude, -1.28);
      expect(loc.isMedia, isFalse);
      final c = ChatMessage.fromJson({'id': 8, 'dir': 'out', 'kind': 'CONTACT_LOOKUP', 'at': 1, 'query': 'Mama'});
      expect(c.query, 'Mama');
    });

    test('finds the request id in the assistant words, only for the assistant', () {
      const id = '0b7f1f0e-6f3a-4a0e-9d57-3d9d6b6f2c11';
      final out = ChatMessage.fromJson({'id': 1, 'dir': 'out', 'kind': 'TEXT', 'at': 1, 'text': 'Open https://pay.example.com/a/$id to approve'});
      expect(out.requestId, id);
      final mine = ChatMessage.fromJson({'id': 2, 'dir': 'in', 'kind': 'TEXT', 'at': 1, 'text': 'https://pay.example.com/a/$id'});
      expect(mine.requestId, isNull);
      expect(requestIdIn('no link here'), isNull);
      expect(requestIdIn('/a/not-a-uuid'), isNull);
    });
  });

  test('ChatPage reads messages, typing and the last id', () {
    final p = ChatPage.fromJson({
      'messages': [{'id': 1, 'dir': 'out', 'kind': 'TEXT', 'at': 1, 'text': 'hi'}],
      'typing': true,
      'lastId': 9,
    });
    expect(p.messages, hasLength(1));
    expect(p.typing, isTrue);
    expect(p.lastId, 9);
    expect(ChatPage.fromJson({}).messages, isEmpty);
  });

  group('parseRich', () {
    test('leaves plain text alone', () {
      expect(parseRich('hello there'), [const RichSpan('hello there')]);
    });

    test('reads bold with one or two stars, italic, code and links', () {
      expect(parseRich('*Send KES 500* to Mama'), [const RichSpan('Send KES 500', bold: true), const RichSpan(' to Mama')]);
      expect(parseRich('**Done** now'), [const RichSpan('Done', bold: true), const RichSpan(' now')]);
      expect(parseRich('this is _new_'), [const RichSpan('this is '), const RichSpan('new', italic: true)]);
      expect(parseRich('code `SGL123`'), [const RichSpan('code '), const RichSpan('SGL123', code: true)]);
      expect(parseRich('open https://x.example/a/1, then'), [
        const RichSpan('open '),
        const RichSpan('https://x.example/a/1', link: 'https://x.example/a/1'),
        const RichSpan(', then'),
      ]);
    });

    test('does not treat snake_case or a lone star as markup', () {
      expect(parseRich('use my_account_id here'), [const RichSpan('use my_account_id here')]);
      expect(parseRich('5 * 3 = 15'), [const RichSpan('5 * 3 = 15')]);
    });
  });

  test('splitContactSummary takes the number from the end', () {
    expect(splitContactSummary('Mama Wanjiku 0712345678').name, 'Mama Wanjiku');
    expect(splitContactSummary('Mama Wanjiku 0712345678').phone, '0712345678');
    expect(splitContactSummary('0712345678').name, '');
  });
}
