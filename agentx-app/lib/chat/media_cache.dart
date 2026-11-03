import 'dart:async';
import 'dart:io';

import 'package:path_provider/path_provider.dart';

import '../api/api_client.dart' show ChatFile;

final RegExp _mediaId = RegExp(r'^[0-9a-fA-F-]{36}$');

/// "12.4 MB", "830 KB", "0 B".
String formatBytes(int bytes) {
  if (bytes < 1024) return '$bytes B';
  if (bytes < 1024 * 1024) return '${(bytes / 1024).round()} KB';
  if (bytes < 1024 * 1024 * 1024) return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
  return '${(bytes / (1024 * 1024 * 1024)).toStringAsFixed(1)} GB';
}

/// Chat files kept on this phone, so a photo or a receipt opens at once and without data after the first time.
///
/// The cache is a convenience, never the only copy: if anything goes wrong reading or writing, the file is fetched again
/// from the server. It lives in the app's private storage, is trimmed oldest first when it passes the limit, and is
/// emptied when the person signs out. Files are not encrypted on top of the phone's own storage protection.
class MediaCache {
  MediaCache(this.dir);

  static const int defaultLimit = 200 * 1024 * 1024;
  static const List<int> limitChoices = [0, 50 * 1024 * 1024, 200 * 1024 * 1024, 500 * 1024 * 1024, 1024 * 1024 * 1024];

  final Directory dir;
  final Map<String, Future<ChatFile>> _inFlight = {};

  static MediaCache? _shared;

  /// The cache the app uses.
  static Future<MediaCache> shared() async {
    final existing = _shared;
    if (existing != null) return existing;
    final base = await getApplicationSupportDirectory();
    return _shared = MediaCache(Directory('${base.path}/chat-files'));
  }

  /// The file from this phone when it is here, otherwise from [fetch], which is then kept.
  Future<ChatFile> load(String id, Future<ChatFile> Function() fetch) {
    if (!_mediaId.hasMatch(id)) return fetch();
    return _inFlight.putIfAbsent(id, () async {
      try {
        final hit = await _read(id);
        if (hit != null) return hit;
        final file = await fetch();
        await _write(id, file);
        return file;
      } finally {
        unawaited(Future<void>.delayed(Duration.zero, () => _inFlight.remove(id)));
      }
    });
  }

  Future<ChatFile?> _read(String id) async {
    try {
      final bin = File('${dir.path}/$id.bin');
      final type = File('${dir.path}/$id.type');
      if (!await bin.exists() || !await type.exists()) return null;
      final bytes = await bin.readAsBytes();
      final mime = (await type.readAsString()).trim();
      await bin.setLastModified(DateTime.now());   // so the least recently opened goes first when trimming
      return ChatFile(bytes, mime.isEmpty ? 'application/octet-stream' : mime);
    } on FileSystemException {
      return null;
    }
  }

  Future<void> _write(String id, ChatFile file) async {
    try {
      final limit = await limitBytes();
      if (limit == 0 || file.bytes.length > limit) return;
      await dir.create(recursive: true);
      await File('${dir.path}/$id.type').writeAsString(file.mimeType);
      final tmp = File('${dir.path}/$id.tmp');
      await tmp.writeAsBytes(file.bytes, flush: true);
      await tmp.rename('${dir.path}/$id.bin');   // the .bin appears last, so a .bin always has its .type
      await _trim(limit);
    } on FileSystemException {
      // a full disk or a missing folder must never stop the chat from showing the file
    }
  }

  /// Deletes the oldest files until the cache fits the limit.
  Future<void> _trim(int limit) async {
    final files = await _binFiles();
    var total = files.fold<int>(0, (sum, f) => sum + f.size);
    files.sort((a, b) => a.modified.compareTo(b.modified));
    for (final f in files) {
      if (total <= limit) break;
      await _delete(f.id);
      total -= f.size;
    }
  }

  Future<List<({String id, int size, DateTime modified})>> _binFiles() async {
    final out = <({String id, int size, DateTime modified})>[];
    if (!await dir.exists()) return out;
    await for (final e in dir.list()) {
      if (e is! File || !e.path.endsWith('.bin')) continue;
      final name = e.uri.pathSegments.last;
      final id = name.substring(0, name.length - 4);
      if (!_mediaId.hasMatch(id)) continue;
      try {
        final stat = await e.stat();
        out.add((id: id, size: stat.size, modified: stat.modified));
      } on FileSystemException {
        continue;
      }
    }
    return out;
  }

  Future<void> _delete(String id) async {
    for (final ext in ['bin', 'type', 'tmp']) {
      try {
        final f = File('${dir.path}/$id.$ext');
        if (await f.exists()) await f.delete();
      } on FileSystemException {
        continue;
      }
    }
  }

  /// Forget one file, for example after it was deleted from the server.
  Future<void> remove(String id) async {
    if (_mediaId.hasMatch(id)) await _delete(id);
  }

  /// How much of the phone's storage the chat files use.
  Future<int> usedBytes() async => (await _binFiles()).fold<int>(0, (sum, f) => sum + f.size);

  Future<int> fileCount() async => (await _binFiles()).length;

  /// Removes every cached file. The server copies stay.
  Future<void> clear() async {
    _inFlight.clear();
    try {
      if (await dir.exists()) {
        await for (final e in dir.list()) {
          if (e is File && !e.path.endsWith('limit.txt')) await e.delete();
        }
      }
    } on FileSystemException {
      // nothing more to do
    }
  }

  /// The most this cache may use. 0 means files are not kept on the phone.
  Future<int> limitBytes() async {
    try {
      final f = File('${dir.path}/limit.txt');
      if (await f.exists()) {
        final n = int.tryParse((await f.readAsString()).trim());
        if (n != null && n >= 0) return n;
      }
    } on FileSystemException {
      // use the default
    }
    return defaultLimit;
  }

  Future<void> setLimitBytes(int bytes) async {
    final limit = bytes < 0 ? 0 : bytes;
    await dir.create(recursive: true);
    await File('${dir.path}/limit.txt').writeAsString('$limit');
    await _trim(limit);
  }
}
