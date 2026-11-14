import 'package:agentx_app/contacts/contact_errors.dart';
import 'package:agentx_app/contacts/contact_models.dart';
import 'package:agentx_app/contacts/finder_controller.dart';
import 'package:agentx_app/contacts/sources.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

class _Tokens implements AccessTokens {
  @override
  bool get microsoftConfigured => false;

  @override
  Future<String> google() async => 'g';

  @override
  Future<String> microsoft() async => 'm';

  @override
  Future<void> forget({required bool google}) async {}
}

class _Book implements AddressBook {
  _Book(this.error);

  final Object error;

  @override
  Future<bool> ensurePermission() async => throw error;

  @override
  Future<List<ContactEntry>> all() async => throw error;

  @override
  Future<ContactEntry?> chooseWithSystemPicker() async => throw error;
}

class _Files implements ContactFileReader {
  _Files(this.error);

  final Object error;

  @override
  Future<({String name, String text})?> pick() async => throw error;
}

ContactFinderController _finder(Object error) {
  final tokens = _Tokens();
  final client = MockClient((_) async => http.Response('{}', 200));
  return ContactFinderController(
    addressBook: _Book(error),
    google: GoogleContactsClient(client: client, tokens: tokens),
    microsoft: MicrosoftContactsClient(client: client, tokens: tokens),
    files: _Files(error),
    microsoftConfigured: false,
  );
}

final _missing = MissingPluginException('No implementation found for method requestPermission on channel github.com/QuisApp/flutter_contacts');

void main() {
  group('a computer with no address book', () {
    test('the customer is told what to use instead and sees no code words', () async {
      final f = _finder(_missing);
      await f.search();

      expect(f.error, 'This device has no address book the app can read. Use Google, vCard or CSV, or Type it instead.');
      expect(f.error, isNot(contains('MissingPlugin')));
      expect(f.error, isNot(contains('channel')));
      expect(f.phoneUnavailable, isTrue);
      expect(f.phoneDenied, isFalse);
      expect(f.results, isEmpty);
      expect(f.busy, isFalse);
    });

    test('the phone chooser says the same and the screen stops offering it', () async {
      final f = _finder(_missing);
      final person = await f.chooseWithSystemPicker();

      expect(person, isNull);
      expect(f.error, 'This device has no contact chooser. Use Google, vCard or CSV, or Type it instead.');
      expect(f.phoneUnavailable, isTrue);
    });

    test('a file chooser that is missing says so', () async {
      final f = _finder(_missing);
      await f.importFile();

      expect(f.error, 'This device cannot open a file chooser. Use Type it instead.');
    });
  });

  group('other trouble', () {
    test('a refused or failed read on a phone points to the phone settings', () async {
      final f = _finder(PlatformException(code: 'denied', message: 'permission denied'));
      await f.search();

      expect(f.error, contains('phone settings'));
      expect(f.error, isNot(contains('denied')));
      expect(f.phoneUnavailable, isFalse);
    });

    test('anything else is a plain sentence, with the technical text left out', () async {
      final f = _finder(Exception('SQLiteException: database is locked'));
      await f.search();

      expect(f.error, 'Contacts could not be read. Try again, or use Google, vCard or CSV, or Type it instead.');
      expect(f.error, isNot(contains('SQLite')));
      expect(f.phoneUnavailable, isFalse);
    });

    test('an unreadable file says what to choose', () async {
      final f = _finder(const FormatException('Unexpected character'));
      await f.importFile();

      expect(f.error, 'That file could not be read. Choose a .vcf or .csv file saved from your contacts.');
    });

    test('a message the app wrote itself is kept as it is', () async {
      final f = ContactFinderController(
        addressBook: _Book(StateError('unused')),
        google: GoogleContactsClient(client: MockClient((_) async => http.Response('{}', 200)), tokens: _Tokens()),
        microsoft: MicrosoftContactsClient(client: MockClient((_) async => http.Response('{}', 200)), tokens: _Tokens()),
        files: _Files(const ContactSourceException('That file is larger than 20 MB.')),
        microsoftConfigured: false,
      );
      await f.importFile();

      expect(f.error, 'That file is larger than 20 MB.');
    });

    test('the sign-in messages do not show codes either', () {
      expect(contactProblem(_missing, ContactAction.google), 'Google sign-in is not available on this device. Use vCard or CSV, or Type it instead.');
      expect(contactProblem(Exception('socket closed'), ContactAction.google), 'Google sign-in did not work. Check your internet connection and try again.');
      expect(contactProblem(Exception('x'), ContactAction.microsoft), 'Microsoft sign-in did not work. Check your internet connection and try again.');
    });
  });
}
