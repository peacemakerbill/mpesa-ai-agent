import 'package:agentx_app/app_state.dart';
import 'package:agentx_app/chat/media_compressor.dart';
import 'package:agentx_app/services/secure_store.dart';
import 'package:flutter_test/flutter_test.dart';

class _Store extends SecureStore {
  _Store(this.saved);

  String? saved;

  @override
  Future<String?> token() async => null;

  @override
  Future<String?> phone() async => null;

  @override
  Future<String?> theme() async => null;

  @override
  Future<String?> compression() async => saved;

  @override
  Future<void> setCompression(String name) async {
    saved = name;
  }
}

void main() {
  test('the saved size setting is used when the app starts', () async {
    final state = AppState(store: _Store('small'));
    await state.start();
    await Future<void>.delayed(Duration.zero);
    expect(state.compression, CompressionLevel.small);
    expect(state.compressor.level, CompressionLevel.small);
  });

  test('with nothing saved the setting is Balanced', () async {
    final state = AppState(store: _Store(null));
    await state.start();
    expect(state.compression, CompressionLevel.balanced);
  });

  test('choosing a size applies at once, is saved, and tells the screens', () async {
    final store = _Store(null);
    final state = AppState(store: store);
    await state.start();
    var told = 0;
    state.addListener(() => told++);
    await state.setCompression(CompressionLevel.original);
    expect(state.compressor.level, CompressionLevel.original);
    expect(store.saved, 'original');
    expect(told, 1);
    await state.setCompression(CompressionLevel.original);
    expect(told, 1);
  });
}
