import 'dart:io';
import 'dart:typed_data';

import 'package:open_filex/open_filex.dart';
import 'package:path_provider/path_provider.dart';

import 'chat_device.dart';

/// Android, iOS, Linux, macOS and Windows: real files in the temporary folder.
class DeviceFiles {
  const DeviceFiles._();

  /// Where a recording is written, in the temporary folder.
  static Future<String> recordingPath(String ending) async {
    final dir = await getTemporaryDirectory();
    return '${dir.path}/voice-${DateTime.now().millisecondsSinceEpoch}.$ending';
  }

  /// Deletes a temporary file. Never throws, the system clears the folder later anyway.
  static Future<void> discard(String path) async {
    try {
      await File(path).delete();
    } on Exception {
      // already gone
    }
  }

  /// Writes the file to the temporary folder and hands it to the app that opens that kind of file.
  static Future<void> open(Uint8List bytes, String fileName) async {
    final dir = await getTemporaryDirectory();
    final safe = fileName.replaceAll(RegExp(r'[^\w.\- ]'), '_');
    final file = File('${dir.path}/$safe');
    await file.writeAsBytes(bytes, flush: true);
    final result = await OpenFilex.open(file.path);
    if (result.type != ResultType.done) {
      throw ChatDeviceException(
        result.type == ResultType.noAppToOpen ? 'No app on this device can open that file.' : 'The file could not be opened.',
      );
    }
  }
}
