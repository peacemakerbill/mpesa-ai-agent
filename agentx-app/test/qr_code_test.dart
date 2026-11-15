import 'dart:convert';

import 'package:agentx_app/api/api_client.dart';
import 'package:agentx_app/app_state.dart';
import 'package:agentx_app/models/bonga.dart';
import 'package:agentx_app/models/business_account.dart';
import 'package:agentx_app/models/business_operation.dart';
import 'package:agentx_app/screens/qr_code_screen.dart';
import 'package:agentx_app/services/secure_store.dart';
import 'package:agentx_app/theme/app_theme.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

// A real 1 pixel picture, so the image widget has something it can draw.
const _png = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==';

Map<String, dynamic> _account({bool qrReady = true}) => {
      'id': '11111111-1111-1111-1111-111111111111',
      'label': 'Main shop',
      'environment': 'SANDBOX',
      'shortcodeType': 'BUY_GOODS',
      'shortcode': '174379',
      'tillNumber': '9876543',
      'accountReference': '',
      'parentId': null,
      'inheritsCredentials': false,
      'consumerKeyHint': '1234',
      'hasConsumerSecret': true,
      'initiatorName': '',
      'hasInitiatorPassword': false,
      'hasPasskey': false,
      'capabilities': [
        {'code': 'DYNAMIC_QR', 'label': 'Dynamic QR', 'ready': qrReady, 'needs': 'consumer key and secret'},
      ],
      'verifiedAt': null,
      'createdAt': '2026-11-03T06:00:00Z',
    };

Map<String, dynamic> _qrOperation({String trx = 'BG', String cpi = '123456', String qr = _png}) => {
      'id': '22222222-2222-2222-2222-222222222222',
      'accountLabel': 'Main shop',
      'type': 'DYNAMIC_QR',
      'typeLabel': 'Dynamic QR',
      'status': 'SUCCEEDED',
      'amount': 200,
      'summary': 'QR code for KES 200 to Till 123456 (reference Main)',
      'params': {'trxCode': trx, 'cpi': cpi, 'payTo': 'Till 123456'},
      'needsApproval': false,
      'qrCode': qr,
    };

class _Store extends SecureStore {
  @override
  Future<String?> token() async => 'tok';

  @override
  Future<String?> phone() async => null;

  @override
  Future<String?> theme() async => null;
}

void main() {
  group('the QR picture on an operation', () {
    test('is read from the answer and decoded', () {
      final op = BusinessOperation.fromJson(_qrOperation());
      expect(op.qrBytes, isNotNull);
      expect(op.qrBytes!.length, greaterThan(20));
      expect(op.qrTill, '123456');
    });

    test('is empty when the server sent none or sent rubbish', () {
      expect(BusinessOperation.fromJson(_qrOperation(qr: '')).qrBytes, isNull);
      expect(BusinessOperation.fromJson(_qrOperation(qr: '***')).qrBytes, isNull);
      final plain = BusinessOperation.fromJson({..._qrOperation(), 'qrCode': null});
      expect(plain.qrCode, '');
    });

    test('only a Buy Goods code has a Till for Bonga steps', () {
      expect(BusinessOperation.fromJson(_qrOperation(trx: 'PB', cpi: '600111')).qrTill, '');
      expect(BusinessOperation.fromJson(_qrOperation(trx: 'SM', cpi: '254712345678')).qrTill, '');
    });
  });

  group('Lipa na Bonga steps', () {
    test('name the Till and work out the points', () {
      final steps = bongaSteps('123456', amount: 200);
      expect(steps.length, 4);
      expect(steps.first, contains('*126#'));
      expect(steps[1], contains('123456'));
      expect(steps[2], contains('1000 Bonga Points'));
      expect(steps.last, contains('Never share that PIN'));
    });

    test('leave the points out when there is no amount', () {
      expect(bongaSteps('123456')[2], isNot(contains('Bonga Points')));
    });
  });

  test('making a QR code is a POST to the QR address with only what was filled in', () async {
    late http.BaseRequest seen;
    late Map<String, dynamic> body;
    final api = ApiClient(
      baseUrl: 'http://x.test',
      client: MockClient((req) async {
        seen = req;
        body = jsonDecode(req.body) as Map<String, dynamic>;
        return http.Response(jsonEncode(_qrOperation()), 201);
      }),
      tokenProvider: () async => 'tok',
    );

    final op = await api.createQrCode(account: 'abc', amount: 200, payTo: 'till', payToNumber: ' 123456 ', reference: ' ');

    expect(seen.method, 'POST');
    expect(seen.url.path, '/api/business-operations/qr');
    expect(body, {'account': 'abc', 'amount': 200, 'payTo': 'till', 'payToNumber': '123456'});
    expect(op.qrBytes, isNotNull);
  });

  group('the QR screen', () {
    late List<Map<String, dynamic>> posted;
    late List<List<int>> saved;

    Future<void> open(WidgetTester tester, {List<Map<String, dynamic>>? accounts, Map<String, dynamic>? answer}) async {
      posted = [];
      saved = [];
      final api = ApiClient(
        baseUrl: 'http://x.test',
        client: MockClient((req) async {
          if (req.method == 'POST' && req.url.path == '/api/business-operations/qr') {
            posted.add(jsonDecode(req.body) as Map<String, dynamic>);
            return http.Response(jsonEncode(answer ?? _qrOperation()), 201);
          }
          return http.Response('{}', 404);
        }),
        tokenProvider: () async => 'tok',
      );
      final state = AppState(store: _Store(), api: api);
      tester.view.physicalSize = const Size(900, 2200);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final list = (accounts ?? [_account()]).map(BusinessAccount.fromJson).toList();
      await tester.pumpWidget(MaterialApp(
        theme: AppTheme.build(state.theme),
        home: QrCodeScreen(state: state, accounts: list, save: (png, name) async => saved.add(png)),
      ));
      await tester.pumpAndSettle();
    }

    testWidgets('an account that cannot make a QR code is not offered, and the screen says what is needed', (tester) async {
      await open(tester, accounts: [_account(qrReady: false)]);
      expect(find.textContaining('needs one saved business account'), findsOneWidget);
      expect(find.text('Make QR code'), findsNothing);
    });

    testWidgets('the amount is checked before anything is sent', (tester) async {
      await open(tester);
      await tester.tap(find.text('Make QR code'));
      await tester.pumpAndSettle();
      expect(find.text('Enter the amount in whole shillings.'), findsOneWidget);
      expect(posted, isEmpty);
    });

    testWidgets('a Paybill needs its number and the account number', (tester) async {
      await open(tester);
      await tester.tap(find.text('A Paybill'));
      await tester.pumpAndSettle();
      await tester.enterText(find.widgetWithText(TextField, 'Amount (KES)'), '900');
      await tester.enterText(find.widgetWithText(TextField, 'Paybill number'), '12');
      await tester.tap(find.text('Make QR code'));
      await tester.pumpAndSettle();
      expect(find.text('The Paybill number must be 5 to 7 digits.'), findsOneWidget);

      await tester.enterText(find.widgetWithText(TextField, 'Paybill number'), '888880');
      await tester.tap(find.text('Make QR code'));
      await tester.pumpAndSettle();
      expect(find.text('A Paybill code needs the account number to pay into.'), findsOneWidget);
      expect(posted, isEmpty);
    });

    testWidgets('a Till code is asked for, shown, savable, and offers the Bonga steps', (tester) async {
      await open(tester);
      await tester.tap(find.text('A Till'));
      await tester.pumpAndSettle();
      await tester.enterText(find.widgetWithText(TextField, 'Till number'), '123456');
      await tester.enterText(find.widgetWithText(TextField, 'Amount (KES)'), '200');
      await tester.tap(find.text('Make QR code'));
      await tester.pumpAndSettle();

      expect(posted.single, {'account': '11111111-1111-1111-1111-111111111111', 'amount': 200, 'payTo': 'till', 'payToNumber': '123456'});
      expect(find.byType(Image), findsOneWidget);
      expect(find.textContaining('QR code for KES 200'), findsOneWidget);
      expect(find.text('Prefer to pay with Bonga Points?'), findsOneWidget);
      expect(find.textContaining('Safaricom has no way for a business to charge Bonga Points'), findsOneWidget);
      expect(find.textContaining('1000 Bonga Points'), findsOneWidget);

      await tester.tap(find.text('Save or share the picture'));
      await tester.pumpAndSettle();
      expect(saved.length, 1);

      await tester.tap(find.text('Make another'));
      await tester.pumpAndSettle();
      expect(find.text('Make QR code'), findsOneWidget);
    });

    testWidgets('a code for a Paybill or a phone has no Bonga steps', (tester) async {
      await open(tester, answer: _qrOperation(trx: 'SM', cpi: '254712345678'));
      await tester.tap(find.text('A phone number'));
      await tester.pumpAndSettle();
      await tester.enterText(find.widgetWithText(TextField, 'Phone number'), '0712345678');
      await tester.enterText(find.widgetWithText(TextField, 'Amount (KES)'), '75');
      await tester.tap(find.text('Make QR code'));
      await tester.pumpAndSettle();
      expect(find.byType(Image), findsOneWidget);
      expect(find.text('Prefer to pay with Bonga Points?'), findsNothing);
    });

    testWidgets('an answer without a picture shows the reason instead', (tester) async {
      await open(tester, answer: {..._qrOperation(qr: ''), 'status': 'FAILED', 'failure': 'Invalid Access Token'});
      await tester.enterText(find.widgetWithText(TextField, 'Amount (KES)'), '50');
      await tester.tap(find.text('Make QR code'));
      await tester.pumpAndSettle();
      expect(find.text('Invalid Access Token'), findsOneWidget);
      expect(find.byType(Image), findsNothing);
    });
  });
}
