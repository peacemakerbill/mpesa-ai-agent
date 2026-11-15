import 'dart:typed_data';

import 'package:agentx_app/chat/media_compressor.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:image/image.dart' as img;

/// A busy picture, so JPEG cannot squeeze it to nothing before the code under test does.
Uint8List _photo(int width, int height, {int quality = 95}) {
  final image = img.Image(width: width, height: height);
  for (var y = 0; y < height; y++) {
    for (var x = 0; x < width; x++) {
      image.setPixelRgb(x, y, (x * 7 + y * 3) & 255, (x * y) & 255, (x + y * 5) & 255);
    }
  }
  return Uint8List.fromList(img.encodeJpg(image, quality: quality));
}

void main() {
  group('shrinkImage', () {
    test('a wide photo is scaled to the longest side and gets smaller', () {
      final original = _photo(2000, 1400);
      final result = shrinkImage(ImageJob(bytes: original, longestEdge: 1280, quality: 70));
      expect(result, isNotNull);
      expect(result!.png, isFalse);
      final decoded = img.decodeImage(result.bytes)!;
      expect(decoded.width, 1280);
      expect(decoded.height, 896);
      expect(result.bytes.length, lessThan(original.length));
    });

    test('a tall photo is limited by its height', () {
      final result = shrinkImage(ImageJob(bytes: _photo(1000, 1600), longestEdge: 800, quality: 70))!;
      final decoded = img.decodeImage(result.bytes)!;
      expect(decoded.height, 800);
      expect(decoded.width, 500);
    });

    test('a photo that is already small enough is never made bigger in pixels', () {
      final result = shrinkImage(ImageJob(bytes: _photo(640, 480), longestEdge: 1920, quality: 82))!;
      final decoded = img.decodeImage(result.bytes)!;
      expect(decoded.width, 640);
      expect(decoded.height, 480);
    });

    test('a picture with transparency stays a PNG', () {
      final image = img.Image(width: 300, height: 200, numChannels: 4);
      img.fill(image, color: img.ColorRgba8(10, 120, 60, 128));
      final result = shrinkImage(ImageJob(bytes: Uint8List.fromList(img.encodePng(image)), longestEdge: 100, quality: 70))!;
      expect(result.png, isTrue);
      expect(img.decodeImage(result.bytes)!.width, 100);
    });

    test('bytes that are not a picture give null, so the original is kept', () {
      expect(shrinkImage(ImageJob(bytes: Uint8List.fromList([1, 2, 3, 4, 5]), longestEdge: 1920, quality: 82)), isNull);
    });
  });

  group('MediaCompressor', () {
    test('Original sends the bytes as they are', () async {
      final bytes = _photo(1600, 1200);
      final out = await MediaCompressor(level: CompressionLevel.original).image(bytes, mimeType: 'image/jpeg', fileName: 'a.jpg');
      expect(out.changed, isFalse);
      expect(identical(out.bytes, bytes), isTrue);
    });

    test('a big photo is made smaller, and a PNG name becomes a JPEG name', () async {
      final bytes = _photo(2600, 1800);
      final out = await MediaCompressor(level: CompressionLevel.balanced).image(bytes, mimeType: 'image/png', fileName: 'Screenshot 1.png');
      expect(out.changed, isTrue);
      expect(out.mimeType, 'image/jpeg');
      expect(out.fileName, 'Screenshot 1.jpg');
      expect(out.bytes.length, lessThan(bytes.length));
    });

    test('small files, GIFs and non-pictures are left alone', () async {
      final compressor = MediaCompressor(level: CompressionLevel.small);
      final tiny = Uint8List.fromList(List.filled(1000, 7));
      expect((await compressor.image(tiny, mimeType: 'image/jpeg', fileName: 'a.jpg')).changed, isFalse);
      final big = Uint8List.fromList(List.filled(MediaCompressor.skipBelowBytes + 10, 7));
      expect((await compressor.image(big, mimeType: 'image/gif', fileName: 'a.gif')).changed, isFalse);
      expect((await compressor.image(big, mimeType: 'application/pdf', fileName: 'a.pdf')).changed, isFalse);
    });

    test('a file this package cannot read goes through unchanged', () async {
      final junk = Uint8List.fromList(List.filled(MediaCompressor.skipBelowBytes + 10, 9));
      final out = await MediaCompressor(level: CompressionLevel.balanced).image(junk, mimeType: 'image/heic', fileName: 'a.heic');
      expect(out.changed, isFalse);
      expect(out.fileName, 'a.heic');
    });
  });

  group('CompressionLevel', () {
    test('is read from its saved name, and Balanced is the default', () {
      expect(CompressionLevelX.parse('small'), CompressionLevel.small);
      expect(CompressionLevelX.parse('original'), CompressionLevel.original);
      expect(CompressionLevelX.parse('nonsense'), CompressionLevel.balanced);
      expect(CompressionLevelX.parse(null), CompressionLevel.balanced);
    });

    test('smaller levels use a shorter side and a lower bitrate', () {
      expect(CompressionLevel.small.longestEdge, lessThan(CompressionLevel.balanced.longestEdge));
      expect(CompressionLevel.small.videoBitrate, lessThan(CompressionLevel.balanced.videoBitrate));
      expect(CompressionLevel.original.longestEdge, 0);
    });
  });
}
