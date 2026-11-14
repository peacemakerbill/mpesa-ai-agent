import 'package:agentx_app/services/code_autofill.dart';
import 'package:agentx_app/services/code_reader.dart';
import 'package:flutter_test/flutter_test.dart';

import 'support/fake_code_reader.dart';

void main() {
  group('auto fill', () {
    test('listening starts first and the app hash is handed back for the request', () async {
      final reader = FakeCodeReader();
      final auto = CodeAutoFill(reader);

      final hash = await auto.start((_) {});

      expect(hash, 'FA+9qCX9VSu');
      expect(reader.listens, 1);
    });

    test('a code from the message is filled in once', () async {
      final reader = FakeCodeReader();
      final auto = CodeAutoFill(reader);
      final filled = <String>[];
      await auto.start(filled.add);

      reader.arrives('482913');
      reader.arrives('111111');

      expect(filled, ['482913']);
    });

    test('a code the server shows is filled in after a short moment, and only once', () async {
      final auto = CodeAutoFill(FakeCodeReader(isSupported: false), showFor: const Duration(milliseconds: 20));
      final filled = <String>[];
      await auto.start(filled.add);

      auto.shown('123456');
      expect(filled, isEmpty);
      await Future<void>.delayed(const Duration(milliseconds: 80));

      expect(filled, ['123456']);
    });

    test('a message that comes first wins, and the shown code is not tried after it', () async {
      final reader = FakeCodeReader();
      final auto = CodeAutoFill(reader, showFor: const Duration(milliseconds: 20));
      final filled = <String>[];
      await auto.start(filled.add);

      auto.shown('123456');
      reader.arrives('654321');
      await Future<void>.delayed(const Duration(milliseconds: 80));

      expect(filled, ['654321']);
    });

    test('asking again starts fresh', () async {
      final reader = FakeCodeReader();
      final auto = CodeAutoFill(reader);
      final filled = <String>[];
      await auto.start(filled.add);
      reader.arrives('111111');

      await auto.start(filled.add);
      reader.arrives('222222');

      expect(filled, ['111111', '222222']);
    });

    test('stopping cancels a code that was waiting to be filled in', () async {
      final reader = FakeCodeReader();
      final auto = CodeAutoFill(reader, showFor: const Duration(milliseconds: 20));
      final filled = <String>[];
      await auto.start(filled.add);
      auto.shown('123456');

      auto.stop();
      await Future<void>.delayed(const Duration(milliseconds: 80));

      expect(filled, isEmpty);
      expect(reader.stops, 1);
    });

    test('a phone that cannot read messages gives no hash and does not get in the way', () async {
      final auto = CodeAutoFill(FakeCodeReader(isSupported: false));

      expect(await auto.start((_) {}), isNull);
    });

    test('a reader that never answers does not hold up asking for the code', () async {
      final auto = CodeAutoFill(FakeCodeReader(hang: true), startTimeout: const Duration(milliseconds: 30));

      expect(await auto.start((_) {}), isNull);
    });
  });

  group('the phone reader', () {
    test('off Android it does nothing and never fails', () async {
      final reader = DeviceCodeReader();
      // The tests run on a computer, not on an Android phone.
      expect(reader.supported, isFalse);
      expect(await reader.listen((_) {}), isNull);
      await reader.stop();
    });
  });
}
