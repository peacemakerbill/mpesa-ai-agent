import 'dart:io';
import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:agentx_app/api/api_client.dart' show ChatFile;
import 'package:agentx_app/chat/media_cache.dart';
import 'package:agentx_app/chat/one_app.dart';

const _a = '11111111-1111-1111-1111-111111111111';
const _b = '22222222-2222-2222-2222-222222222222';

ChatFile _file(int size, [String type = 'image/png']) => ChatFile(Uint8List.fromList(List<int>.filled(size, 7)), type);

void main() {
  late Directory dir;
  late MediaCache cache;

  setUp(() async {
    dir = await Directory.systemTemp.createTemp('media_cache_test');
    cache = MediaCache(Directory('${dir.path}/chat-files'));
  });

  tearDown(() async {
    if (await dir.exists()) await dir.delete(recursive: true);
  });

  group('MediaCache', () {
    test('fetches once, then answers from the phone with the same type', () async {
      var fetches = 0;
      Future<ChatFile> fetch() async {
        fetches++;
        return _file(10, 'application/pdf');
      }

      final first = await cache.load(_a, fetch);
      final second = await cache.load(_a, fetch);
      expect(fetches, 1);
      expect(second.bytes, first.bytes);
      expect(second.mimeType, 'application/pdf');
      expect(await cache.fileCount(), 1);
      expect(await cache.usedBytes(), 10);
    });

    test('two loads at the same moment make one request', () async {
      var fetches = 0;
      Future<ChatFile> fetch() async {
        fetches++;
        await Future<void>.delayed(const Duration(milliseconds: 20));
        return _file(5);
      }

      await Future.wait([cache.load(_a, fetch), cache.load(_a, fetch)]);
      expect(fetches, 1);
    });

    test('a failed fetch is passed on and nothing is kept', () async {
      await expectLater(cache.load(_a, () async => throw StateError('offline')), throwsStateError);
      expect(await cache.fileCount(), 0);
      final ok = await cache.load(_a, () async => _file(3));
      expect(ok.bytes.length, 3);
    });

    test('ids that are not ids never touch the disk', () async {
      final f = await cache.load('../../secret', () async => _file(4));
      expect(f.bytes.length, 4);
      expect(await cache.fileCount(), 0);
      expect(await cache.usedBytes(), 0);
    });

    test('the oldest file is removed first when the limit is passed', () async {
      await cache.setLimitBytes(100);
      await cache.load(_a, () async => _file(60));
      await File('${cache.dir.path}/$_a.bin').setLastModified(DateTime.now().subtract(const Duration(hours: 1)));
      await cache.load(_b, () async => _file(60));
      expect(await File('${cache.dir.path}/$_a.bin').exists(), isFalse);
      expect(await File('${cache.dir.path}/$_a.type').exists(), isFalse);
      expect(await File('${cache.dir.path}/$_b.bin').exists(), isTrue);
      expect(await cache.usedBytes(), 60);
    });

    test('limit 0 keeps nothing, and a file bigger than the limit is not kept', () async {
      await cache.setLimitBytes(0);
      await cache.load(_a, () async => _file(10));
      expect(await cache.fileCount(), 0);
      await cache.setLimitBytes(5);
      await cache.load(_b, () async => _file(10));
      expect(await cache.fileCount(), 0);
    });

    test('clear removes files but keeps the chosen limit; remove drops one', () async {
      await cache.setLimitBytes(MediaCache.limitChoices[1]);
      await cache.load(_a, () async => _file(10));
      await cache.load(_b, () async => _file(10));
      await cache.remove(_a);
      expect(await cache.fileCount(), 1);
      await cache.clear();
      expect(await cache.fileCount(), 0);
      expect(await cache.limitBytes(), MediaCache.limitChoices[1]);
    });

    test('the default limit applies until one is chosen', () async {
      expect(await cache.limitBytes(), MediaCache.defaultLimit);
    });
  });

  group('helpers', () {
    test('formatBytes', () {
      expect(formatBytes(0), '0 B');
      expect(formatBytes(2048), '2 KB');
      expect(formatBytes(5 * 1024 * 1024), '5.0 MB');
      expect(formatBytes(3 * 1024 * 1024 * 1024), '3.0 GB');
    });

    test('the USSD link keeps the hash sign', () {
      expect(ussdUri('*334#').toString(), 'tel:*334%23');
    });
  });
}
