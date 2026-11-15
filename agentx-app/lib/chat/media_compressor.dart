import 'dart:typed_data';

import 'package:flutter/foundation.dart' show TargetPlatform, compute, defaultTargetPlatform, kIsWeb;
import 'package:image/image.dart' as img;
import 'package:image_picker/image_picker.dart' show XFile;
import 'package:video_compress/video_compress.dart';

/// How hard photos and videos are squeezed before they are sent. Chosen in Settings, kept on this device.
enum CompressionLevel { original, balanced, small }

extension CompressionLevelX on CompressionLevel {
  String get label => switch (this) {
        CompressionLevel.original => 'Original',
        CompressionLevel.balanced => 'Balanced',
        CompressionLevel.small => 'Small',
      };

  String get hint => switch (this) {
        CompressionLevel.original => 'Send photos and videos exactly as they are. Large files can be refused by the server and use more data.',
        CompressionLevel.balanced => 'Photos up to 1920 pixels on the long side. Videos at medium quality. Good for receipts and screenshots.',
        CompressionLevel.small => 'Photos up to 1280 pixels on the long side. Videos at low quality. Uses the least data.',
      };

  /// Longest side of a photo, in pixels. 0 means leave it alone.
  int get longestEdge => switch (this) {
        CompressionLevel.original => 0,
        CompressionLevel.balanced => 1920,
        CompressionLevel.small => 1280,
      };

  int get jpegQuality => switch (this) {
        CompressionLevel.original => 100,
        CompressionLevel.balanced => 82,
        CompressionLevel.small => 68,
      };

  /// The video size the camera screen records at (web and desktop). Phones use [videoQuality] after recording.
  int get videoBitrate => switch (this) {
        CompressionLevel.original => 8000000,
        CompressionLevel.balanced => 2500000,
        CompressionLevel.small => 1000000,
      };

  VideoQuality get videoQuality => switch (this) {
        CompressionLevel.original => VideoQuality.DefaultQuality,
        CompressionLevel.balanced => VideoQuality.MediumQuality,
        CompressionLevel.small => VideoQuality.LowQuality,
      };

  static CompressionLevel parse(String? name) {
    for (final level in CompressionLevel.values) {
      if (level.name == name) return level;
    }
    return CompressionLevel.balanced;
  }
}

/// A photo (or other file) after compression: what to send, and whether it differs from what was picked.
class CompressedMedia {
  const CompressedMedia({required this.bytes, required this.mimeType, required this.fileName, required this.changed});

  final Uint8List bytes;
  final String mimeType;
  final String fileName;
  final bool changed;
}

/// Input of [shrinkImage]. Plain data, so it can cross to another isolate.
class ImageJob {
  const ImageJob({required this.bytes, required this.longestEdge, required this.quality, this.maxBytes = 4500000});

  final Uint8List bytes;
  final int longestEdge;
  final int quality;

  /// The server reads photos up to 5 MB, so stay under it even when the first pass is bigger.
  final int maxBytes;
}

class ImageResult {
  const ImageResult(this.bytes, {required this.png});

  final Uint8List bytes;
  final bool png;
}

/// Decodes a photo, applies its camera rotation, scales it down and encodes it again. Returns null when the bytes are
/// not a picture this package can read (HEIC, for one), so the caller keeps the original. A photo with transparency stays
/// a PNG, everything else becomes a JPEG. Top level so it can run in another isolate.
ImageResult? shrinkImage(ImageJob job) {
  img.Image? decoded;
  try {
    decoded = img.decodeImage(job.bytes);
  } on Object {
    return null;
  }
  if (decoded == null) return null;
  var image = img.bakeOrientation(decoded);
  final edge = job.longestEdge;
  if (edge > 0 && (image.width > edge || image.height > edge)) {
    image = image.width >= image.height
        ? img.copyResize(image, width: edge, interpolation: img.Interpolation.average)
        : img.copyResize(image, height: edge, interpolation: img.Interpolation.average);
  }
  if (image.hasAlpha) return ImageResult(Uint8List.fromList(img.encodePng(image)), png: true);
  var quality = job.quality;
  var out = Uint8List.fromList(img.encodeJpg(image, quality: quality));
  while (out.length > job.maxBytes && quality > 40) {
    quality -= 12;
    out = Uint8List.fromList(img.encodeJpg(image, quality: quality));
  }
  return ImageResult(out, png: false);
}

/// Makes photos and videos smaller before they are uploaded. It never makes a file worse than it was: when the result is
/// not smaller, or anything fails, the original goes through.
class MediaCompressor {
  MediaCompressor({this.level = CompressionLevel.balanced});

  CompressionLevel level;

  /// Photos smaller than this are not worth another round of compression.
  static const int skipBelowBytes = 200 * 1024;

  Future<CompressedMedia> image(Uint8List bytes, {required String mimeType, required String fileName}) async {
    final same = CompressedMedia(bytes: bytes, mimeType: mimeType, fileName: fileName, changed: false);
    if (level == CompressionLevel.original) return same;
    if (!mimeType.startsWith('image/') || mimeType == 'image/gif' || mimeType == 'image/svg+xml') return same;
    if (bytes.length < skipBelowBytes) return same;
    try {
      final result = await compute(shrinkImage, ImageJob(bytes: bytes, longestEdge: level.longestEdge, quality: level.jpegQuality));
      if (result == null || result.bytes.length >= bytes.length) return same;
      return CompressedMedia(
        bytes: result.bytes,
        mimeType: result.png ? 'image/png' : 'image/jpeg',
        fileName: _withEnding(fileName, result.png ? 'png' : 'jpg'),
        changed: true,
      );
    } on Object {
      return same;
    }
  }

  /// Phones only (Android and iOS). Elsewhere the video is sent as recorded; the camera screen already records at the
  /// size of the chosen level.
  Future<({XFile file, bool changed})> video(XFile file) async {
    if (level == CompressionLevel.original || !supportsVideoCompression) return (file: file, changed: false);
    try {
      final Object? info = await VideoCompress.compressVideo(file.path, quality: level.videoQuality, deleteOrigin: false, includeAudio: true);
      final path = info is MediaInfo ? info.path : null;
      if (path == null || path.isEmpty) return (file: file, changed: false);
      final smaller = XFile(path);
      if (await smaller.length() >= await file.length()) return (file: file, changed: false);
      return (file: smaller, changed: true);
    } on Object {
      return (file: file, changed: false);
    }
  }

  /// Frees the temporary copies the video compressor leaves behind.
  Future<void> clearVideoCache() async {
    if (!supportsVideoCompression) return;
    try {
      await VideoCompress.deleteAllCache();
    } on Object {
      // a cache the phone clears later
    }
  }

  static bool get supportsVideoCompression =>
      !kIsWeb && (defaultTargetPlatform == TargetPlatform.android || defaultTargetPlatform == TargetPlatform.iOS);
}

String _withEnding(String fileName, String ending) {
  final dot = fileName.lastIndexOf('.');
  final stem = dot > 0 ? fileName.substring(0, dot) : fileName;
  return '$stem.$ending';
}
