import 'dart:typed_data';

/// Used only where neither dart:io nor the browser is available. Nothing runs here in the app.
class DeviceFiles {
  const DeviceFiles._();

  static Future<String> recordingPath(String ending) async => throw UnsupportedError('Recording is not available here.');

  static Future<void> discard(String path) async {}

  static Future<void> open(Uint8List bytes, String fileName) async => throw UnsupportedError('Opening files is not available here.');
}
