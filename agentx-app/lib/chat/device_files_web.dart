import 'dart:js_interop';
import 'dart:typed_data';

import 'package:web/web.dart' as web;

import 'chat_device.dart';

/// The browser: there are no folders, so a recording lives at a blob address and an opened file is downloaded.
class DeviceFiles {
  const DeviceFiles._();

  /// The browser decides where a recording lives, the path is ignored.
  static Future<String> recordingPath(String ending) async => '';

  /// Lets the browser free the memory behind a recording.
  static Future<void> discard(String path) async {
    if (!path.startsWith('blob:')) return;
    try {
      web.URL.revokeObjectURL(path);
    } on Object {
      // already released
    }
  }

  /// Offers the file as a download. A browser has no "open with", so the person opens it from the downloads list.
  static Future<void> open(Uint8List bytes, String fileName) async {
    final blob = web.Blob([bytes.toJS].toJS, web.BlobPropertyBag(type: mimeFor(fileName)));
    final url = web.URL.createObjectURL(blob);
    try {
      final link = web.document.createElement('a') as web.HTMLAnchorElement;
      link.href = url;
      link.download = fileName.replaceAll(RegExp(r'[\\/:*?"<>|]'), '_');
      link.click();
    } on Object {
      throw const ChatDeviceException('The browser did not let the file download.');
    } finally {
      // Give the download time to start before the address is dropped.
      Future<void>.delayed(const Duration(seconds: 30), () => web.URL.revokeObjectURL(url));
    }
  }
}
