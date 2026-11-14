import 'dart:convert';

import 'package:agentx_app/api/api_client.dart';
import 'package:agentx_app/app_state.dart';
import 'package:agentx_app/chat/chat_device.dart';
import 'package:agentx_app/models/chat.dart';
import 'package:agentx_app/models/wallet_action.dart';
import 'package:agentx_app/services/secure_store.dart';
import 'package:agentx_app/theme/app_theme.dart';
import 'package:agentx_app/widgets/chat_bubbles.dart';
import 'package:agentx_app/widgets/otp_approval.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

const _requestId = '0b7f1f0e-6f3a-4a0e-9d57-3d9d6b6f2c11';
const _businessId = '11111111-2222-3333-4444-555555555555';

class _Store extends SecureStore {
  @override
  Future<String?> token() async => 'tok';

  @override
  Future<String?> phone() async => null;

  @override
  Future<String?> theme() async => null;
}

class _NoDevice implements ChatDevice {
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

AppState _state() {
  final api = ApiClient(baseUrl: 'http://x.test', client: MockClient((_) async => http.Response('{}', 200)), tokenProvider: () async => 'tok');
  return AppState(store: _Store(), api: api);
}

Map<String, dynamic> _action(String status) => {
      'id': _requestId,
      'type': 'PAY_TILL',
      'status': status,
      'amount': 300,
      'summary': 'Pay KES 300 to Till 123456',
      'details': {'till': '123456'},
      'warnings': [],
      'needsContact': false,
    };

void main() {
  group('approving with a code, on the wire', () {
    test('asking for a code is a POST and the answer is read', () async {
      late http.BaseRequest seen;
      final api = ApiClient(
        baseUrl: 'http://x.test',
        client: MockClient((req) async {
          seen = req;
          return http.Response(jsonEncode({'expiresAt': '2026-11-15T00:20:00Z', 'resendAfterSeconds': 45, 'devCode': '123456'}), 200);
        }),
        tokenProvider: () async => 'tok',
      );

      final sent = await api.sendApprovalCode('abc');

      expect(seen.method, 'POST');
      expect(seen.url.path, '/api/actions/abc/approval-code');
      expect(sent.resendAfterSeconds, 45);
      expect(sent.devCode, '123456');
      expect(sent.expiresAt, isNotNull);
    });

    test('a real system sends no code back, and a missing time falls back to 30 seconds', () {
      final sent = ApprovalCodeSent.fromJson(const {});
      expect(sent.devCode, isNull);
      expect(sent.expiresAt, isNull);
      expect(sent.resendAfterSeconds, 30);
    });

    test('approving with a code sends the method OTP and the code', () async {
      late http.Request seen;
      final api = ApiClient(
        baseUrl: 'http://x.test',
        client: MockClient((req) async {
          seen = req;
          return http.Response(jsonEncode(_action('APPROVED')), 200);
        }),
        tokenProvider: () async => 'tok',
      );

      final a = await api.approveWithCode('abc', '482913');

      expect(seen.method, 'POST');
      expect(seen.url.path, '/api/actions/abc/approve');
      expect(jsonDecode(seen.body), {'method': 'OTP', 'code': '482913'});
      expect(a.status, 'APPROVED');
    });

    test('a business request uses its own paths', () async {
      final seen = <String>[];
      final api = ApiClient(
        baseUrl: 'http://x.test',
        client: MockClient((req) async {
          seen.add('${req.method} ${req.url.path}');
          if (req.url.path.endsWith('/approval-code')) return http.Response(jsonEncode({'resendAfterSeconds': 30}), 200);
          return http.Response(
              jsonEncode({
                'id': _businessId,
                'accountId': '99999999-9999-9999-9999-999999999999',
                'accountLabel': 'Main shop',
                'type': 'B2C',
                'typeLabel': 'Pay out to a phone',
                'status': 'SUBMITTED',
                'amount': 500,
                'summary': 'Pay KES 500 to 254700111222 from Main shop',
                'params': <String, dynamic>{},
                'needsApproval': true,
                'receipt': '',
                'resultText': '',
                'failure': '',
              }),
              200);
        }),
        tokenProvider: () async => 'tok',
      );

      await api.sendBusinessApprovalCode(_businessId);
      final op = await api.approveBusinessOperationWithCode(_businessId, '482913');

      expect(seen, ['POST /api/business-operations/$_businessId/approval-code', 'POST /api/business-operations/$_businessId/approve']);
      expect(op.id, _businessId);
    });
  });

  group('the Approve with OTP button in the chat', () {
    Future<void> show(WidgetTester tester, ChatMessage m, List<String> log) async {
      final actions = BubbleActions(
        onButton: (_) {},
        onRow: (_) {},
        onFindContact: (_) {},
        onOpenRequest: (id) => log.add('open $id'),
        onOpenBusiness: (id) => log.add('open business $id'),
        onApproveWithOtp: (id, {required bool business}) => log.add('otp $id ${business ? 'business' : 'request'}'),
        onLink: (_) {},
        onError: (_) {},
        loadFile: (_) async => throw UnimplementedError(),
        device: _NoDevice(),
      );
      final state = _state();
      await tester.pumpWidget(MaterialApp(
        theme: AppTheme.build(state.theme),
        home: Scaffold(body: SingleChildScrollView(child: MessageBubble(message: m, live: true, actions: actions))),
      ));
    }

    ChatMessage message(Map<String, dynamic> extra) =>
        ChatMessage.fromJson({'id': 1, 'dir': 'out', 'kind': 'TEXT', 'at': 1, 'text': 'Your request is ready.', ...extra});

    testWidgets('a request message shows both buttons, and the code button asks for a code', (tester) async {
      final log = <String>[];
      await show(tester, message({'requestId': _requestId}), log);

      expect(find.text('Open request'), findsOneWidget);
      expect(find.text('Approve with OTP'), findsOneWidget);

      await tester.tap(find.text('Approve with OTP'));
      await tester.pump();
      expect(log, ['otp $_requestId request']);

      await tester.tap(find.text('Open request'));
      await tester.pump();
      expect(log.last, 'open $_requestId');
    });

    testWidgets('a business request says so', (tester) async {
      final log = <String>[];
      await show(tester, message({'businessId': _businessId}), log);

      await tester.tap(find.text('Approve with OTP'));
      await tester.pump();
      await tester.tap(find.text('Open request'));
      await tester.pump();

      expect(log, ['otp $_businessId business', 'open business $_businessId']);
    });

    testWidgets('an ordinary message has neither button', (tester) async {
      await show(tester, message({}), <String>[]);

      expect(find.text('Open request'), findsNothing);
      expect(find.text('Approve with OTP'), findsNothing);
    });

    testWidgets('a message from the customer never has the buttons', (tester) async {
      await show(tester, ChatMessage.fromJson({'id': 2, 'dir': 'in', 'kind': 'TEXT', 'at': 1, 'text': 'x', 'requestId': _requestId}), <String>[]);

      expect(find.text('Approve with OTP'), findsNothing);
    });
  });

  group('the code sheet', () {
    late AppState state;
    bool? result;

    Future<void> open(
      WidgetTester tester, {
      required Future<ApprovalCodeSent> Function() send,
      required Future<void> Function(String code) approve,
      bool moneyOut = false,
    }) async {
      tester.view.physicalSize = const Size(900, 1800);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      state = _state();
      result = null;
      await tester.pumpWidget(MaterialApp(
        theme: AppTheme.build(state.theme),
        home: Builder(
          builder: (context) => Scaffold(
            body: Center(
              child: FilledButton(
                onPressed: () async {
                  result = await showOtpApproval(context,
                      state: state, summary: 'Pay KES 10 to Till 123456', send: send, approve: approve, moneyOut: moneyOut);
                },
                child: const Text('Open'),
              ),
            ),
          ),
        ),
      ));
      await tester.tap(find.text('Open'));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 400));
    }

    Future<void> close(WidgetTester tester) async {
      await tester.tap(find.widgetWithText(OutlinedButton, 'Close'));
      await tester.pumpAndSettle();
    }

    FilledButton approveButton(WidgetTester tester) =>
        tester.widget<FilledButton>(find.ancestor(of: find.text('Approve'), matching: find.bySubtype<FilledButton>()));

    testWidgets('it sends the code by itself, waits for six digits, and closes once approved', (tester) async {
      var sent = 0;
      String? approvedWith;
      await open(tester, send: () async {
        sent++;
        return const ApprovalCodeSent(resendAfterSeconds: 30);
      }, approve: (code) async => approvedWith = code);

      expect(sent, 1);
      expect(find.text('Approve with OTP'), findsOneWidget);
      expect(find.text('Pay KES 10 to Till 123456'), findsOneWidget);
      expect(find.text('Code sent. It works for 5 minutes.'), findsOneWidget);
      expect(approveButton(tester).onPressed, isNull);

      await tester.enterText(find.byType(TextField), '4829');
      await tester.pump();
      expect(approveButton(tester).onPressed, isNull);

      await tester.enterText(find.byType(TextField), '482913');
      await tester.pump();
      expect(approveButton(tester).onPressed, isNotNull);

      await tester.tap(find.text('Approve'));
      await tester.pumpAndSettle();

      expect(approvedWith, '482913');
      expect(result, isTrue);
      expect(find.text('Approve with OTP'), findsNothing);
    });

    testWidgets('only digits are accepted and no more than six', (tester) async {
      await open(tester, send: () async => const ApprovalCodeSent(), approve: (_) async {});

      await tester.enterText(find.byType(TextField), '12ab34567890');
      await tester.pump();

      expect(tester.widget<TextField>(find.byType(TextField)).controller!.text, '123456');
      await close(tester);
    });

    testWidgets('a wrong code is explained and the sheet stays open', (tester) async {
      await open(tester, send: () async => const ApprovalCodeSent(), approve: (_) async {
        throw ApiException(400, 'That code is not right or has expired. Ask for a new one.');
      });

      await tester.enterText(find.byType(TextField), '000000');
      await tester.pump();
      await tester.tap(find.text('Approve'));
      await tester.pumpAndSettle();

      expect(find.text('That code is not right or has expired. Ask for a new one.'), findsOneWidget);
      expect(find.text('Approve with OTP'), findsOneWidget);
      expect(result, isNull);

      // typing again clears the message
      await tester.enterText(find.byType(TextField), '00000');
      await tester.pump();
      expect(find.text('That code is not right or has expired. Ask for a new one.'), findsNothing);
      await close(tester);
    });

    testWidgets('both close buttons close it without approving', (tester) async {
      var approved = 0;
      await open(tester, send: () async => const ApprovalCodeSent(), approve: (_) async => approved++);

      expect(find.byTooltip('Close'), findsOneWidget);
      expect(find.widgetWithText(OutlinedButton, 'Close'), findsOneWidget);

      await tester.tap(find.widgetWithText(OutlinedButton, 'Close'));
      await tester.pumpAndSettle();

      expect(result, isFalse);
      expect(approved, 0);
      expect(find.text('Approve with OTP'), findsNothing);
    });

    testWidgets('a test system shows the code and fills it in', (tester) async {
      await open(tester, send: () async => const ApprovalCodeSent(devCode: '123456'), approve: (_) async {});

      expect(find.textContaining('the code is 123456'), findsOneWidget);
      expect(tester.widget<TextField>(find.byType(TextField)).controller!.text, '123456');
      expect(approveButton(tester).onPressed, isNotNull);
      await close(tester);
    });

    testWidgets('a business request carries the warning about money leaving', (tester) async {
      await open(tester, send: () async => const ApprovalCodeSent(), approve: (_) async {}, moneyOut: true);

      expect(find.textContaining('Money leaves your business account'), findsOneWidget);
      await close(tester);
    });

    testWidgets('a new code can be asked for once the wait is over', (tester) async {
      var sent = 0;
      await open(tester, send: () async {
        sent++;
        return const ApprovalCodeSent(resendAfterSeconds: 30);
      }, approve: (_) async {});

      expect(find.text('Send a new code in 30s'), findsOneWidget);
      await tester.tap(find.text('Send a new code in 30s'), warnIfMissed: false);
      await tester.pump();
      expect(sent, 1);

      await tester.pump(const Duration(seconds: 10));
      expect(find.text('Send a new code in 20s'), findsOneWidget);

      await tester.pump(const Duration(seconds: 20));
      expect(find.text('Send a new code'), findsOneWidget);
      await tester.tap(find.text('Send a new code'));
      await tester.pump();
      await tester.pump();
      expect(sent, 2);

      await tester.tap(find.widgetWithText(OutlinedButton, 'Close'));
      await tester.pumpAndSettle();
    });

    testWidgets('a code that was just sent is not sent again, and the box stays', (tester) async {
      await open(tester, send: () async {
        throw ApiException(429, 'A code was just sent. Wait a few seconds before asking for another.');
      }, approve: (_) async {});

      expect(find.text('A code was just sent. Wait a few seconds before asking for another.'), findsOneWidget);
      expect(find.byType(TextField), findsOneWidget);
      expect(find.text('Send a new code in 30s'), findsOneWidget);

      await tester.tap(find.widgetWithText(OutlinedButton, 'Close'));
      await tester.pumpAndSettle();
    });
  });
}
