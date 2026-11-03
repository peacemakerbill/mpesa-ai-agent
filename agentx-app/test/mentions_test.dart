import 'package:flutter_test/flutter_test.dart';
import 'package:agentx_app/chat/mentions.dart';
import 'package:agentx_app/models/business_account.dart';

BusinessAccount _account(String label, {String shortcode = '174379', String till = ''}) => BusinessAccount.fromJson({
      'id': '11111111-1111-1111-1111-${label.hashCode.abs().toString().padLeft(12, '0').substring(0, 12)}',
      'label': label,
      'environment': 'SANDBOX',
      'shortcodeType': 'BUY_GOODS',
      'shortcode': shortcode,
      'tillNumber': till,
      'accountReference': '',
      'parentId': null,
      'inheritsCredentials': false,
      'consumerKeyHint': '1234',
      'hasConsumerSecret': true,
      'initiatorName': '',
      'hasInitiatorPassword': false,
      'hasPasskey': false,
      'capabilities': <Map<String, Object?>>[],
      'verifiedAt': null,
    });

void main() {
  group('finding the @ being typed', () {
    test('an @ at the end of the text opens the picker with what follows it', () {
      final q = activeMention('send 500 to @Ma', 15);
      expect(q, isNotNull);
      expect(q!.start, 12);
      expect(q.query, 'Ma');
    });

    test('a lone @ gives an empty query', () {
      final q = activeMention('@', 1);
      expect(q!.start, 0);
      expect(q.query, '');
    });

    test('an email address never opens the picker', () {
      expect(activeMention('write to bill@example.com', 25), isNull);
    });

    test('a cursor before the @ or no @ at all gives nothing', () {
      expect(activeMention('hi @x', 2), isNull);
      expect(activeMention('hello', 5), isNull);
      expect(activeMention('', 0), isNull);
    });

    test('a new line or a very long run ends it', () {
      expect(activeMention('@a\nb', 4), isNull);
      expect(activeMention('@${'x' * (maxMentionLength + 1)}', maxMentionLength + 2), isNull);
    });

    test('the last @ before the cursor is the one used', () {
      final q = activeMention('check @one and @tw', 18);
      expect(q!.query, 'tw');
      expect(q.start, 15);
    });

    test('an account name with a space is kept whole', () {
      expect(activeMention('@Main sh', 8)!.query, 'Main sh');
    });
  });

  group('putting the choice into the text', () {
    test('the @ and what was typed are replaced and a space follows', () {
      final q = activeMention('send 500 to @Ma', 15)!;
      final r = applyMention('send 500 to @Ma', 15, q, '@Main shop');
      expect(r.text, 'send 500 to @Main shop ');
      expect(r.cursor, r.text.length);
    });

    test('no extra space when one already follows', () {
      final q = activeMention('pay @Ma now', 7)!;
      final r = applyMention('pay @Ma now', 7, q, '@Main shop');
      expect(r.text, 'pay @Main shop now');
      expect(r.cursor, 14);
    });

    test('a contact name replaces the @ without adding an @', () {
      final q = activeMention('send 500 to @mam', 16)!;
      final r = applyMention('send 500 to @mam', 16, q, 'Mama');
      expect(r.text, 'send 500 to Mama ');
    });
  });

  group('what the picker offers', () {
    final accounts = [
      _account('Bakery main road', shortcode: '600111'),
      _account('Main shop', till: '9876543'),
      _account('School fees', shortcode: '400200'),
    ];

    test('the best match comes first and the contacts row is always last', () {
      final items = mentionSuggestions(accounts, 'ma');
      expect(items.map((i) => i.title), ['Main shop', 'Bakery main road', 'Find "ma" in contacts']);
      expect(items.last.kind, MentionKind.contacts);
      expect(items.last.query, 'ma');
    });

    test('nothing typed lists the accounts, up to the limit', () {
      final items = mentionSuggestions(accounts, '', limit: 2);
      expect(items, hasLength(3));
      expect(items.last.title, 'Choose a contact');
    });

    test('a till or shortcode finds its account', () {
      expect(mentionSuggestions(accounts, '98765').first.account!.label, 'Main shop');
      expect(mentionSuggestions(accounts, '4002').first.account!.label, 'School fees');
    });

    test('a name that matches no account still offers the contacts', () {
      final items = mentionSuggestions(accounts, 'zzz');
      expect(items, hasLength(1));
      expect(items.single.kind, MentionKind.contacts);
    });

    test('an account is inserted by its full name', () {
      final item = mentionSuggestions(accounts, 'school').first;
      expect(item.insertText, '@School fees');
      expect(item.subtitle, contains('Sandbox'));
    });
  });
}
