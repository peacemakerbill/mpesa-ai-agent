import 'dart:convert';

import 'package:agentx_app/api/api_client.dart';
import 'package:agentx_app/app_state.dart';
import 'package:agentx_app/models/wallet_action.dart';
import 'package:agentx_app/screens/actions_screen.dart';
import 'package:agentx_app/services/secure_store.dart';
import 'package:agentx_app/theme/app_theme.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

Map<String, dynamic> _action(String id, String status, String summary) => {
      'id': id,
      'type': 'PAY_TILL',
      'status': status,
      'amount': 300,
      'summary': summary,
      'details': {'till': '123456'},
      'warnings': [],
      'needsContact': false,
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
  group('clearing requests, on the wire', () {
    test('one request is cleared with a DELETE and the answer says how many', () async {
      late http.BaseRequest seen;
      final api = ApiClient(
        baseUrl: 'http://x.test',
        client: MockClient((req) async {
          seen = req;
          return http.Response(jsonEncode({'cleared': 1, 'skipped': 0}), 200);
        }),
        tokenProvider: () async => 'tok',
      );

      final result = await api.clearAction('abc');

      expect(seen.method, 'DELETE');
      expect(seen.url.path, '/api/actions/abc');
      expect(result.cleared, 1);
      expect(result.skipped, 0);
    });

    test('the chosen requests are sent together', () async {
      late http.BaseRequest seen;
      late String body;
      final api = ApiClient(
        baseUrl: 'http://x.test',
        client: MockClient((req) async {
          seen = req;
          body = req.body;
          return http.Response(jsonEncode({'cleared': 2, 'skipped': 1}), 200);
        }),
        tokenProvider: () async => 'tok',
      );

      final result = await api.clearActions(['a', 'b', 'c']);

      expect(seen.method, 'POST');
      expect(seen.url.path, '/api/actions/clear');
      expect(jsonDecode(body)['ids'], ['a', 'b', 'c']);
      expect(result.sentence, '2 requests cleared. 1 still open, so left in the list.');
    });

    test('clearing every finished request is a DELETE on the list', () async {
      late http.BaseRequest seen;
      final api = ApiClient(
        baseUrl: 'http://x.test',
        client: MockClient((req) async {
          seen = req;
          return http.Response(jsonEncode({'cleared': 4, 'skipped': 0}), 200);
        }),
        tokenProvider: () async => 'tok',
      );

      final result = await api.clearFinishedActions();

      expect(seen.method, 'DELETE');
      expect(seen.url.path, '/api/actions');
      expect(result.cleared, 4);
    });

    test('the sentence reads naturally for one, none and several', () {
      expect(const ClearResult(cleared: 1, skipped: 0).sentence, '1 request cleared');
      expect(const ClearResult(cleared: 0, skipped: 0).sentence, '0 requests cleared');
      expect(ClearResult.fromJson(const {}).cleared, 0);
    });
  });

  group('clearing requests, on the screen', () {
    late List<Map<String, dynamic>> server;
    late List<String> calls;

    Future<AppState> setUp(WidgetTester tester) async {
      server = [
        _action('open-1', 'CREATED', 'Pay KES 300 to Till 111111'),
        _action('done-1', 'DECLINED', 'Pay KES 300 to Till 222222'),
        _action('done-2', 'DONE_REPORTED', 'Pay KES 300 to Till 333333'),
      ];
      calls = [];
      final api = ApiClient(
        baseUrl: 'http://x.test',
        tokenProvider: () async => 'tok',
        client: MockClient((req) async {
          calls.add('${req.method} ${req.url.path}');
          if (req.method == 'GET') return http.Response(jsonEncode(server), 200);
          if (req.method == 'DELETE' && req.url.path == '/api/actions') {
            final n = server.where((a) => a['status'] != 'CREATED').length;
            server.removeWhere((a) => a['status'] != 'CREATED');
            return http.Response(jsonEncode({'cleared': n, 'skipped': 1}), 200);
          }
          if (req.method == 'DELETE') {
            final id = req.url.pathSegments.last;
            server.removeWhere((a) => a['id'] == id);
            return http.Response(jsonEncode({'cleared': 1, 'skipped': 0}), 200);
          }
          if (req.method == 'POST' && req.url.path == '/api/actions/clear') {
            final ids = (jsonDecode(req.body)['ids'] as List).cast<String>();
            server.removeWhere((a) => ids.contains(a['id']));
            return http.Response(jsonEncode({'cleared': ids.length, 'skipped': 0}), 200);
          }
          return http.Response('{}', 404);
        }),
      );
      final state = AppState(store: _Store(), api: api);
      tester.view.physicalSize = const Size(900, 1800);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      await tester.pumpWidget(MaterialApp(theme: AppTheme.build(state.theme), home: ActionsScreen(state: state)));
      await tester.pumpAndSettle();
      return state;
    }

    testWidgets('only a finished request has a clear button, and it clears after asking', (tester) async {
      await setUp(tester);

      expect(find.byTooltip('Clear this request'), findsNWidgets(2));

      await tester.tap(find.byTooltip('Clear this request').first);
      await tester.pumpAndSettle();
      expect(find.text('Clear this request?'), findsOneWidget);
      await tester.tap(find.text('Clear'));
      await tester.pumpAndSettle();

      expect(calls.where((c) => c.startsWith('DELETE')).length, 1);
      expect(find.byTooltip('Clear this request'), findsOneWidget);
    });

    testWidgets('asking and then keeping clears nothing', (tester) async {
      await setUp(tester);

      await tester.tap(find.byTooltip('Clear this request').first);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Keep'));
      await tester.pumpAndSettle();

      expect(calls.where((c) => c.startsWith('DELETE')), isEmpty);
      expect(find.byTooltip('Clear this request'), findsNWidgets(2));
    });

    testWidgets('select mode ticks finished requests and clears them together', (tester) async {
      await setUp(tester);

      await tester.tap(find.byTooltip('More'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Select requests'));
      await tester.pumpAndSettle();
      expect(find.text('Select requests'), findsOneWidget);   // the title of the select bar

      // an open request cannot be ticked, so only two boxes show
      expect(find.byType(Checkbox), findsNWidgets(2));
      await tester.tap(find.byType(Checkbox).first);
      await tester.pump();
      expect(find.text('1 selected'), findsOneWidget);

      await tester.tap(find.byTooltip('Select all finished'));
      await tester.pump();
      expect(find.text('2 selected'), findsOneWidget);

      await tester.tap(find.byTooltip('Clear selected'));
      await tester.pumpAndSettle();
      expect(find.text('Clear 2 requests?'), findsOneWidget);
      await tester.tap(find.text('Clear'));
      await tester.pumpAndSettle();

      expect(calls, contains('POST /api/actions/clear'));
      expect(find.byType(Checkbox), findsNothing);   // back to the normal list
      expect(find.byTooltip('Clear this request'), findsNothing);
      expect(find.text('Pay KES 300 to Till 111111'), findsOneWidget);   // the open one is still there
    });

    testWidgets('clear finished requests asks once and leaves the open ones', (tester) async {
      await setUp(tester);

      await tester.tap(find.byTooltip('More'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Clear finished requests'));
      await tester.pumpAndSettle();
      expect(find.text('Clear finished requests?'), findsOneWidget);
      await tester.tap(find.text('Clear'));
      await tester.pumpAndSettle();

      expect(calls, contains('DELETE /api/actions'));
      expect(find.text('Pay KES 300 to Till 111111'), findsOneWidget);
      expect(find.text('Pay KES 300 to Till 222222'), findsNothing);
    });
  });
}
