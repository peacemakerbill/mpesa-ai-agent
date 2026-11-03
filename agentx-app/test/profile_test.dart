import 'dart:convert';
import 'dart:typed_data';

import 'package:agentx_app/api/api_client.dart';
import 'package:agentx_app/app_state.dart';
import 'package:agentx_app/models/profile.dart';
import 'package:agentx_app/profile/signup_flow.dart';
import 'package:agentx_app/screens/email_recipients_screen.dart';
import 'package:agentx_app/screens/profile_screen.dart';
import 'package:agentx_app/screens/signup_screen.dart';
import 'package:agentx_app/services/secure_store.dart';
import 'package:agentx_app/theme/app_theme.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

class _Store extends SecureStore {
  String? saved;

  @override
  Future<String?> token() async => saved ?? 'tok';

  @override
  Future<String?> phone() async => '254700000001';

  @override
  Future<String?> theme() async => null;

  @override
  Future<void> setTheme(String name) async {}

  @override
  Future<String> deviceId() async => 'device-1234';

  @override
  Future<void> saveSession({required String token, required String phone, required DateTime expiresAt}) async {
    saved = token;
  }
}

const profileJson = {
  'phone': '254700000001',
  'status': 'ACTIVE',
  'firstName': 'Bill',
  'middleName': '',
  'lastName': 'Graham',
  'fullName': 'Bill Graham',
  'initials': 'BG',
  'email': 'bill@example.com',
  'emailVerified': true,
  'pendingEmail': '',
  'address': 'Moi Avenue',
  'city': 'Nairobi',
  'county': 'Nairobi',
  'country': 'Kenya',
  'latitude': null,
  'longitude': null,
  'hasPicture': false,
  'pictureVersion': 0,
  'notifySignIn': true,
  'notifyPayments': true,
  'complete': true,
  'emailDelivers': true,
};

http.Response _json(Object body, [int status = 200]) =>
    http.Response(jsonEncode(body), status, headers: {'content-type': 'application/json'});

AppState _state(Future<http.Response> Function(http.Request) handler, {List<http.Request>? seen, _Store? store}) {
  final client = MockClient((req) async {
    seen?.add(req);
    return handler(req);
  });
  return AppState(
    store: store ?? _Store(),
    api: ApiClient(baseUrl: 'http://x.test', client: client, tokenProvider: () async => 'tok'),
  );
}

Future<void> _show(WidgetTester tester, Widget screen) async {
  await tester.binding.setSurfaceSize(const Size(800, 2600));
  addTearDown(() => tester.binding.setSurfaceSize(null));
  await tester.pumpWidget(MaterialApp(
    theme: AppTheme.build(ThemeKey.daylight),
    home: Builder(
      builder: (context) => Scaffold(
        body: Center(
          child: TextButton(
            onPressed: () => Navigator.of(context).push<void>(MaterialPageRoute<void>(builder: (_) => screen)),
            child: const Text('open'),
          ),
        ),
      ),
    ),
  ));
  await tester.tap(find.text('open'));
  await tester.pumpAndSettle();
}

Finder _field(String label) => find.widgetWithText(TextField, label);

Finder _inDialog(Type type) => find.descendant(of: find.byType(AlertDialog), matching: find.byType(type));

void main() {
  group('models', () {
    test('a profile is read, and an older answer without the new fields still reads', () {
      final p = Profile.fromJson(profileJson);
      expect(p.fullName, 'Bill Graham');
      expect(p.emailVerified, isTrue);
      expect(p.hasLocation, isFalse);
      expect(p.displayName, 'Bill');

      final bare = Profile.fromJson({'phone': '254700000001'});
      expect(bare.firstName, '');
      expect(bare.displayName, '254700000001');
      expect(bare.notifySignIn, isTrue);
      expect(bare.emailDelivers, isTrue);
      expect(bare.hasEmail, isFalse);
    });

    test('only the fields that were set are sent, and a location needs both numbers', () {
      expect(const ProfileChanges(firstName: 'Billy', notifySignIn: false).toJson(), {'firstName': 'Billy', 'notifySignIn': false});
      expect(const ProfileChanges(latitude: -1.29).toJson(), isEmpty);
      expect(const ProfileChanges(latitude: -1.29, longitude: 36.82).toJson(), {'latitude': -1.29, 'longitude': 36.82});
      expect(const ProfileChanges(clearLocation: true).toJson(), {'clearLocation': true});
      expect(const ProfileChanges(middleName: '').toJson(), {'middleName': ''});
    });

    test('a list import is described in plain words', () {
      final r = RecipientImport.fromJson({'added': 2, 'alreadySaved': 1, 'invalid': 3, 'invalidSamples': ['hello'], 'limitReached': true});
      expect(r.summary, '2 addresses were saved. 1 was already on your list. 3 entries were not email addresses. Your list is full, so some addresses were left out.');
      expect(RecipientImport.fromJson({'added': 0}).summary, 'No new addresses were saved.');
      expect(RecipientImport.fromJson({'added': 1}).summary, '1 address was saved.');
    });

    test('an address is recognised, and the checks are only for typing slips', () {
      expect(looksLikeEmail('bill@example.com'), isTrue);
      expect(looksLikeEmail(' bill@example.co.ke '), isTrue);
      expect(looksLikeEmail('bill@example'), isFalse);
      expect(looksLikeEmail('two words@example.com'), isFalse);
      expect(phoneDigits('0712 345-678'), 10);
    });
  });

  group('api', () {
    test('sign up asks without a token and leaves out what is empty', () async {
      final seen = <http.Request>[];
      final state = _state((r) async => _json({'expiresAt': '2026-11-03T10:00:00Z'}), seen: seen);
      final code = await state.api.signupRequest(phone: '0700000001', firstName: 'Bill', middleName: '', lastName: 'Graham', email: '');
      expect(code.devCode, isNull);
      final req = seen.single;
      expect(req.url.path, '/api/auth/signup/request');
      expect(req.headers.containsKey('Authorization'), isFalse);
      expect(jsonDecode(req.body), {'phone': '0700000001', 'firstName': 'Bill', 'lastName': 'Graham'});
    });

    test('a picture is sent as the raw body with its type', () async {
      final seen = <http.Request>[];
      final state = _state((r) async => _json({...profileJson, 'hasPicture': true, 'pictureVersion': 5}), seen: seen);
      final p = await state.api.uploadProfilePicture(Uint8List.fromList([1, 2, 3]), mimeType: 'image/png');
      expect(p.hasPicture, isTrue);
      expect(seen.single.method, 'PUT');
      expect(seen.single.url.path, '/api/profile/picture');
      expect(seen.single.headers['Content-Type'], startsWith('image/png'));
      expect(seen.single.bodyBytes, [1, 2, 3]);
    });

    test('no picture is null and not an error', () async {
      final state = _state((r) async => _json({'message': 'none'}, 404));
      expect(await state.api.profilePicture(), isNull);
      final broken = _state((r) async => _json({'message': 'down'}, 500));
      expect(broken.api.profilePicture(), throwsA(isA<ApiException>()));
    });
  });

  group('sign up', () {
    testWidgets('missing details are explained before anything is sent', (tester) async {
      final seen = <http.Request>[];
      final state = _state((r) async => _json({'expiresAt': '2026-11-03T10:00:00Z'}), seen: seen);
      await _show(tester, SignupScreen(state: state));
      await tester.tap(find.text('Send me a code'));
      await tester.pump();
      expect(find.text('Enter your first name.'), findsOneWidget);
      await tester.enterText(_field('First name'), 'Bill');
      await tester.tap(find.text('Send me a code'));
      await tester.pump();
      expect(find.text('Enter your last name.'), findsOneWidget);
      expect(seen, isEmpty);
    });

    testWidgets('a bad email is caught, and a good set of details asks for the SMS code', (tester) async {
      final seen = <http.Request>[];
      final state = _state((r) async => _json({'expiresAt': '2026-11-03T10:00:00Z', 'devCode': '123456'}), seen: seen);
      await _show(tester, SignupScreen(state: state));
      await tester.enterText(_field('First name'), 'Bill');
      await tester.enterText(_field('Last name'), 'Graham');
      await tester.enterText(_field('Your phone number'), '0700 000 001');
      await tester.enterText(_field('Email (optional)'), 'not-an-email');
      await tester.tap(find.text('Send me a code'));
      await tester.pump();
      expect(find.textContaining('does not look right'), findsOneWidget);
      expect(seen, isEmpty);

      await tester.enterText(_field('Email (optional)'), 'bill@example.com');
      await tester.tap(find.text('Send me a code'));
      await tester.pumpAndSettle();
      expect(find.text('Create account'), findsOneWidget);
      expect(find.textContaining('shows the code'), findsOneWidget);
      expect(jsonDecode(seen.single.body), containsPair('email', 'bill@example.com'));
    });

    test('a correct code saves the session like a sign in and moves the app to signed in', () async {
      final store = _Store()..saved = null;
      final seen = <http.Request>[];
      final state = _state((r) async {
        if (r.url.path == '/api/auth/signup/verify') {
          return _json({'token': 'new-token', 'phone': '254700000001', 'expiresAt': '2026-12-03T10:00:00Z'});
        }
        return _json({'message': 'not needed'}, 500);
      }, seen: seen, store: store);
      await finishSignup(state, phone: '0700000001', code: '123456');
      expect(store.saved, 'new-token');
      expect(state.linked, isTrue);
      expect(jsonDecode(seen.first.body), containsPair('deviceId', 'device-1234'));
    });
  });

  group('profile screen', () {
    testWidgets('the saved details are shown and the email says it is verified', (tester) async {
      final state = _state((r) async => _json(profileJson));
      await _show(tester, ProfileScreen(state: state));
      expect(find.text('Bill Graham'), findsOneWidget);
      expect(find.text('bill@example.com'), findsOneWidget);
      expect(find.text('Verified'), findsOneWidget);
      expect(tester.widget<TextField>(_field('First name')).controller!.text, 'Bill');
      expect(tester.widget<TextField>(_field('Town or city')).controller!.text, 'Nairobi');
    });

    testWidgets('a problem loading is shown with a way to try again', (tester) async {
      var fail = true;
      final state = _state((r) async => fail ? _json({'message': 'The service had a problem.'}, 500) : _json(profileJson));
      await _show(tester, ProfileScreen(state: state));
      expect(find.text('Could not load your profile'), findsOneWidget);
      fail = false;
      await tester.tap(find.text('Try again'));
      await tester.pumpAndSettle();
      expect(find.text('Bill Graham'), findsOneWidget);
    });

    testWidgets('saving sends the names, address and switches', (tester) async {
      final seen = <http.Request>[];
      final state = _state((r) async => r.method == 'PUT' ? _json({...profileJson, 'firstName': 'Billy'}) : _json(profileJson), seen: seen);
      await _show(tester, ProfileScreen(state: state));
      await tester.enterText(_field('First name'), 'Billy');
      await tester.enterText(_field('Middle name (optional)'), 'Otieno');
      await tester.tap(find.text('Save changes'));
      await tester.pumpAndSettle();
      final put = seen.firstWhere((r) => r.method == 'PUT');
      final body = jsonDecode(put.body) as Map<String, dynamic>;
      expect(put.url.path, '/api/profile');
      expect(body['firstName'], 'Billy');
      expect(body['middleName'], 'Otieno');
      expect(body['lastName'], 'Graham');
      expect(body['city'], 'Nairobi');
      expect(body['notifySignIn'], true);
      expect(body.containsKey('clearLocation'), isFalse);
      expect(find.text('Saved.'), findsOneWidget);
    });

    testWidgets('the first and last name can not be saved empty', (tester) async {
      final seen = <http.Request>[];
      final state = _state((r) async => _json(profileJson), seen: seen);
      await _show(tester, ProfileScreen(state: state));
      await tester.enterText(_field('Last name'), '');
      await tester.tap(find.text('Save changes'));
      await tester.pump();
      expect(find.text('The first and last name can not be empty.'), findsOneWidget);
      expect(seen.where((r) => r.method == 'PUT'), isEmpty);
    });

    testWidgets('a new email needs its code before it counts', (tester) async {
      final seen = <http.Request>[];
      final state = _state((r) async {
        if (r.url.path == '/api/profile/email/verify') {
          return _json({...profileJson, 'email': 'new@example.com'});
        }
        if (r.url.path == '/api/profile/email/request-code') {
          return _json({'expiresAt': '2026-11-03T10:00:00Z'});
        }
        return _json({...profileJson, 'pendingEmail': 'new@example.com'});
      }, seen: seen);
      await _show(tester, ProfileScreen(state: state));
      await tester.tap(find.text('Change email'));
      await tester.pumpAndSettle();
      await tester.enterText(_inDialog(TextField), 'new@example.com');
      await tester.tap(find.text('Send me a code'));
      await tester.pumpAndSettle();
      expect(find.text('Enter the code'), findsOneWidget);
      expect(jsonDecode(seen.firstWhere((r) => r.url.path == '/api/profile/email/request-code').body), {'email': 'new@example.com'});

      await tester.enterText(_inDialog(TextField), '123456');
      await tester.tap(find.text('Verify'));
      await tester.pumpAndSettle();
      expect(jsonDecode(seen.firstWhere((r) => r.url.path == '/api/profile/email/verify').body), {'code': '123456'});
      expect(find.text('Your email is verified.'), findsOneWidget);
      expect(find.text('new@example.com'), findsOneWidget);
    });

    testWidgets('a wrong code shows the reason and keeps the box open', (tester) async {
      final state = _state((r) async {
        if (r.url.path == '/api/profile/email/verify') return _json({'message': 'That code is not right. 4 tries left.'}, 400);
        if (r.url.path == '/api/profile/email/request-code') return _json({'expiresAt': '2026-11-03T10:00:00Z'});
        return _json({...profileJson, 'pendingEmail': 'new@example.com'});
      });
      await _show(tester, ProfileScreen(state: state));
      await tester.tap(find.text('Change email'));
      await tester.pumpAndSettle();
      await tester.enterText(_inDialog(TextField), 'new@example.com');
      await tester.tap(find.text('Send me a code'));
      await tester.pumpAndSettle();
      await tester.enterText(_inDialog(TextField), '000000');
      await tester.tap(find.text('Verify'));
      await tester.pumpAndSettle();
      expect(find.textContaining('not right'), findsOneWidget);
      expect(find.text('Enter the code'), findsOneWidget);
    });

    testWidgets('a server that cannot send email says so', (tester) async {
      final state = _state((r) async => _json({...profileJson, 'emailDelivers': false}));
      await _show(tester, ProfileScreen(state: state));
      expect(find.textContaining('not set up to send email'), findsOneWidget);
    });

    testWidgets('the alert switches are off until the email is verified', (tester) async {
      final state = _state((r) async => _json({...profileJson, 'emailVerified': false}));
      await _show(tester, ProfileScreen(state: state));
      expect(find.text('Not verified'), findsOneWidget);
      expect(find.text('These work once your email is verified.'), findsOneWidget);
      expect(tester.widget<SwitchListTile>(find.widgetWithText(SwitchListTile, 'When I sign in')).onChanged, isNull);
    });
  });

  group('email addresses', () {
    const list = [
      {'id': 'r1', 'email': 'acc@example.com', 'name': 'Accountant'},
    ];

    testWidgets('the saved addresses are listed and one can be removed', (tester) async {
      final seen = <http.Request>[];
      var removed = false;
      final state = _state((r) async {
        if (r.method == 'DELETE') {
          removed = true;
          return http.Response('', 204);
        }
        return _json(removed ? <Object>[] : list);
      }, seen: seen);
      await _show(tester, EmailRecipientsScreen(state: state));
      expect(find.text('Accountant'), findsOneWidget);
      expect(find.text('acc@example.com'), findsOneWidget);
      await tester.tap(find.byTooltip('Remove acc@example.com'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Remove'));
      await tester.pumpAndSettle();
      expect(seen.any((r) => r.method == 'DELETE' && r.url.path == '/api/email/recipients/r1'), isTrue);
      expect(find.textContaining('No addresses yet'), findsOneWidget);
    });

    testWidgets('a pasted list is sent as text and the result is explained', (tester) async {
      final seen = <http.Request>[];
      final state = _state((r) async {
        if (r.url.path.endsWith('/paste')) return _json({'added': 2, 'alreadySaved': 0, 'invalid': 0, 'invalidSamples': <String>[], 'limitReached': false});
        return _json(<Object>[]);
      }, seen: seen);
      await _show(tester, EmailRecipientsScreen(state: state));
      await tester.tap(find.text('Paste a list'));
      await tester.pumpAndSettle();
      await tester.enterText(_inDialog(TextField), 'a@x.com\nb@x.com');
      await tester.tap(find.text('Save addresses'));
      await tester.pumpAndSettle();
      expect(jsonDecode(seen.firstWhere((r) => r.url.path.endsWith('/paste')).body), {'text': 'a@x.com\nb@x.com'});
      expect(find.text('2 addresses were saved.'), findsOneWidget);
    });

    testWidgets('an uploaded file is sent and cancelling the picker sends nothing', (tester) async {
      final seen = <http.Request>[];
      final state = _state((r) async {
        if (r.url.path.endsWith('/import')) return _json({'added': 1, 'alreadySaved': 1, 'invalid': 0, 'invalidSamples': <String>[], 'limitReached': false});
        return _json(<Object>[]);
      }, seen: seen);
      var cancel = true;
      await _show(
        tester,
        EmailRecipientsScreen(
          state: state,
          pick: () async => cancel ? null : (name: 'emails.csv', bytes: Uint8List.fromList(utf8.encode('a@x.com\nb@x.com'))),
        ),
      );
      await tester.tap(find.text('Upload CSV or text'));
      await tester.pumpAndSettle();
      expect(seen.where((r) => r.url.path.endsWith('/import')), isEmpty);

      cancel = false;
      await tester.tap(find.text('Upload CSV or text'));
      await tester.pumpAndSettle();
      expect(seen.where((r) => r.url.path.endsWith('/import')), hasLength(1));
      expect(find.text('1 address was saved. 1 was already on your list.'), findsOneWidget);
    });

    testWidgets('one address is added with its name', (tester) async {
      final seen = <http.Request>[];
      final state = _state((r) async {
        if (r.method == 'POST') return _json({'id': 'r2', 'email': 'boss@example.com', 'name': 'Boss'});
        return _json(<Object>[]);
      }, seen: seen);
      await _show(tester, EmailRecipientsScreen(state: state));
      await tester.tap(find.text('Add one'));
      await tester.pumpAndSettle();
      await tester.enterText(_field('Email address'), 'not-an-address');
      await tester.tap(find.text('Save'));
      await tester.pump();
      expect(find.text('That email address does not look right.'), findsOneWidget);
      await tester.enterText(_field('Email address'), 'boss@example.com');
      await tester.enterText(_field('Name (optional)'), 'Boss');
      await tester.tap(find.text('Save'));
      await tester.pumpAndSettle();
      expect(jsonDecode(seen.firstWhere((r) => r.method == 'POST').body), {'email': 'boss@example.com', 'name': 'Boss'});
    });
  });
}
