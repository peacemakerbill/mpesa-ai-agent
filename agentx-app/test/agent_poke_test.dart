import 'dart:convert';

import 'package:agentx_app/api/api_client.dart';
import 'package:agentx_app/app_state.dart';
import 'package:agentx_app/services/agent_poke.dart';
import 'package:agentx_app/services/secure_store.dart';
import 'package:agentx_app/services/waker.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';

/// Never reads the phone's secure storage.
class _Store extends SecureStore {
  @override
  Future<String?> token() async => null;

  @override
  Future<String?> phone() async => null;

  @override
  Future<String?> theme() async => null;
}

/// Counts the starts and stops, and sends no request.
class _RecordingPoke extends AgentPoke {
  _RecordingPoke(this.events) : super('https://agent.example.com');

  final List<String> events;

  @override
  Future<void> start() {
    events.add('start');
    return Future<void>.value();
  }

  @override
  void stop() => events.add('stop');
}

AgentPoke _poke(MockClient client, {String address = 'https://agent.example.com', Duration giveUpAfter = const Duration(minutes: 5)}) =>
    AgentPoke(address, client: client, pause: Duration.zero, giveUpAfter: giveUpAfter, sleep: (_) async {});

void main() {
  test('with no address, or one that is not http or https, it does nothing', () async {
    var calls = 0;
    final client = MockClient((_) async {
      calls++;
      return http.Response('', 200);
    });

    for (final address in ['', '   ', 'agent-service.local', 'ftp://agent.example.com']) {
      final poke = _poke(client, address: address);
      expect(poke.enabled, isFalse, reason: address);
      await poke.start();
    }

    expect(calls, 0);
  });

  test('it asks the health check of the assistant service, and a slash at the end of the address does not matter', () async {
    final seen = <String>[];
    final client = MockClient((req) async {
      seen.add(req.url.toString());
      return http.Response('{"status":"UP"}', 200);
    });

    await _poke(client, address: 'https://agent.example.com/').start();

    expect(seen, ['https://agent.example.com/actuator/health/liveness']);
  });

  test('the request carries no sign-in and no key', () async {
    late http.BaseRequest seen;
    final client = MockClient((req) async {
      seen = req;
      return http.Response('', 200);
    });

    await _poke(client).start();

    expect(seen.headers.containsKey('Authorization'), isFalse);
    expect(seen.headers.keys.map((k) => k.toLowerCase()), isNot(contains('x-internal-key')));
  });

  test('it stops by itself once the service answers 200', () async {
    var calls = 0;
    final client = MockClient((_) async => ++calls < 3 ? http.Response('', 503) : http.Response('', 200));

    await _poke(client).start();

    expect(calls, 3);
  });

  test('a request that fails, as a browser does when it hides the answer, is not the end', () async {
    var calls = 0;
    final poke = _poke(MockClient((_) async {
      calls++;
      if (calls < 4) throw http.ClientException('blocked');
      return http.Response('', 200);
    }));

    await poke.start();

    expect(calls, 4);
  });

  test('stop ends the asking', () async {
    var calls = 0;
    late AgentPoke poke;
    poke = _poke(MockClient((_) async {
      if (++calls == 3) poke.stop();
      return http.Response('', 503);
    }));

    await poke.start();

    expect(calls, 3);
  });

  test('it gives up by itself after its time, even if nobody stops it', () async {
    var calls = 0;
    final poke = _poke(MockClient((_) async {
      calls++;
      return http.Response('', 503);
    }), giveUpAfter: Duration.zero);

    await poke.start();

    expect(calls, 1);
  });

  test('starting again after a stop sends a new request', () async {
    var calls = 0;
    final poke = _poke(MockClient((_) async {
      calls++;
      return http.Response('', 200);
    }));

    await poke.start();
    poke.stop();
    await poke.start();

    expect(calls, 2);
  });

  test('connect starts the nudge before the server answers and stops it when the server is ready', () async {
    final events = <String>[];
    final client = MockClient((req) async {
      if (req.url.path == '/api/wake') {
        events.add('wake');
        return http.Response(jsonEncode({'api': 'up', 'agent': 'up', 'ready': true}), 200);
      }
      return http.Response(jsonEncode({'ussdMain': '*334#', 'environment': 'staging'}), 200);
    });
    final api = ApiClient(baseUrl: 'http://x.test', client: client);
    final state = AppState(store: _Store(), api: api, waker: Waker(api.wake, pause: Duration.zero), agentPoke: _RecordingPoke(events));

    await state.connect();

    expect(events, ['start', 'wake', 'stop']);
  });

  test('connect also stops the nudge when the server never gets ready', () async {
    final events = <String>[];
    final state = AppState(
      store: _Store(),
      api: ApiClient(baseUrl: 'http://x.test', client: MockClient((_) async => http.Response('', 503))),
      waker: Waker(() async => throw ApiException(503, 'starting'), pause: Duration.zero, giveUpAfter: Duration.zero),
      agentPoke: _RecordingPoke(events),
    );

    await state.connect();

    expect(state.server, ServerPhase.unreachable);
    expect(events, ['start', 'stop']);
  });
}
