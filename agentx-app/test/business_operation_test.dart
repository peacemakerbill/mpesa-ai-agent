import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:agentx_app/api/api_client.dart';
import 'package:agentx_app/app_state.dart';
import 'package:agentx_app/models/business_operation.dart';
import 'package:agentx_app/screens/business_operation_screen.dart';
import 'package:agentx_app/screens/business_operations_screen.dart';
import 'package:agentx_app/services/approval.dart';
import 'package:agentx_app/services/secure_store.dart';
import 'package:agentx_app/theme/app_theme.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

const _id = '11111111-2222-3333-4444-555555555555';

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

class _Approval extends Approval {
  _Approval(this.result);

  final ApprovalResult result;
  int asked = 0;

  @override
  Future<ApprovalResult> ask(String reason) async {
    asked++;
    return result;
  }
}

Map<String, Object?> _op({String status = 'PENDING_APPROVAL', bool needsApproval = true, String type = 'B2C', Object? expiresAt, String resultText = '', String failure = ''}) => {
      'id': _id,
      'accountId': '99999999-9999-9999-9999-999999999999',
      'accountLabel': 'Main shop',
      'type': type,
      'typeLabel': 'Pay out to a phone',
      'status': status,
      'amount': 500,
      'summary': 'Pay KES 500 to 254700111222 from Main shop',
      'params': {'phone': '254700111222', 'remarks': 'Salary', 'command': 'BusinessPayment', 'occasion': ''},
      'needsApproval': needsApproval,
      'receipt': '',
      'resultText': resultText,
      'failure': failure,
      'createdAt': '2026-11-03T06:00:00Z',
      'expiresAt': expiresAt ?? DateTime.now().add(const Duration(minutes: 20)).toUtc().toIso8601String(),
      'completedAt': null,
    };

http.Response _json(Object body, [int status = 200]) =>
    http.Response(jsonEncode(body), status, headers: {'content-type': 'application/json'});

AppState _state(Future<http.Response> Function(http.Request) handler, {Approval? approval, List<http.Request>? seen}) {
  final client = MockClient((req) async {
    seen?.add(req);
    return handler(req);
  });
  return AppState(
    store: _Store(),
    approval: approval,
    api: ApiClient(baseUrl: 'http://x.test', client: client, tokenProvider: () async => 'tok'),
  );
}

Future<void> _show(WidgetTester tester, Widget screen) async {
  await tester.binding.setSurfaceSize(const Size(800, 2000));
  addTearDown(() => tester.binding.setSurfaceSize(null));
  await tester.pumpWidget(MaterialApp(theme: AppTheme.build(ThemeKey.daylight), home: screen));
  await tester.pumpAndSettle();
}

void main() {
  group('links', () {
    test('a link from the assistant gives the request id', () {
      expect(parseBusinessLink(Uri.parse('https://app.example.com/b/$_id')), _id);
      expect(parseBusinessLink(Uri.parse('agentx://b/$_id')), _id);
      expect(parseBusinessLink(Uri.parse('https://app.example.com/b/${_id.toUpperCase()}')), _id);
    });

    test('anything else is ignored', () {
      expect(parseBusinessLink(Uri.parse('https://app.example.com/a/$_id')), isNull);
      expect(parseBusinessLink(Uri.parse('https://app.example.com/b/not-an-id')), isNull);
      expect(parseBusinessLink(Uri.parse('https://app.example.com/b/$_id/extra')), isNull);
      expect(parseBusinessLink(Uri.parse('otherapp://b/$_id')), isNull);
    });

    test('a link inside a message is found, and one for a wallet request is not taken', () {
      expect(businessIdIn('Open this link on your phone:\nhttps://app.example.com/b/$_id\nYou approve with your fingerprint.'), _id);
      expect(businessIdIn('See (https://app.example.com/b/$_id).'), _id);
      expect(businessIdIn('https://app.example.com/a/$_id'), isNull);
      expect(businessIdIn('no link here'), isNull);
    });
  });

  group('the model', () {
    test('a waiting request can be decided until it expires', () {
      final op = BusinessOperation.fromJson(_op(expiresAt: '2026-11-03T06:30:00Z'));
      expect(op.stage, OperationStage.waiting);
      expect(op.stageLabel, 'Needs your approval');
      expect(op.canDecide(DateTime.utc(2026, 11, 3, 6, 10)), isTrue);
      expect(op.canDecide(DateTime.utc(2026, 11, 3, 6, 31)), isFalse);
      expect(op.movesMoneyOut, isTrue);
    });

    test('a request that is not waiting cannot be decided', () {
      expect(BusinessOperation.fromJson(_op(status: 'SUBMITTED', needsApproval: false)).canDecide(DateTime.utc(2026, 11, 3, 6, 1)), isFalse);
      expect(BusinessOperation.fromJson(_op(status: 'DECLINED', needsApproval: false)).stage, OperationStage.declined);
      expect(BusinessOperation.fromJson(_op(status: 'UNKNOWN', needsApproval: false)).stageLabel, 'Not sure');
    });

    test('reads are not money out and the details use plain names', () {
      final op = BusinessOperation.fromJson(_op(type: 'BALANCE'));
      expect(op.movesMoneyOut, isFalse);
      final labels = op.details.map((e) => e.key).toList();
      expect(labels, containsAll(['Phone', 'Note', 'Payout type']));
      expect(labels, isNot(contains('Occasion')));
    });
  });

  group('approving', () {
    testWidgets('a waiting request shows what will happen and sends it only after the phone confirms', (tester) async {
      final seen = <http.Request>[];
      final approval = _Approval(ApprovalResult.approvedBiometric);
      final state = _state((req) async {
        if (req.method == 'POST') return _json(_op(status: 'SUBMITTED', needsApproval: false, resultText: 'Safaricom accepted the request.'));
        return _json(_op());
      }, approval: approval, seen: seen);
      await _show(tester, BusinessOperationScreen(state: state, operationId: _id));

      expect(find.text('Needs your approval'), findsOneWidget);
      expect(find.textContaining('Money leaves your business account'), findsOneWidget);
      expect(find.text('254700111222'), findsOneWidget);
      expect(seen.where((r) => r.method == 'POST'), isEmpty);

      await tester.tap(find.text('Approve'));
      await tester.pumpAndSettle();

      final post = seen.singleWhere((r) => r.method == 'POST');
      expect(post.url.path, '/api/business-operations/$_id/approve');
      expect(jsonDecode(post.body), {'method': 'BIOMETRIC'});
      expect(approval.asked, 1);
      expect(find.text('Sent to Safaricom'), findsOneWidget);
      expect(find.text('Approve'), findsNothing);
      await tester.pumpWidget(const SizedBox());
    });

    testWidgets('a refused fingerprint sends nothing', (tester) async {
      final seen = <http.Request>[];
      final state = _state((req) async => _json(_op()), approval: _Approval(ApprovalResult.cancelled), seen: seen);
      await _show(tester, BusinessOperationScreen(state: state, operationId: _id));
      await tester.tap(find.text('Approve'));
      await tester.pumpAndSettle();
      expect(seen.where((r) => r.method == 'POST'), isEmpty);
      expect(find.textContaining('Not approved. Nothing was sent.'), findsOneWidget);
    });

    testWidgets('declining asks first and then tells the server', (tester) async {
      final seen = <http.Request>[];
      final state = _state((req) async {
        if (req.method == 'POST') return _json(_op(status: 'DECLINED', needsApproval: false, resultText: 'Declined. Nothing was sent to Safaricom.'));
        return _json(_op());
      }, seen: seen);
      await _show(tester, BusinessOperationScreen(state: state, operationId: _id));
      await tester.tap(find.text('Decline'));
      await tester.pumpAndSettle();
      expect(seen.where((r) => r.method == 'POST'), isEmpty);
      await tester.tap(find.widgetWithText(TextButton, 'Decline'));
      await tester.pumpAndSettle();
      expect(seen.singleWhere((r) => r.method == 'POST').url.path, '/api/business-operations/$_id/decline');
      expect(find.text('Declined'), findsOneWidget);
    });

    testWidgets('an expired request offers nothing to approve', (tester) async {
      final state = _state((req) async => _json(_op(status: 'EXPIRED', needsApproval: false, resultText: 'Not approved in time. Nothing was sent to Safaricom.')));
      await _show(tester, BusinessOperationScreen(state: state, operationId: _id));
      expect(find.text('Expired'), findsOneWidget);
      expect(find.text('Approve'), findsNothing);
      expect(find.textContaining('Not approved in time'), findsOneWidget);
    });

    testWidgets('when the server says no, its reason is shown', (tester) async {
      final state = _state((req) async => _json({'message': 'This request is no longer waiting for approval.'}, 409), approval: _Approval(ApprovalResult.approvedDeviceLock));
      await _show(tester, BusinessOperationScreen(state: state, operationId: _id));
      expect(find.textContaining('no longer waiting for approval'), findsOneWidget);
      expect(find.text('Approve'), findsNothing);
    });
  });

  group('the list', () {
    testWidgets('requests are listed with their state and open on tap', (tester) async {
      final state = _state((req) async {
        if (req.url.path == '/api/business-operations') return _json([_op(), {..._op(status: 'SUCCEEDED', needsApproval: false, type: 'BALANCE'), 'id': '22222222-2222-3333-4444-555555555555', 'amount': 0, 'summary': 'Check the account balance on Main shop'}]);
        return _json(_op());
      });
      await _show(tester, BusinessOperationsScreen(state: state));
      expect(find.text('Needs your approval'), findsOneWidget);
      expect(find.text('Done'), findsOneWidget);
      await tester.tap(find.textContaining('Pay KES 500'));
      await tester.pumpAndSettle();
      expect(find.text('Approve'), findsOneWidget);
    });

    testWidgets('an empty list explains how to start', (tester) async {
      final state = _state((req) async => _json(<Object?>[]));
      await _show(tester, BusinessOperationsScreen(state: state));
      expect(find.text('Nothing here yet'), findsOneWidget);
    });
  });
}
