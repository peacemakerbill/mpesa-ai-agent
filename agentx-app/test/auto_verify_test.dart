import 'dart:async';
import 'dart:convert';

import 'package:agentx_app/api/api_client.dart';
import 'package:agentx_app/app_state.dart';
import 'package:agentx_app/screens/link_screen.dart';
import 'package:agentx_app/screens/signup_screen.dart';
import 'package:agentx_app/services/secure_store.dart';
import 'package:agentx_app/theme/app_theme.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

import 'support/fake_code_reader.dart';

class _Store extends SecureStore {
  @override
  Future<String?> token() async => null;

  @override
  Future<String?> phone() async => null;

  @override
  Future<String?> theme() async => null;

  @override
  Future<void> setTheme(String name) async {}

  @override
  Future<String> deviceId() async => 'device-1234';

  @override
  Future<void> saveSession({required String token, required String phone, required DateTime expiresAt}) async {}
}

http.Response _json(Object body, [int status = 200]) =>
    http.Response(jsonEncode(body), status, headers: {'content-type': 'application/json'});

const _token = {'token': 'new-token', 'phone': '254700000001', 'expiresAt': '2026-12-03T10:00:00Z'};

AppState _state(Future<http.Response> Function(http.Request) handler, List<http.Request> seen, FakeCodeReader reader) {
  final client = MockClient((req) async {
    seen.add(req);
    return handler(req);
  });
  return AppState(
    store: _Store(),
    api: ApiClient(baseUrl: 'http://x.test', client: client),
    codeReader: reader,
  );
}

Future<void> _open(WidgetTester tester, Widget screen) async {
  await tester.binding.setSurfaceSize(const Size(800, 2600));
  addTearDown(() => tester.binding.setSurfaceSize(null));
  await tester.pumpWidget(MaterialApp(theme: AppTheme.build(ThemeKey.daylight), home: screen));
  await tester.pumpAndSettle();
}

Finder _field(String label) => find.widgetWithText(TextField, label);

Iterable<http.Request> _verifies(List<http.Request> seen) => seen.where((r) => r.url.path == '/api/auth/verify');

void main() {
  group('signing in', () {
    testWidgets('the app listens for the text message and sends its hash with the request', (tester) async {
      final seen = <http.Request>[];
      final reader = FakeCodeReader();
      final state = _state((r) async => _json({'expiresAt': '2026-11-03T10:00:00Z', 'smsSent': true}), seen, reader);
      await _open(tester, LinkScreen(state: state));

      await tester.enterText(_field('Your phone number'), '0700 000 001');
      await tester.tap(find.text('Send me a code'));
      await tester.pumpAndSettle();

      expect(reader.listens, 1);
      expect(jsonDecode(seen.single.body), containsPair('appHash', 'FA+9qCX9VSu'));
    });

    testWidgets('a phone that cannot read messages sends no hash', (tester) async {
      final seen = <http.Request>[];
      final state = _state((r) async => _json({'expiresAt': '2026-11-03T10:00:00Z', 'smsSent': true}), seen, FakeCodeReader(isSupported: false));
      await _open(tester, LinkScreen(state: state));

      await tester.enterText(_field('Your phone number'), '0700 000 001');
      await tester.tap(find.text('Send me a code'));
      await tester.pumpAndSettle();

      expect(jsonDecode(seen.single.body), {'phone': '0700 000 001'});
    });

    testWidgets('the code from the text message is filled in and the sign in is sent without a tap', (tester) async {
      final seen = <http.Request>[];
      final reader = FakeCodeReader();
      final state = _state((r) async {
        if (r.url.path == '/api/auth/verify') return _json(_token);
        if (r.url.path == '/api/auth/request-code') return _json({'expiresAt': '2026-11-03T10:00:00Z', 'smsSent': true});
        return _json({'message': 'not needed'}, 500);
      }, seen, reader);
      await _open(tester, LinkScreen(state: state));
      await tester.enterText(_field('Your phone number'), '0700 000 001');
      await tester.tap(find.text('Send me a code'));
      await tester.pumpAndSettle();
      expect(_verifies(seen), isEmpty);

      reader.arrives('482913');
      await tester.pumpAndSettle();

      expect(_verifies(seen), hasLength(1));
      expect(jsonDecode(_verifies(seen).single.body), containsPair('code', '482913'));
      expect(state.linked, isTrue);
    });

    testWidgets('a code that arrives before the answer to the request is kept and sent', (tester) async {
      final seen = <http.Request>[];
      final reader = FakeCodeReader();
      final gate = Completer<void>();
      final state = _state((r) async {
        if (r.url.path == '/api/auth/request-code') {
          await gate.future;
          return _json({'expiresAt': '2026-11-03T10:00:00Z', 'smsSent': true});
        }
        if (r.url.path == '/api/auth/verify') return _json(_token);
        return _json({'message': 'not needed'}, 500);
      }, seen, reader);
      await _open(tester, LinkScreen(state: state));
      await tester.enterText(_field('Your phone number'), '0700 000 001');
      await tester.tap(find.text('Send me a code'));
      await tester.pump();

      reader.arrives('555666');
      gate.complete();
      await tester.pumpAndSettle();

      expect(jsonDecode(_verifies(seen).single.body), containsPair('code', '555666'));
    });

    testWidgets('a code the server shows is filled in and sent after a moment', (tester) async {
      final seen = <http.Request>[];
      final state = _state((r) async {
        if (r.url.path == '/api/auth/verify') return _json(_token);
        if (r.url.path == '/api/auth/request-code') return _json({'expiresAt': '2026-11-03T10:00:00Z', 'devCode': '123456', 'smsSent': false});
        return _json({'message': 'not needed'}, 500);
      }, seen, FakeCodeReader(isSupported: false));
      await _open(tester, LinkScreen(state: state));
      await tester.enterText(_field('Your phone number'), '0700 000 001');
      await tester.tap(find.text('Send me a code'));
      await tester.pumpAndSettle();

      // The person sees the code first
      expect(find.textContaining('shows the code'), findsOneWidget);
      expect(find.textContaining('123456'), findsWidgets);
      expect(_verifies(seen), isEmpty);

      await tester.pump(const Duration(milliseconds: 1500));
      await tester.pumpAndSettle();

      expect(jsonDecode(_verifies(seen).single.body), containsPair('code', '123456'));
      expect(state.linked, isTrue);
    });

    testWidgets('the sixth digit typed, pasted or suggested by the keyboard sends the code', (tester) async {
      final seen = <http.Request>[];
      final state = _state((r) async {
        if (r.url.path == '/api/auth/verify') return _json(_token);
        if (r.url.path == '/api/auth/request-code') return _json({'expiresAt': '2026-11-03T10:00:00Z'});
        return _json({'message': 'not needed'}, 500);
      }, seen, FakeCodeReader(isSupported: false));
      await _open(tester, LinkScreen(state: state));
      await tester.enterText(_field('Your phone number'), '0700 000 001');
      await tester.tap(find.text('Send me a code'));
      await tester.pumpAndSettle();

      await tester.enterText(_field('6 digit code'), '12345');
      await tester.pumpAndSettle();
      expect(_verifies(seen), isEmpty);

      await tester.enterText(_field('6 digit code'), '123456');
      await tester.pumpAndSettle();
      expect(jsonDecode(_verifies(seen).single.body), containsPair('code', '123456'));
    });

    testWidgets('a wrong shown code is not tried again by itself', (tester) async {
      final seen = <http.Request>[];
      final state = _state((r) async {
        if (r.url.path == '/api/auth/verify') return _json({'message': 'That code is not valid or has expired. Ask for a new one.'}, 401);
        return _json({'expiresAt': '2026-11-03T10:00:00Z', 'devCode': '123456'});
      }, seen, FakeCodeReader(isSupported: false));
      await _open(tester, LinkScreen(state: state));
      await tester.enterText(_field('Your phone number'), '0700 000 001');
      await tester.tap(find.text('Send me a code'));
      await tester.pumpAndSettle();

      await tester.pump(const Duration(milliseconds: 1500));
      await tester.pumpAndSettle();
      await tester.pump(const Duration(seconds: 5));
      await tester.pumpAndSettle();

      expect(_verifies(seen), hasLength(1));
      expect(find.textContaining('not valid or has expired'), findsOneWidget);
    });

    testWidgets('leaving the screen stops the listening', (tester) async {
      final seen = <http.Request>[];
      final reader = FakeCodeReader();
      final state = _state((r) async => _json({'expiresAt': '2026-11-03T10:00:00Z', 'smsSent': true}), seen, reader);
      await _open(tester, LinkScreen(state: state));
      await tester.enterText(_field('Your phone number'), '0700 000 001');
      await tester.tap(find.text('Send me a code'));
      await tester.pumpAndSettle();

      await tester.pumpWidget(const SizedBox());

      expect(reader.stops, greaterThanOrEqualTo(1));
    });
  });

  group('signing up', () {
    Future<void> toContactStep(WidgetTester tester) async {
      await tester.enterText(_field('First name'), 'Bill');
      await tester.enterText(_field('Last name'), 'Graham');
      await tester.tap(find.text('Continue'));
      await tester.pumpAndSettle();
    }

    testWidgets('the hash goes with the request and the code from the text message is sent by itself', (tester) async {
      final seen = <http.Request>[];
      final reader = FakeCodeReader();
      final state = _state((r) async {
        if (r.url.path == '/api/auth/signup/verify') return _json(_token);
        if (r.url.path == '/api/auth/signup/request') return _json({'expiresAt': '2026-11-03T10:00:00Z', 'smsSent': true});
        return _json({'message': 'not needed'}, 500);
      }, seen, reader);
      await _open(tester, SignupScreen(state: state));
      await toContactStep(tester);
      await tester.enterText(_field('Your phone number'), '0700 000 001');
      await tester.tap(find.text('Send me a code'));
      await tester.pumpAndSettle();
      expect(jsonDecode(seen.first.body), containsPair('appHash', 'FA+9qCX9VSu'));

      reader.arrives('482913');
      await tester.pumpAndSettle();

      final verify = seen.where((r) => r.url.path == '/api/auth/signup/verify');
      expect(verify, hasLength(1));
      expect(jsonDecode(verify.single.body), containsPair('code', '482913'));
    });

    testWidgets('a code the server shows is filled in and sent after a moment', (tester) async {
      final seen = <http.Request>[];
      final state = _state((r) async {
        if (r.url.path == '/api/auth/signup/verify') return _json(_token);
        if (r.url.path == '/api/auth/signup/request') return _json({'expiresAt': '2026-11-03T10:00:00Z', 'devCode': '123456'});
        return _json({'message': 'not needed'}, 500);
      }, seen, FakeCodeReader(isSupported: false));
      await _open(tester, SignupScreen(state: state));
      await toContactStep(tester);
      await tester.enterText(_field('Your phone number'), '0700 000 001');
      await tester.tap(find.text('Send me a code'));
      await tester.pumpAndSettle();
      expect(find.textContaining('shows the code'), findsOneWidget);
      expect(seen.where((r) => r.url.path == '/api/auth/signup/verify'), isEmpty);

      await tester.pump(const Duration(milliseconds: 1500));
      await tester.pumpAndSettle();

      final verify = seen.where((r) => r.url.path == '/api/auth/signup/verify');
      expect(jsonDecode(verify.single.body), containsPair('code', '123456'));
    });
  });
}
