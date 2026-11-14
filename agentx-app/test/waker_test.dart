import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:agentx_app/api/api_client.dart';
import 'package:agentx_app/app_state.dart';
import 'package:agentx_app/models/wake.dart';
import 'package:agentx_app/services/secure_store.dart';
import 'package:agentx_app/services/waker.dart';

const _up = WakeStatus(api: true, agent: true, ready: true);
const _waking = WakeStatus(api: true, agent: false, ready: false);

/// Never reads the phone's secure storage.
class _Store extends SecureStore {
  @override
  Future<String?> token() async => null;

  @override
  Future<String?> phone() async => null;

  @override
  Future<String?> theme() async => null;
}

Waker _waker(List<Object> answers, {Duration giveUpAfter = const Duration(minutes: 4)}) {
  var i = 0;
  return Waker(
    () async {
      final next = answers[i < answers.length ? i++ : answers.length - 1];
      if (next is Exception) throw next;
      return next as WakeStatus;
    },
    pause: Duration.zero,
    giveUpAfter: giveUpAfter,
    showAfter: const Duration(milliseconds: 20),
    sleep: (_) async {},
  );
}

void main() {
  test('a server that is already awake is ready after one question', () async {
    expect(await _waker([_up]).run(), isTrue);
  });

  test('it keeps asking while the assistant is still waking, and reports each answer', () async {
    final seen = <WakeStatus?>[];
    final ready = await _waker([_waking, _waking, _up]).run(onAnswer: seen.add);

    expect(ready, isTrue);
    expect(seen.map((s) => s?.ready), [false, false, true]);
  });

  test('no answer, a timeout or a busy host are not the end, it keeps asking', () async {
    final ready = await _waker([ApiException(0, 'too long'), ApiException(503, 'starting'), ApiException(502, 'starting'), _up]).run();

    expect(ready, isTrue);
  });

  test('it gives up when the server never gets ready', () async {
    final ready = await _waker([ApiException(0, 'no answer')], giveUpAfter: Duration.zero).run();

    expect(ready, isFalse);
  });

  test('a server without the check is treated as ready, so an older server is not locked out', () async {
    expect(await _waker([ApiException(404, 'Not found')]).run(), isTrue);
  });

  test('the wake-up question is sent without a token and reads the answer', () async {
    late http.BaseRequest seen;
    final client = MockClient((req) async {
      seen = req;
      return http.Response(jsonEncode({'api': 'up', 'agent': 'waking', 'ready': false}), 200);
    });
    final api = ApiClient(baseUrl: 'http://x.test', client: client, tokenProvider: () async => 'must-not-be-sent');

    final status = await api.wake();

    expect(seen.url.path, '/api/wake');
    expect(seen.headers.containsKey('Authorization'), isFalse);
    expect(status.api, isTrue);
    expect(status.agent, isFalse);
    expect(status.ready, isFalse);
  });

  test('connect wakes the server, then reads its settings', () async {
    final paths = <String>[];
    final client = MockClient((req) async {
      paths.add(req.url.path);
      if (req.url.path == '/api/wake') return http.Response(jsonEncode({'api': 'up', 'agent': 'up', 'ready': true}), 200);
      return http.Response(jsonEncode({'ussdMain': '*334#', 'environment': 'staging'}), 200);
    });
    final api = ApiClient(baseUrl: 'http://x.test', client: client);
    final state = AppState(store: _Store(), api: api, waker: Waker(api.wake, pause: Duration.zero));

    expect(state.server, ServerPhase.checking);
    await state.connect();

    expect(state.server, ServerPhase.ready);
    expect(paths, ['/api/wake', '/api/config']);
    expect(state.config.environment, 'staging');
  });

  test('connect shows the starting phase while it waits, and unreachable when it gives up', () async {
    final phases = <ServerPhase>[];
    final state = AppState(
      store: _Store(),
      api: ApiClient(baseUrl: 'http://x.test', client: MockClient((_) async => http.Response('', 503))),
      waker: Waker(
        () async => throw ApiException(503, 'starting'),
        pause: const Duration(milliseconds: 60),
        giveUpAfter: const Duration(milliseconds: 150),
        showAfter: const Duration(milliseconds: 20),
      ),
    );
    state.addListener(() => phases.add(state.server));

    await state.connect();

    expect(phases, contains(ServerPhase.starting));
    expect(state.server, ServerPhase.unreachable);
  });
}
