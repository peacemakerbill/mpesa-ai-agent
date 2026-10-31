import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:ganji_app/api/api_client.dart';
import 'package:ganji_app/models/mpesa_sms.dart';

const action = {
  'id': '44444444-4444-4444-4444-444444444444',
  'type': 'PAY_TILL',
  'status': 'APPROVED',
  'amount': 300,
  'summary': 'Pay KES 300 to Till 123456',
  'details': {'till': '123456'},
  'warnings': [],
  'needsContact': false,
};

void main() {
  test('linking sends the code and device id without a token', () async {
    late http.BaseRequest seen;
    late String body;
    final client = MockClient((req) async {
      seen = req;
      body = req.body;
      return http.Response(jsonEncode({'token': 't1', 'phone': '254700000001', 'expiresAt': '2026-11-29T10:00:00Z'}), 200);
    });
    final api = ApiClient(baseUrl: 'http://x.test', client: client, tokenProvider: () async => 'should-not-be-sent');

    final result = await api.verify(phone: '0700000001', code: '123456', deviceId: 'device12345678');

    expect(result.token, 't1');
    expect(seen.headers.containsKey('Authorization'), isFalse);
    expect(jsonDecode(body)['deviceId'], 'device12345678');
  });

  test('calls after linking carry the bearer token', () async {
    String? auth;
    final client = MockClient((req) async {
      auth = req.headers['Authorization'];
      return http.Response(jsonEncode([action]), 200);
    });
    final api = ApiClient(baseUrl: 'http://x.test', client: client, tokenProvider: () async => 'tok');

    final list = await api.actions();

    expect(auth, 'Bearer tok');
    expect(list.single.summary, 'Pay KES 300 to Till 123456');
  });

  test('without a token the app asks to link instead of calling the server', () async {
    var called = false;
    final client = MockClient((req) async {
      called = true;
      return http.Response('{}', 200);
    });
    final api = ApiClient(baseUrl: 'http://x.test', client: client, tokenProvider: () async => null);

    await expectLater(api.actions(), throwsA(isA<ApiException>().having((e) => e.unauthorized, 'unauthorized', isTrue)));
    expect(called, isFalse);
  });

  test('the server message is shown as it is', () async {
    final client = MockClient((req) async => http.Response(jsonEncode({'message': 'That code is wrong'}), 401));
    final api = ApiClient(baseUrl: 'http://x.test', client: client);

    await expectLater(
      api.verify(phone: '0700000001', code: '000000', deviceId: 'device12345678'),
      throwsA(isA<ApiException>().having((e) => e.message, 'message', 'That code is wrong')),
    );
  });

  test('a server error without JSON gets a friendly message', () async {
    final client = MockClient((req) async => http.Response('<html>bad gateway</html>', 502));
    final api = ApiClient(baseUrl: 'http://x.test', client: client);

    await expectLater(api.config(), throwsA(isA<ApiException>().having((e) => e.message, 'message', isNot(contains('html')))));
  });

  test('reporting sends the outcome and the optional code', () async {
    late Map<String, dynamic> sent;
    final client = MockClient((req) async {
      sent = jsonDecode(req.body) as Map<String, dynamic>;
      return http.Response(jsonEncode({...action, 'status': 'DONE_REPORTED'}), 200);
    });
    final api = ApiClient(baseUrl: 'http://x.test', client: client, tokenProvider: () async => 'tok');

    final done = await api.report('44444444-4444-4444-4444-444444444444', done: true, receipt: 'SGL1234567');

    expect(sent, {'outcome': 'DONE', 'receipt': 'SGL1234567'});
    expect(done.status, 'DONE_REPORTED');
  });

  test('a network failure is reported plainly', () async {
    final client = MockClient((req) async => throw http.ClientException('no route'));
    final api = ApiClient(baseUrl: 'http://x.test', client: client);

    await expectLater(api.config(), throwsA(isA<ApiException>().having((e) => e.status, 'status', 0)));
  });

  test('message evidence sends the parsed fields and never the text', () async {
    late Map<String, dynamic> sent;
    final client = MockClient((req) async {
      sent = jsonDecode(req.body) as Map<String, dynamic>;
      return http.Response(jsonEncode({...action, 'status': 'DONE_REPORTED', 'verification': 'SMS_MATCHED'}), 200);
    });
    final api = ApiClient(baseUrl: 'http://x.test', client: client, tokenProvider: () async => 'tok');
    final sms = MpesaSms(
        code: 'SGL1234567', kind: SmsKind.bill, amount: 1200, receivedAt: DateTime.fromMillisecondsSinceEpoch(1000), payee: 'KPLC', account: '123456');

    final result = await api.smsEvidence('44444444-4444-4444-4444-444444444444', sms);

    expect(sent, {'code': 'SGL1234567', 'kind': 'BILL', 'amount': 1200, 'payee': 'KPLC', 'account': '123456', 'smsAt': 1000});
    expect(result.verification, 'SMS_MATCHED');
    expect(result.smsBacked, isTrue);
  });

  group('sign-in code', () {
    test('asking for a code needs no token and reads the test code when the server gives one', () async {
      late http.BaseRequest seen;
      final client = MockClient((req) async {
        seen = req;
        return http.Response(jsonEncode({'expiresAt': '2026-10-30T10:00:00Z', 'devCode': '123456'}), 200);
      });
      final api = ApiClient(baseUrl: 'http://x.test', client: client, tokenProvider: () async => 'should-not-be-sent');

      final r = await api.requestCode('0712345678');

      expect(seen.url.path, '/api/auth/request-code');
      expect(seen.headers.containsKey('Authorization'), isFalse);
      expect(jsonDecode((seen as http.Request).body), {'phone': '0712345678'});
      expect(r.devCode, '123456');
    });

    test('a normal server gives no code back', () async {
      final client = MockClient((req) async => http.Response(jsonEncode({'expiresAt': '2026-10-30T10:00:00Z', 'devCode': null}), 200));
      final api = ApiClient(baseUrl: 'http://x.test', client: client);
      expect((await api.requestCode('0712345678')).devCode, isNull);
    });

    test('too many tries gets a plain message', () async {
      final client = MockClient((req) async => http.Response('', 429));
      final api = ApiClient(baseUrl: 'http://x.test', client: client);
      await expectLater(api.requestCode('0712345678'), throwsA(isA<ApiException>().having((e) => e.message, 'message', contains('wait'))));
    });
  });

  group('chat', () {
    test('reading asks for messages after the last id and parses the page', () async {
      late Uri url;
      String? auth;
      final client = MockClient((req) async {
        url = req.url;
        auth = req.headers['Authorization'];
        return http.Response(
            jsonEncode({'messages': [{'id': 8, 'dir': 'out', 'kind': 'TEXT', 'at': 1, 'text': 'hi'}], 'typing': true, 'lastId': 8}), 200);
      });
      final api = ApiClient(baseUrl: 'http://x.test', client: client, tokenProvider: () async => 'tok');

      final page = await api.chatMessages(after: 7, limit: 50);

      expect(url.path, '/api/chat/messages');
      expect(url.queryParameters, {'after': '7', 'limit': '50'});
      expect(auth, 'Bearer tok');
      expect(page.messages.single.text, 'hi');
      expect(page.typing, isTrue);
    });

    test('sending posts the fields as JSON', () async {
      late http.Request seen;
      final client = MockClient((req) async {
        seen = req;
        return http.Response(jsonEncode({'messageId': 'x'}), 202);
      });
      final api = ApiClient(baseUrl: 'http://x.test', client: client, tokenProvider: () async => 'tok');

      await api.sendChat({'kind': 'TEXT', 'text': 'hi', 'clientMessageId': 'c1'});

      expect(seen.method, 'POST');
      expect(seen.headers['Content-Type'], contains('application/json'));
      expect(jsonDecode(seen.body), {'kind': 'TEXT', 'text': 'hi', 'clientMessageId': 'c1'});
    });

    test('an upload sends the raw bytes with the file type and a plain ASCII name', () async {
      late http.Request seen;
      final client = MockClient((req) async {
        seen = req;
        return http.Response(jsonEncode({'mediaId': 'm-1', 'mimeType': 'image/png', 'size': 3}), 201);
      });
      final api = ApiClient(baseUrl: 'http://x.test', client: client, tokenProvider: () async => 'tok');

      final id = await api.uploadChatFile(Uint8List.fromList([1, 2, 3]), mimeType: 'image/png', fileName: 'ujumbe wa Mama é.png');

      expect(id, 'm-1');
      expect(seen.bodyBytes, [1, 2, 3]);
      expect(seen.headers['Content-Type'], startsWith('image/png'));
      expect(seen.headers['X-File-Name'], 'ujumbe wa Mama _.png');
    });

    test('a file comes back with its type', () async {
      final client = MockClient((req) async => http.Response.bytes([9, 8, 7], 200, headers: {'content-type': 'audio/mp4; charset=binary'}));
      final api = ApiClient(baseUrl: 'http://x.test', client: client, tokenProvider: () async => 'tok');

      final f = await api.chatFile('m-1');

      expect(f.bytes, [9, 8, 7]);
      expect(f.mimeType, 'audio/mp4');
    });

    test('clearing the chat is a DELETE', () async {
      late http.Request seen;
      final client = MockClient((req) async {
        seen = req;
        return http.Response('', 204);
      });
      final api = ApiClient(baseUrl: 'http://x.test', client: client, tokenProvider: () async => 'tok');
      await api.clearChat();
      expect(seen.method, 'DELETE');
      expect(seen.url.path, '/api/chat/messages');
    });

    test('a 401 is reported as unauthorized so the app can ask for a new sign-in', () async {
      final client = MockClient((req) async => http.Response('', 401));
      final api = ApiClient(baseUrl: 'http://x.test', client: client, tokenProvider: () async => 'tok');
      await expectLater(api.chatMessages(), throwsA(isA<ApiException>().having((e) => e.unauthorized, 'unauthorized', isTrue)));
    });
  });
}
