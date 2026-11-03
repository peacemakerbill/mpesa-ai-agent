import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:agentx_app/api/api_client.dart';
import 'package:agentx_app/app_state.dart';
import 'package:agentx_app/models/business_account.dart';
import 'package:agentx_app/screens/business_account_form_screen.dart';
import 'package:agentx_app/screens/business_accounts_screen.dart';
import 'package:agentx_app/screens/csv_import_screen.dart';
import 'package:agentx_app/services/secure_store.dart';
import 'package:agentx_app/theme/app_theme.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

class _Store extends SecureStore {
  @override
  Future<String?> token() async => 'tok';

  @override
  Future<String?> phone() async => '254700000001';

  @override
  Future<String?> theme() async => null;

  @override
  Future<void> setTheme(String name) async {}
}

const headJson = {
  'id': '11111111-1111-1111-1111-111111111111',
  'label': 'Main shop',
  'environment': 'SANDBOX',
  'shortcodeType': 'BUY_GOODS',
  'shortcode': '174379',
  'tillNumber': '',
  'accountReference': '',
  'parentId': null,
  'inheritsCredentials': false,
  'consumerKeyHint': '1234',
  'hasConsumerSecret': true,
  'initiatorName': 'testapi',
  'hasInitiatorPassword': true,
  'hasPasskey': false,
  'capabilities': [
    {'code': 'STK_PUSH', 'label': 'STK Push', 'ready': false, 'needs': 'STK passkey'},
    {'code': 'DYNAMIC_QR', 'label': 'Dynamic QR', 'ready': true, 'needs': 'consumer key and secret'},
    {'code': 'B2C', 'label': 'Pay out to phones (B2C)', 'ready': true, 'needs': 'initiator name and password'},
  ],
  'verifiedAt': null,
  'createdAt': '2026-11-03T06:00:00Z',
};

const branchJson = {
  'id': '22222222-2222-2222-2222-222222222222',
  'label': 'Till 1',
  'environment': 'SANDBOX',
  'shortcodeType': 'BUY_GOODS',
  'shortcode': '174379',
  'tillNumber': '123456',
  'accountReference': '',
  'parentId': '11111111-1111-1111-1111-111111111111',
  'inheritsCredentials': true,
  'consumerKeyHint': '1234',
  'hasConsumerSecret': true,
  'initiatorName': 'testapi',
  'hasInitiatorPassword': true,
  'hasPasskey': false,
  'capabilities': [],
  'verifiedAt': null,
  'createdAt': '2026-11-03T06:01:00Z',
};

http.Response _json(Object body, [int status = 200]) =>
    http.Response(jsonEncode(body), status, headers: {'content-type': 'application/json'});

AppState _state(Future<http.Response> Function(http.Request) handler, [List<http.Request>? seen]) {
  final client = MockClient((req) async {
    seen?.add(req);
    return handler(req);
  });
  return AppState(store: _Store(), api: ApiClient(baseUrl: 'http://x.test', client: client, tokenProvider: () async => 'tok'));
}

/// Opens [screen] on top of a first page, so a screen that closes itself has somewhere to go.
Future<void> _openOver(WidgetTester tester, Widget screen) async {
  await tester.binding.setSurfaceSize(const Size(800, 2600));
  addTearDown(() => tester.binding.setSurfaceSize(null));
  await tester.pumpWidget(MaterialApp(
    theme: AppTheme.build(ThemeKey.daylight),
    home: Builder(
      builder: (context) => Scaffold(
        body: Center(
          child: TextButton(
            onPressed: () => Navigator.of(context).push<bool>(MaterialPageRoute<bool>(builder: (_) => screen)),
            child: const Text('open'),
          ),
        ),
      ),
    ),
  ));
  await tester.tap(find.text('open'));
  await tester.pumpAndSettle();
}

Finder _field(String label) => find.widgetWithText(TextFormField, label);

void main() {
  group('models', () {
    test('an account is read with its capabilities and a readable headline', () {
      final head = BusinessAccount.fromJson(headJson);
      expect(head.isBranch, isFalse);
      expect(head.environment, DarajaEnv.sandbox);
      expect(head.kind, ShortcodeKind.buyGoods);
      expect(head.hasKey, isTrue);
      expect(head.readyCount, 2);
      expect(head.capabilities, hasLength(3));
      expect(head.headline, 'Buy Goods 174379');

      final branch = BusinessAccount.fromJson(branchJson);
      expect(branch.isBranch, isTrue);
      expect(branch.headline, 'Till 123456, shortcode 174379');
      expect(branch.payTo, '123456');
    });

    test('a store number with its own shortcode and credentials is read with where each group comes from', () {
      final store = BusinessAccount.fromJson({
        ...branchJson,
        'shortcode': '600111',
        'tillNumber': '5551234',
        'ownShortcode': true,
        'headShortcode': '174379',
        'inheritsCredentials': false,
        'credentialSources': {'app': 'OWN', 'passkey': 'OWN', 'initiator': 'HEAD'},
      });
      expect(store.ownShortcode, isTrue);
      expect(store.headShortcode, '174379');
      expect(store.holdsOwnCredentials, isTrue);
      expect(store.sources.initiator, 'HEAD');
      expect(store.headline, 'Store 600111, till 5551234');
      expect(store.payTo, '5551234');
    });

    test('older answers without the new fields still read: a head owns what it has and a child uses its head office', () {
      final head = BusinessAccount.fromJson(headJson);
      expect(head.ownShortcode, isFalse);
      expect(head.sources.app, 'OWN');
      expect(head.sources.passkey, 'NONE');
      final branch = BusinessAccount.fromJson(branchJson);
      expect(branch.sources.app, 'HEAD');
      expect(branch.holdsOwnCredentials, isFalse);
    });

    test('a paybill child shortcode has no till in its headline', () {
      final child = BusinessAccount.fromJson({...branchJson, 'shortcodeType': 'PAYBILL', 'shortcode': '400300', 'tillNumber': '', 'ownShortcode': true, 'headShortcode': '400200'});
      expect(child.headline, 'Paybill 400300');
      expect(child.payTo, '400300');
    });

    test('a paybill headline names the account reference of a branch', () {
      final account = BusinessAccount.fromJson({...branchJson, 'shortcodeType': 'PAYBILL', 'tillNumber': '', 'accountReference': 'NAIROBI'});
      expect(account.headline, 'Paybill 174379, account NAIROBI');
      expect(account.payTo, '174379');
    });

    test('branches are listed under their head', () {
      final other = {...headJson, 'id': '33333333-3333-3333-3333-333333333333', 'label': 'School fees', 'shortcode': '400200'};
      final groups = groupAccounts([
        BusinessAccount.fromJson(headJson),
        BusinessAccount.fromJson(other),
        BusinessAccount.fromJson(branchJson),
      ]);
      expect(groups, hasLength(2));
      expect(groups[0].head.label, 'Main shop');
      expect(groups[0].branches.map((b) => b.label), ['Till 1']);
      expect(groups[1].branches, isEmpty);
    });

    test('an import result keeps its rows, issues and ignored columns', () {
      final r = ImportResult.fromJson({
        'dryRun': true,
        'saved': false,
        'total': 2,
        'created': 1,
        'updated': 1,
        'rows': [
          {'row': 2, 'label': 'Main shop', 'environment': 'sandbox', 'shortcodeType': 'buy_goods', 'shortcode': '174379', 'tillNumber': '', 'accountReference': '', 'parent': '', 'action': 'create'},
        ],
        'issues': [
          {'row': 3, 'field': 'till_number', 'message': 'A Buy Goods branch needs its till number.'},
          {'row': 0, 'field': 'file', 'message': 'The file is empty.'},
        ],
        'ignoredColumns': ['colour'],
      });
      expect(r.clean, isFalse);
      expect(r.rows.single.action, 'create');
      expect(r.issues[0].where, 'Row 3');
      expect(r.issues[1].where, 'File');
      expect(r.ignoredColumns, ['colour']);
    });
  });

  group('api', () {
    test('the list, create, change and remove calls use the right paths and bodies', () async {
      final seen = <http.Request>[];
      final state = _state((req) async {
        if (req.method == 'GET') return _json([headJson, branchJson]);
        if (req.method == 'DELETE') return _json({'removed': 2});
        return _json(headJson, req.method == 'POST' ? 201 : 200);
      }, seen);

      final list = await state.api.businessAccounts();
      expect(list, hasLength(2));
      await state.api.saveBusinessAccount({'label': 'A'});
      await state.api.saveBusinessAccount({'label': 'B'}, id: 'abc');
      final removed = await state.api.deleteBusinessAccount('abc');

      expect(removed, 2);
      expect(seen.map((r) => '${r.method} ${r.url.path}'), [
        'GET /api/business-accounts',
        'POST /api/business-accounts',
        'PUT /api/business-accounts/abc',
        'DELETE /api/business-accounts/abc',
      ]);
      expect(seen.every((r) => r.headers['Authorization'] == 'Bearer tok'), isTrue);
      expect(jsonDecode(seen[1].body)['label'], 'A');
    });

    test('a CSV file goes up as a multipart form with the dry run flag', () async {
      late http.Request seen;
      final state = _state((req) async {
        seen = req;
        return _json({'dryRun': true, 'saved': false, 'total': 1, 'created': 1, 'updated': 0, 'rows': [], 'issues': [], 'ignoredColumns': []});
      });

      final result = await state.api.importBusinessCsv(Uint8List.fromList(utf8.encode('label,shortcode\nMain,174379\n')), fileName: 'my accounts.csv', dryRun: true);

      expect(result.clean, isTrue);
      expect(seen.url.path, '/api/business-accounts/import');
      expect(seen.url.queryParameters['dryRun'], 'true');
      expect(seen.headers['Authorization'], 'Bearer tok');
      expect(seen.headers['content-type'], startsWith('multipart/form-data'));
      expect(seen.body, contains('name="file"'));
      expect(seen.body, contains('filename="my accounts.csv"'));
      expect(seen.body, contains('Main,174379'));
    });

    test('a file the server says is too large gets a plain message', () async {
      final state = _state((req) async => http.Response('', 413));
      expect(
        () => state.api.importBusinessCsv(Uint8List(3), fileName: 'a.csv', dryRun: true),
        throwsA(isA<ApiException>().having((e) => e.message, 'message', 'That file is too large.')),
      );
    });

    test('a server that is not set up shows its own message', () async {
      final state = _state((req) async => _json({'message': 'Business accounts are switched off on this server.'}, 503));
      expect(
        () => state.api.businessAccounts(),
        throwsA(isA<ApiException>().having((e) => e.message, 'message', 'Business accounts are switched off on this server.')),
      );
    });
  });

  group('form', () {
    testWidgets('an empty head account asks for the name and the shortcode, and the credentials can wait', (tester) async {
      final seen = <http.Request>[];
      final state = _state((req) async => _json(headJson, 201), seen);
      await _openOver(tester, BusinessAccountFormScreen(state: state));

      await tester.tap(find.text('Save account'));
      await tester.pumpAndSettle();

      expect(find.text('Give the account a name.'), findsOneWidget);
      expect(find.text('Enter 5 to 7 digits.'), findsOneWidget);
      expect(find.text('This is required.'), findsNothing);
      expect(seen, isEmpty);
    });

    testWidgets('a head account is sent with only what was typed, then the form closes', (tester) async {
      final seen = <http.Request>[];
      final state = _state((req) async => _json(headJson, 201), seen);
      await _openOver(tester, BusinessAccountFormScreen(state: state));

      await tester.enterText(_field('Name'), 'Main shop');
      await tester.enterText(_field('Shortcode (head office)'), '174379');
      await tester.enterText(_field('Consumer key'), 'abcdefgh1234');
      await tester.enterText(_field('Consumer secret'), 'ABCDEFGH5678');
      await tester.tap(find.text('Save account'));
      await tester.pumpAndSettle();

      expect(seen, hasLength(1));
      final body = jsonDecode(seen.single.body) as Map<String, dynamic>;
      expect(seen.single.method, 'POST');
      expect(body['label'], 'Main shop');
      expect(body['environment'], 'SANDBOX');
      expect(body['shortcodeType'], 'BUY_GOODS');
      expect(body['shortcode'], '174379');
      expect(body['consumerKey'], 'abcdefgh1234');
      expect(body['consumerSecret'], 'ABCDEFGH5678');
      expect(body.containsKey('passkey'), isFalse);
      expect(body.containsKey('initiatorPassword'), isFalse);
      expect(body.containsKey('parentId'), isFalse);
      expect(find.byType(BusinessAccountFormScreen), findsNothing);
    });

    testWidgets('a placeholder from the example file is refused before it is sent', (tester) async {
      final seen = <http.Request>[];
      final state = _state((req) async => _json(headJson, 201), seen);
      await _openOver(tester, BusinessAccountFormScreen(state: state));

      await tester.enterText(_field('Name'), 'Main shop');
      await tester.enterText(_field('Shortcode (head office)'), '174379');
      await tester.enterText(_field('Consumer key'), 'PASTE_CONSUMER_KEY');
      await tester.enterText(_field('Consumer secret'), 'ABCDEFGH5678');
      await tester.tap(find.text('Save account'));
      await tester.pumpAndSettle();

      expect(find.text('Replace the placeholder with the real value.'), findsOneWidget);
      expect(seen, isEmpty);
    });

    testWidgets('a branch asks only for a till and names its head', (tester) async {
      final seen = <http.Request>[];
      final state = _state((req) async => _json(branchJson, 201), seen);
      final head = BusinessAccount.fromJson(headJson);
      await _openOver(tester, BusinessAccountFormScreen(state: state, parent: head));

      expect(find.text('Add a branch'), findsOneWidget);
      expect(_field('Till number'), findsOneWidget);
      expect(_field('Consumer key'), findsNothing);
      expect(find.textContaining('shares the shortcode and credentials of "Main shop"'), findsOneWidget);

      await tester.enterText(_field('Name'), 'Till 1');
      await tester.enterText(_field('Till number'), '123456');
      await tester.tap(find.text('Save account'));
      await tester.pumpAndSettle();

      final body = jsonDecode(seen.single.body) as Map<String, dynamic>;
      expect(body['parentId'], headJson['id']);
      expect(body['tillNumber'], '123456');
      expect(body.containsKey('consumerKey'), isFalse);
      // the head office's own number is sent, which keeps a till on the shared shortcode
      expect(body['shortcode'], '174379');
      expect(body.containsKey('credentials'), isFalse);
    });

    testWidgets('a store number asks for its own shortcode, the linked till and its own credentials', (tester) async {
      final seen = <http.Request>[];
      final state = _state((req) async => _json(branchJson, 201), seen);
      final head = BusinessAccount.fromJson(headJson);
      await _openOver(tester, BusinessAccountFormScreen(state: state, parent: head, startWithOwnShortcode: true));

      expect(find.text('Add a child shortcode'), findsOneWidget);
      expect(_field('Store number (child shortcode)'), findsOneWidget);
      expect(_field('Till number linked to this store'), findsOneWidget);
      expect(_field('Consumer key'), findsOneWidget);
      expect(_field('STK Push passkey'), findsOneWidget);

      await tester.enterText(_field('Name'), 'Mombasa store');
      await tester.enterText(_field('Store number (child shortcode)'), '600111');
      await tester.enterText(_field('Till number linked to this store'), '5551234');
      await tester.enterText(_field('Consumer key'), 'storekey12345');
      await tester.enterText(_field('Consumer secret'), 'storesecret12345');
      await tester.enterText(_field('STK Push passkey'), 'storepasskeystorepasskey1');
      await tester.tap(find.text('Save account'));
      await tester.pumpAndSettle();

      final body = jsonDecode(seen.single.body) as Map<String, dynamic>;
      expect(body['parentId'], headJson['id']);
      expect(body['shortcode'], '600111');
      expect(body['tillNumber'], '5551234');
      expect(body['credentials'], 'own');
      expect(body['consumerKey'], 'storekey12345');
      expect(body['consumerSecret'], 'storesecret12345');
      expect(body['passkey'], 'storepasskeystorepasskey1');
      expect(body.containsKey('environment'), isFalse);
    });

    testWidgets('a store number can use the head office credentials and then sends none of its own', (tester) async {
      final seen = <http.Request>[];
      final state = _state((req) async => _json(branchJson, 201), seen);
      final head = BusinessAccount.fromJson(headJson);
      await _openOver(tester, BusinessAccountFormScreen(state: state, parent: head, startWithOwnShortcode: true));

      await tester.tap(find.text('The head office\'s'));
      await tester.pumpAndSettle();
      expect(_field('Consumer key'), findsNothing);

      await tester.enterText(_field('Name'), 'Kisumu store');
      await tester.enterText(_field('Store number (child shortcode)'), '600222');
      await tester.enterText(_field('Till number linked to this store'), '5552345');
      await tester.tap(find.text('Save account'));
      await tester.pumpAndSettle();

      final body = jsonDecode(seen.single.body) as Map<String, dynamic>;
      expect(body['shortcode'], '600222');
      expect(body['credentials'], 'head');
      expect(body.containsKey('consumerKey'), isFalse);
      expect(body.containsKey('passkey'), isFalse);
    });

    testWidgets('choosing its own credentials and typing none is refused before it is sent', (tester) async {
      final seen = <http.Request>[];
      final state = _state((req) async => _json(branchJson, 201), seen);
      final head = BusinessAccount.fromJson(headJson);
      await _openOver(tester, BusinessAccountFormScreen(state: state, parent: head, startWithOwnShortcode: true));

      await tester.enterText(_field('Name'), 'Mombasa store');
      await tester.enterText(_field('Store number (child shortcode)'), '600111');
      await tester.enterText(_field('Till number linked to this store'), '5551234');
      await tester.tap(find.text('Save account'));
      await tester.pumpAndSettle();

      expect(find.textContaining('Add the credentials from this shortcode'), findsOneWidget);
      expect(seen, isEmpty);
    });

    testWidgets('a child of a paybill has its own shortcode, no till and an optional account reference', (tester) async {
      final seen = <http.Request>[];
      final state = _state((req) async => _json(branchJson, 201), seen);
      final head = BusinessAccount.fromJson({...headJson, 'shortcodeType': 'PAYBILL', 'shortcode': '400200'});
      await _openOver(tester, BusinessAccountFormScreen(state: state, parent: head, startWithOwnShortcode: true));

      expect(_field('Child shortcode'), findsOneWidget);
      expect(_field('Till number linked to this store'), findsNothing);
      expect(_field('Till number'), findsNothing);
      expect(_field('Account reference (optional)'), findsOneWidget);

      await tester.tap(find.text('The head office\'s'));
      await tester.pumpAndSettle();
      await tester.enterText(_field('Name'), 'Kisumu campus');
      await tester.enterText(_field('Child shortcode'), '400300');
      await tester.tap(find.text('Save account'));
      await tester.pumpAndSettle();

      final body = jsonDecode(seen.single.body) as Map<String, dynamic>;
      expect(body['shortcode'], '400300');
      expect(body['credentials'], 'head');
      expect(body.containsKey('tillNumber'), isFalse);
    });

    testWidgets('a paybill branch asks for an account reference', (tester) async {
      final state = _state((req) async => _json(branchJson, 201));
      final head = BusinessAccount.fromJson({...headJson, 'shortcodeType': 'PAYBILL', 'shortcode': '400200'});
      await _openOver(tester, BusinessAccountFormScreen(state: state, parent: head));

      expect(_field('Account reference'), findsOneWidget);
      expect(_field('Till number'), findsNothing);
    });

    testWidgets('changing an account keeps saved secrets unless new ones are typed', (tester) async {
      final seen = <http.Request>[];
      final state = _state((req) async => _json(headJson), seen);
      await _openOver(tester, BusinessAccountFormScreen(state: state, account: BusinessAccount.fromJson(headJson)));

      expect(find.text('Saved, ends in 1234. Leave empty to keep it.'), findsOneWidget);
      expect(find.text('What this account can do'), findsOneWidget);
      expect(find.text('STK Push. Needs the STK passkey.'), findsOneWidget);

      await tester.tap(find.text('Save changes'));
      await tester.pumpAndSettle();

      expect(seen.single.method, 'PUT');
      expect(seen.single.url.path, '/api/business-accounts/${headJson['id']}');
      final body = jsonDecode(seen.single.body) as Map<String, dynamic>;
      expect(body.containsKey('consumerKey'), isFalse);
      expect(body.containsKey('consumerSecret'), isFalse);
      expect(body['initiatorName'], 'testapi');
    });

    testWidgets('a refusal from the server is shown and the form stays open', (tester) async {
      final state = _state((req) async => _json({'message': 'This shortcode is already saved as "Main shop". Edit that account instead.'}, 400));
      await _openOver(tester, BusinessAccountFormScreen(state: state));

      await tester.enterText(_field('Name'), 'Again');
      await tester.enterText(_field('Shortcode (head office)'), '174379');
      await tester.enterText(_field('Consumer key'), 'abcdefgh1234');
      await tester.enterText(_field('Consumer secret'), 'ABCDEFGH5678');
      await tester.tap(find.text('Save account'));
      await tester.pumpAndSettle();

      expect(find.textContaining('already saved as "Main shop"'), findsOneWidget);
      expect(find.byType(BusinessAccountFormScreen), findsOneWidget);
    });

    testWidgets('checking with Safaricom shows both answers', (tester) async {
      final state = _state((req) async => _json({
            'ok': true,
            'message': 'Safaricom accepted the key and secret.',
            'securityCredentialReady': false,
            'securityCredentialMessage': 'No certificate is installed for sandbox.',
          }));
      await _openOver(tester, BusinessAccountFormScreen(state: state, account: BusinessAccount.fromJson(headJson)));

      await tester.tap(find.text('Check with Safaricom'));
      await tester.pumpAndSettle();

      expect(find.text('Safaricom accepted the key and secret.'), findsOneWidget);
      expect(find.text('No certificate is installed for sandbox.'), findsOneWidget);
    });
  });

  group('list', () {
    testWidgets('heads and their tills are shown together', (tester) async {
      final state = _state((req) async => _json([headJson, branchJson]));
      await _openOver(tester, BusinessAccountsScreen(state: state));

      expect(find.text('Main shop'), findsOneWidget);
      expect(find.text('Till 1'), findsOneWidget);
      expect(find.text('Add a till'), findsOneWidget);
      expect(find.text('Add a store number'), findsOneWidget);
      expect(find.text('2 of 3 ready'), findsOneWidget);
    });

    testWidgets('an empty list invites the first account or a CSV file', (tester) async {
      final state = _state((req) async => _json(<Object>[]));
      await _openOver(tester, BusinessAccountsScreen(state: state));

      expect(find.text('No business accounts yet'), findsOneWidget);
      expect(find.text('Add an account'), findsOneWidget);
      expect(find.text('Upload a CSV file'), findsWidgets);
    });

    testWidgets('a server that is not set up is explained with a way to retry', (tester) async {
      final state = _state((req) async => _json({'message': 'Business accounts are not set up on this server yet.'}, 503));
      await _openOver(tester, BusinessAccountsScreen(state: state));

      expect(find.text('Business accounts are not set up on this server yet.'), findsOneWidget);
      expect(find.text('Try again'), findsOneWidget);
    });
  });

  group('csv import', () {
    Map<String, dynamic> result({required bool dryRun, bool saved = false, List<Map<String, Object>> issues = const []}) => {
          'dryRun': dryRun,
          'saved': saved,
          'total': 2,
          'created': 2,
          'updated': 0,
          'rows': [
            {'row': 2, 'label': 'Main shop', 'environment': 'sandbox', 'shortcodeType': 'buy_goods', 'shortcode': '174379', 'tillNumber': '', 'accountReference': '', 'parent': '', 'action': 'create'},
            {'row': 3, 'label': 'Till 1', 'environment': 'sandbox', 'shortcodeType': 'buy_goods', 'shortcode': '174379', 'tillNumber': '123456', 'accountReference': '', 'parent': '174379', 'action': 'create'},
          ],
          'issues': issues,
          'ignoredColumns': <String>[],
        };

    Future<PickedCsv?> pick() async => (name: 'accounts.csv', bytes: Uint8List.fromList(utf8.encode('label\nx\n')));

    testWidgets('the instructions and the column list are on the page', (tester) async {
      final state = _state((req) async => _json({}));
      await _openOver(tester, CsvImportScreen(state: state, pick: pick, openTemplate: (_) async {}));

      expect(find.text('How it works'), findsOneWidget);
      expect(find.textContaining('If any row has a problem, nothing at all is saved.'), findsOneWidget);
      await tester.tap(find.text('The columns'));
      await tester.pumpAndSettle();
      for (final c in businessCsvColumns) {
        expect(find.text(c.name), findsOneWidget, reason: c.name);
      }
    });

    testWidgets('a file with problems lists them and cannot be saved', (tester) async {
      final seen = <http.Request>[];
      final state = _state((req) async => _json(result(dryRun: true, issues: [
            {'row': 3, 'field': 'till_number', 'message': 'A Buy Goods branch needs its till number.'},
          ])), seen);
      await _openOver(tester, CsvImportScreen(state: state, pick: pick, openTemplate: (_) async {}));

      await tester.tap(find.text('Choose a CSV file'));
      await tester.pumpAndSettle();

      expect(seen.single.url.queryParameters['dryRun'], 'true');
      expect(find.text('Fix these first'), findsOneWidget);
      expect(find.text('Row 3, till_number: A Buy Goods branch needs its till number.'), findsOneWidget);
      expect(find.textContaining('Save 2 accounts'), findsNothing);
    });

    testWidgets('a clean file is previewed, then saved with a second call', (tester) async {
      final seen = <http.Request>[];
      final state = _state((req) async => _json(result(dryRun: req.url.queryParameters['dryRun'] == 'true', saved: req.url.queryParameters['dryRun'] == 'false')), seen);
      await _openOver(tester, CsvImportScreen(state: state, pick: pick, openTemplate: (_) async {}));

      await tester.tap(find.text('Choose a CSV file'));
      await tester.pumpAndSettle();

      expect(find.text('Ready to save'), findsOneWidget);
      expect(find.text('2 accounts: 2 to add, 0 to update.'), findsOneWidget);
      expect(find.text('Save 2 accounts'), findsOneWidget);

      await tester.tap(find.text('Save 2 accounts'));
      await tester.pumpAndSettle();

      expect(seen.map((r) => r.url.queryParameters['dryRun']), ['true', 'false']);
      expect(find.byType(CsvImportScreen), findsNothing);
    });

    testWidgets('a file over 1 MB is refused without being sent', (tester) async {
      final seen = <http.Request>[];
      final state = _state((req) async => _json({}), seen);
      Future<PickedCsv?> big() async => (name: 'big.csv', bytes: Uint8List(maxCsvBytes + 1));
      await _openOver(tester, CsvImportScreen(state: state, pick: big, openTemplate: (_) async {}));

      await tester.tap(find.text('Choose a CSV file'));
      await tester.pumpAndSettle();

      expect(find.textContaining('larger than 1 MB'), findsOneWidget);
      expect(seen, isEmpty);
    });
  });
}
