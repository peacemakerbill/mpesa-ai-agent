import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:mpesa_companion/contacts/contact_models.dart';
import 'package:mpesa_companion/contacts/finder_controller.dart';
import 'package:mpesa_companion/contacts/parsers.dart';
import 'package:mpesa_companion/contacts/sources.dart';

const _people = [
  ContactEntry('Mama Wanjiku', [ContactPhone('+254 712 345 678', 'mobile')]),
  ContactEntry('Brian Otieno', [ContactPhone('0722 111 222', 'home')]),
  ContactEntry('Zoë Achieng', [ContactPhone('0733000111')]),
];

class FakeTokens implements AccessTokens {
  FakeTokens({this.microsoftConfigured = true});

  @override
  final bool microsoftConfigured;
  final forgotten = <String>[];

  @override
  Future<String> google() async => 'g-token';

  @override
  Future<String> microsoft() async => 'm-token';

  @override
  Future<void> forget({required bool google}) async => forgotten.add(google ? 'google' : 'microsoft');
}

class FakeAddressBook implements AddressBook {
  FakeAddressBook({this.allowed = true});

  bool allowed;
  int reads = 0;
  ContactEntry? chosen;

  @override
  Future<bool> ensurePermission() async => allowed;

  @override
  Future<List<ContactEntry>> all() async {
    reads++;
    return _people;
  }

  @override
  Future<ContactEntry?> chooseWithSystemPicker() async => chosen;
}

class FakeFiles implements ContactFileReader {
  FakeFiles(this.file);

  ({String name, String text})? file;

  @override
  Future<({String name, String text})?> pick() async => file;
}

http.Client _unused() => MockClient((_) async => http.Response('{}', 200));

ContactFinderController _finder({FakeAddressBook? book, FakeFiles? files, bool microsoft = true, http.Client? client}) {
  final tokens = FakeTokens(microsoftConfigured: microsoft);
  return ContactFinderController(
    addressBook: book ?? FakeAddressBook(),
    google: GoogleContactsClient(client: client ?? _unused(), tokens: tokens),
    microsoft: MicrosoftContactsClient(client: client ?? _unused(), tokens: tokens),
    files: files ?? FakeFiles(null),
    microsoftConfigured: microsoft,
  );
}

void main() {
  group('numbers', () {
    test('Kenyan numbers in any spelling become 07 or 01 numbers', () {
      expect(kenyanPhone('+254 712 345 678'), '0712345678');
      expect(kenyanPhone('254712345678'), '0712345678');
      expect(kenyanPhone('712345678'), '0712345678');
      expect(kenyanPhone('0712345678'), '0712345678');
      expect(kenyanPhone('00254712345678'), '0712345678');
      expect(kenyanPhone('0712-345-678'), '0712345678');
    });

    test('other countries keep the plus and the country code', () {
      expect(kenyanPhone('+44 7911 123456'), '+447911123456');
      expect(kenyanPhone(''), '');
    });

    test('a number needs at least seven digits', () {
      expect(looksLikeNumber('0712345678'), isTrue);
      expect(looksLikeNumber('12345'), isFalse);
      expect(looksLikeNumber('Mama'), isFalse);
    });
  });

  group('filter', () {
    test('matches every word of the query in the name', () {
      expect(filterContacts(_people, 'wanj').map((c) => c.name), ['Mama Wanjiku']);
      expect(filterContacts(_people, 'mama wanj').map((c) => c.name), ['Mama Wanjiku']);
      expect(filterContacts(_people, 'brian mama'), isEmpty);
    });

    test('matches digits whatever the spelling of the number', () {
      expect(filterContacts(_people, '0712').map((c) => c.name), ['Mama Wanjiku']);
      expect(filterContacts(_people, '254712').map((c) => c.name), ['Mama Wanjiku']);
      expect(filterContacts(_people, '722 111').map((c) => c.name), ['Brian Otieno']);
    });

    test('ignores accents and case', () {
      expect(filterContacts(_people, 'ZOE').map((c) => c.name), ['Zoë Achieng']);
    });

    test('an empty query keeps the first few, and a limit applies', () {
      expect(filterContacts(_people, '').length, 3);
      expect(filterContacts(_people, '', limit: 2).length, 2);
      expect(filterContacts(_people, 'nobody'), isEmpty);
    });
  });

  group('vCard', () {
    const vcf = 'BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Mama Wanjiku\r\nTEL;TYPE=CELL:+254 712 345 678\r\nEND:VCARD\r\n'
        'BEGIN:VCARD\r\nVERSION:3.0\r\nN:Otieno;Brian;;;\r\nTEL;TYPE=HOME:0722 111 222\r\nTEL:020 1234567\r\nEND:VCARD\r\n'
        'BEGIN:VCARD\r\nVERSION:3.0\r\nFN:No Phone\r\nEND:VCARD\r\n';

    test('reads names and numbers and leaves out people without a number', () {
      final list = parseVcf(vcf);
      expect(list.map((c) => c.name), ['Mama Wanjiku', 'Brian Otieno']);
      expect(list.first.phones.single.number, '+254 712 345 678');
      expect(list.first.phones.single.label, 'cell');
      expect(list[1].phones.map((p) => p.number), ['0722 111 222', '020 1234567']);
      expect(list[1].phones.first.label, 'home');
    });

    test('decodes quoted printable UTF-8 names', () {
      const text = 'BEGIN:VCARD\nFN;CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:Mw=C3=A9\nTEL:0712345678\nEND:VCARD';
      expect(parseVcf(text).single.name, 'Mwé');
    });

    test('joins folded lines and handles tel: values', () {
      const text = 'BEGIN:VCARD\nFN:Long\n  Name\nTEL;VALUE=uri:tel:+254712345678\nEND:VCARD';
      final c = parseVcf(text).single;
      expect(c.name, 'Long Name');
      expect(c.phones.single.number, '+254712345678');
    });

    test('a file name or the content decides which parser runs', () {
      expect(parseContactsFile('people.vcf', vcf).length, 2);
      expect(parseContactsFile('export', vcf).length, 2);
    });
  });

  group('CSV', () {
    test('rows keep quoted commas, quotes and line breaks', () {
      final rows = parseCsvRows('name,phone\r\n"Ann ""A""\nline",0712345678\r\n');
      expect(rows, [
        ['name', 'phone'],
        ['Ann "A"\nline', '0712345678'],
      ]);
    });

    test('reads a Google Contacts export', () {
      const csv = 'First Name,Last Name,Phone 1 - Label,Phone 1 - Value\n'
          'Wanjiku,Kamau,Mobile,0712 345 678\n'
          '"Otieno, Brian",,Home,+254722111222 ::: 0733000111\n'
          'NoPhone,X,,\n';
      final list = parseCsv(csv);
      expect(list.map((c) => c.name), ['Wanjiku Kamau', 'Otieno, Brian']);
      expect(list[1].phones.map((p) => p.number), ['+254722111222', '0733000111']);
    });

    test('reads an Outlook export with a display name and several phone columns', () {
      const csv = 'Display Name,Mobile Phone,Home Phone,Business Phone\nPeter Mwangi,0700111222,,0201234567\n';
      final c = parseCsv(csv).single;
      expect(c.name, 'Peter Mwangi');
      expect(c.phones.map((p) => p.number), ['0700111222', '0201234567']);
    });

    test('a file with only a header gives nothing', () {
      expect(parseCsv('Name,Phone\n'), isEmpty);
    });
  });

  group('Google', () {
    Map<String, dynamic> person(String name, List<Map<String, String>> phones) => {
          'person': {
            'names': [
              {'displayName': name}
            ],
            if (phones.isNotEmpty) 'phoneNumbers': phones,
          }
        };

    test('searches contacts and other contacts, drops people without numbers and repeats', () async {
      final paths = <String>[];
      String? auth;
      final client = MockClient((req) async {
        paths.add('${req.url.path}?${req.url.query}');
        auth = req.headers['Authorization'];
        if (req.url.queryParameters['query'] == '') return http.Response('{}', 200);
        if (req.url.path.endsWith('people:searchContacts')) {
          return http.Response(
              jsonEncode({
                'results': [
                  person('Mama Wanjiku', [
                    {'canonicalForm': '+254712345678', 'type': 'mobile'}
                  ]),
                ]
              }),
              200);
        }
        return http.Response(
            jsonEncode({
              'results': [
                person('Mama Wanjiku', [
                  {'value': '0712 345 678'}
                ]),
                person('Wanjiku Kamau', [
                  {'value': '0733 000 111'}
                ]),
                person('Wanjiku Email Only', []),
              ]
            }),
            200);
      });
      final google = GoogleContactsClient(client: client, tokens: FakeTokens());

      final list = await google.search('wanj');

      expect(auth, 'Bearer g-token');
      expect(list.map((c) => c.name), ['Mama Wanjiku', 'Wanjiku Kamau']);
      expect(list.first.from, 'Google contacts');
      expect(list.last.from, 'Other Google contacts');
      expect(paths.where((p) => p.contains('query=wanj')).length, 2);
      expect(paths.where((p) => p.contains('query=&')).length, 2, reason: 'one warm up call for each list');
    });

    test('warms up only once', () async {
      var empty = 0;
      final client = MockClient((req) async {
        if (req.url.queryParameters['query'] == '') empty++;
        return http.Response('{}', 200);
      });
      final google = GoogleContactsClient(client: client, tokens: FakeTokens());
      await google.search('a');
      await google.search('b');
      expect(empty, 2);
    });

    test('an expired token is forgotten and the customer is told', () async {
      final tokens = FakeTokens();
      final client = MockClient((_) async => http.Response('{}', 401));
      final google = GoogleContactsClient(client: client, tokens: tokens);
      await expectLater(google.search('x'), throwsA(isA<ContactSourceException>()));
      expect(tokens.forgotten, contains('google'));
    });

    test('Google error messages are passed on', () async {
      final client = MockClient((req) async {
        if (req.url.queryParameters['query'] == '') return http.Response('{}', 200);
        return http.Response(jsonEncode({'error': {'message': 'People API has not been used in project 1 before.'}}), 403);
      });
      final google = GoogleContactsClient(client: client, tokens: FakeTokens());
      await expectLater(
        google.search('x'),
        throwsA(isA<ContactSourceException>().having((e) => e.message, 'message', contains('People API has not been used'))),
      );
    });
  });

  group('Microsoft', () {
    test('reads every page once and searches on the phone', () async {
      final urls = <String>[];
      final client = MockClient((req) async {
        urls.add(req.url.toString());
        expect(req.headers['Authorization'], 'Bearer m-token');
        if (req.url.toString() == 'https://graph.microsoft.com/next') {
          return http.Response(
              jsonEncode({
                'value': [
                  {'displayName': 'Wanjiru Home', 'mobilePhone': null, 'homePhones': ['0700000001'], 'businessPhones': []},
                ]
              }),
              200);
        }
        return http.Response(
            jsonEncode({
              '@odata.nextLink': 'https://graph.microsoft.com/next',
              'value': [
                {'displayName': 'Peter Mwangi', 'mobilePhone': '0700111222', 'homePhones': [], 'businessPhones': ['0201234567']},
                {'displayName': 'No Number', 'mobilePhone': null, 'homePhones': [], 'businessPhones': []},
              ]
            }),
            200);
      });
      final ms = MicrosoftContactsClient(client: client, tokens: FakeTokens());

      expect((await ms.search('peter')).single.phones.map((p) => p.number), ['0700111222', '0201234567']);
      expect((await ms.search('wanjiru')).single.from, 'Outlook');
      expect(urls.length, 2);
    });

    test('an expired token is forgotten', () async {
      final tokens = FakeTokens();
      final ms = MicrosoftContactsClient(client: MockClient((_) async => http.Response('', 401)), tokens: tokens);
      await expectLater(ms.search('x'), throwsA(isA<ContactSourceException>()));
      expect(tokens.forgotten, ['microsoft']);
    });
  });

  group('finder', () {
    test('the phone is read once and the list follows the typing', () async {
      final book = FakeAddressBook();
      final f = _finder(book: book);
      await f.search();
      expect(f.results.length, 3);
      f.setQuery('brian');
      expect(f.results.map((c) => c.name), ['Brian Otieno']);
      await f.search();
      expect(book.reads, 1);
    });

    test('refusing the permission explains it and offers the system chooser', () async {
      final f = _finder(book: FakeAddressBook(allowed: false));
      await f.search();
      expect(f.phoneDenied, isTrue);
      expect(f.error, contains('not allowed'));
      expect(f.results, isEmpty);
      expect(f.busy, isFalse);
    });

    test('the system chooser result is passed on', () async {
      final book = FakeAddressBook()..chosen = _people.first;
      final f = _finder(book: book);
      expect((await f.chooseWithSystemPicker())?.name, 'Mama Wanjiku');
    });

    test('changing source clears the old results', () async {
      final f = _finder();
      await f.search();
      f.selectSource(ContactSourceKind.google);
      expect(f.results, isEmpty);
      expect(f.searched, isFalse);
    });

    test('a vCard file is imported and searched', () async {
      final f = _finder(files: FakeFiles((name: 'people.vcf', text: 'BEGIN:VCARD\nFN:Asha\nTEL:0711222333\nEND:VCARD')));
      f.selectSource(ContactSourceKind.file);
      await f.importFile();
      expect(f.fileName, 'people.vcf');
      expect(f.results.single.name, 'Asha');
      f.setQuery('zzz');
      expect(f.results, isEmpty);
    });

    test('a file without numbers says so', () async {
      final f = _finder(files: FakeFiles((name: 'empty.csv', text: 'Name,Phone\nAsha,\n')));
      f.selectSource(ContactSourceKind.file);
      await f.importFile();
      expect(f.error, contains('No contacts'));
    });

    test('Microsoft without a client id explains what is missing', () async {
      final f = _finder(microsoft: false);
      f.selectSource(ContactSourceKind.microsoft);
      await f.search();
      expect(f.error, contains('not set up'));
    });

    test('a Google error shows its message and clears results', () async {
      final f = _finder(client: MockClient((_) async => http.Response('{}', 401)));
      f.selectSource(ContactSourceKind.google);
      await f.search();
      expect(f.error, contains('expired'));
      expect(f.results, isEmpty);
      expect(f.busy, isFalse);
    });
  });
}
