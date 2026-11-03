import 'dart:typed_data';

import 'chat_controller.dart';

/// Something the phone could not do for the chat, with a sentence that can be shown as it is.
class ChatDeviceException implements Exception {
  const ChatDeviceException(this.message);

  final String message;

  @override
  String toString() => message;
}

class GeoPoint {
  const GeoPoint(this.latitude, this.longitude);

  final double latitude;
  final double longitude;
}

/// Everything the chat needs from the phone itself: the gallery, the camera, files, the microphone, the
/// position and sound playback. The real one is in platform_chat_device.dart, tests use a fake.
abstract class ChatDevice {
  Future<ChatAttachment?> photoFromGallery();

  Future<ChatAttachment?> photoFromCamera();

  Future<ChatAttachment?> videoFromGallery();

  Future<ChatAttachment?> videoFromCamera();

  Future<ChatAttachment?> document();

  Future<GeoPoint?> location();

  /// Starts the microphone. Throws [ChatDeviceException] when there is no permission.
  Future<void> startVoice();

  /// Stops and returns the recording, or null when it was too short to keep.
  Future<ChatAttachment?> stopVoice();

  Future<void> cancelVoice();

  /// Plays a sound and completes when it ends or is stopped.
  Future<void> play(Uint8List bytes, String mimeType);

  Future<void> stopPlayback();

  /// Saves a received file where the phone can open it, then opens it.
  Future<void> open(Uint8List bytes, String fileName);
}

/// Largest file the app reads into memory for a chat message.
const int maxChatFileBytes = 30 * 1024 * 1024;

const Map<String, String> _types = {
  'jpg': 'image/jpeg',
  'jpeg': 'image/jpeg',
  'png': 'image/png',
  'gif': 'image/gif',
  'webp': 'image/webp',
  'heic': 'image/heic',
  'mp4': 'video/mp4',
  'mov': 'video/quicktime',
  '3gp': 'video/3gpp',
  'webm': 'video/webm',
  'm4a': 'audio/mp4',
  'aac': 'audio/aac',
  'mp3': 'audio/mpeg',
  'wav': 'audio/wav',
  'ogg': 'audio/ogg',
  'pdf': 'application/pdf',
  'txt': 'text/plain',
  'csv': 'text/csv',
  'vcf': 'text/vcard',
  'doc': 'application/msword',
  'docx': 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'xls': 'application/vnd.ms-excel',
  'xlsx': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
};

/// The MIME type for a file name, from its ending. Unknown endings are application/octet-stream.
String mimeFor(String fileName, {String? reported}) {
  if (reported != null && reported.isNotEmpty && reported != 'application/octet-stream') return reported;
  final dot = fileName.lastIndexOf('.');
  if (dot < 0 || dot == fileName.length - 1) return 'application/octet-stream';
  return _types[fileName.substring(dot + 1).toLowerCase()] ?? 'application/octet-stream';
}

/// A file name that suits a MIME type, for received files that arrive without a name.
String extensionFor(String mimeType) {
  for (final e in _types.entries) {
    if (e.value == mimeType.toLowerCase()) return e.key;
  }
  return 'bin';
}
