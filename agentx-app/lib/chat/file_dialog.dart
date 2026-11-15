import 'dart:typed_data';

import 'package:file_picker/file_picker.dart';
import 'package:file_selector/file_selector.dart' show XTypeGroup, openFile;
import 'package:flutter/foundation.dart' show TargetPlatform, defaultTargetPlatform, kIsWeb;

/// A file the person chose, read into memory.
typedef ChosenFile = ({String name, Uint8List bytes, String? mimeType});

/// Shows the system "choose a file" dialog and reads the file. Returns null when the person cancels.
///
/// [extensions] (without dots, for example `['csv', 'txt']`) limits what can be chosen, or null allows any file.
///
/// Phones use `file_picker`. The web, Linux, Windows and macOS use `file_selector`, which uses the platform's own dialog:
/// on Linux `file_picker` needs the zenity or kdialog programs installed, and this one does not.
Future<ChosenFile?> chooseFile({List<String>? extensions, String label = 'Files'}) async {
  final onPhone = !kIsWeb && (defaultTargetPlatform == TargetPlatform.android || defaultTargetPlatform == TargetPlatform.iOS);
  if (onPhone) {
    final result = await FilePicker.platform.pickFiles(
      type: extensions == null ? FileType.any : FileType.custom,
      allowedExtensions: extensions,
      withData: true,
    );
    final file = result?.files.firstOrNull;
    final bytes = file?.bytes;
    if (file == null || bytes == null) return null;
    return (name: file.name, bytes: bytes, mimeType: null);
  }
  final groups = extensions == null ? const <XTypeGroup>[] : <XTypeGroup>[XTypeGroup(label: label, extensions: extensions)];
  final picked = await openFile(acceptedTypeGroups: groups);
  if (picked == null) return null;
  return (name: picked.name, bytes: await picked.readAsBytes(), mimeType: picked.mimeType);
}
