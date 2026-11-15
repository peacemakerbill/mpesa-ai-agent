import 'dart:async';
import 'dart:typed_data';

import 'package:audioplayers/audioplayers.dart';
import 'package:flutter/foundation.dart' show TargetPlatform, ValueListenable, ValueNotifier, defaultTargetPlatform, kIsWeb;
import 'package:flutter/widgets.dart' show GlobalKey, NavigatorState;
import 'package:geolocator/geolocator.dart';
import 'package:image_picker/image_picker.dart';
import 'package:record/record.dart';

import 'camera_capture_screen.dart';
import 'chat_controller.dart';
import 'chat_device.dart';
import 'device_files.dart';
import 'file_dialog.dart';
import 'media_compressor.dart';

/// The real device behind [ChatDevice]: a phone, a computer or a browser. Needs the permissions listed in
/// docs/PLATFORM_SETUP.md.
///
/// Android and iOS use the phone's own camera and file chooser. The web, Linux, Windows and macOS have no system camera
/// picker, so the in-app camera screen is used there, and documents come from the system file dialog.
class PlatformChatDevice implements ChatDevice {
  PlatformChatDevice({this.navigatorKey, MediaCompressor? compressor}) : compressor = compressor ?? MediaCompressor();

  /// Where the in-app camera screen is shown. Without it the camera buttons say the camera cannot open.
  final GlobalKey<NavigatorState>? navigatorKey;

  /// Makes photos and videos smaller before they are sent. The level is set in Settings.
  final MediaCompressor compressor;

  final ImagePicker _images = ImagePicker();
  // Created on first use, so building the app state does not need the audio plugins (and tests need no device).
  late final AudioRecorder _recorder = AudioRecorder();
  late final AudioPlayer _player = AudioPlayer();
  final ValueNotifier<String?> _working = ValueNotifier<String?>(null);
  DateTime? _recordingStarted;
  String? _recordingPath;
  _VoiceFormat? _recordingFormat;
  Completer<void>? _playing;
  StreamSubscription<void>? _completeSub;

  @override
  ValueListenable<String?> get working => _working;

  static bool get _onPhone => !kIsWeb && (defaultTargetPlatform == TargetPlatform.android || defaultTargetPlatform == TargetPlatform.iOS);

  static bool get _onDesktop =>
      !kIsWeb &&
      (defaultTargetPlatform == TargetPlatform.linux || defaultTargetPlatform == TargetPlatform.windows || defaultTargetPlatform == TargetPlatform.macOS);

  static const ChatDeviceException _tooBig = ChatDeviceException('That file is larger than 30 MB. Choose a smaller one, or use the Small setting in Settings.');

  /// Turns what a plugin throws into a sentence that says what to do.
  Future<T> _guard<T>(Future<T> Function() body, String what) async {
    try {
      return await body();
    } on ChatDeviceException {
      rethrow;
    } on Object catch (e) {
      final text = e.toString().toLowerCase();
      if (text.contains('denied') || text.contains('permission') || text.contains('notallowed')) {
        throw ChatDeviceException('The app is not allowed to use the $what. Allow it in the device settings (in a browser, from the lock icon next to the address) and try again.');
      }
      throw ChatDeviceException('The $what could not be used. $e');
    }
  }

  // ------------------------------------------------------------ naming

  /// The type without parameters: "video/webm;codecs=vp8" becomes "video/webm".
  String _cleanType(String type) => type.split(';').first.trim().toLowerCase();

  /// The file's own name, or one built from its type when it has none (a browser camera hands over a bare blob name).
  String _nameOf(XFile file, String stem, String type, String fallbackEnding) {
    final name = file.name;
    if (name.isNotEmpty && name.contains('.') && !name.endsWith('.')) return name;
    final ending = extensionFor(type);
    return '$stem-${DateTime.now().millisecondsSinceEpoch}.${ending == 'bin' ? fallbackEnding : ending}';
  }

  // ------------------------------------------------------------ photos

  double? get _pickerEdge => compressor.level.longestEdge == 0 ? null : compressor.level.longestEdge.toDouble();

  int? get _pickerQuality => compressor.level == CompressionLevel.original ? null : compressor.level.jpegQuality;

  Future<ChatAttachment?> _photo(XFile? file) async {
    if (file == null) return null;
    final raw = await file.readAsBytes();
    final reported = file.mimeType;
    var type = _cleanType(mimeFor(file.name, reported: reported == null ? null : _cleanType(reported)));
    if (!type.startsWith('image/')) type = 'image/jpeg';
    return _shrinkPhoto(raw, _nameOf(file, 'photo', type, 'jpg'), type);
  }

  Future<ChatAttachment> _shrinkPhoto(Uint8List raw, String name, String type) async {
    final worthIt = compressor.level != CompressionLevel.original && raw.length >= MediaCompressor.skipBelowBytes;
    if (worthIt) _working.value = 'Making the photo smaller...';
    try {
      final made = await compressor.image(raw, mimeType: type, fileName: name);
      if (made.bytes.length > maxChatFileBytes) throw _tooBig;
      return ChatAttachment(kind: 'IMAGE', bytes: made.bytes, mimeType: made.mimeType, fileName: made.fileName);
    } finally {
      if (worthIt) _working.value = null;
    }
  }

  @override
  Future<ChatAttachment?> photoFromGallery() => _guard(
        () async => _photo(await _images.pickImage(source: ImageSource.gallery, maxWidth: _pickerEdge, maxHeight: _pickerEdge, imageQuality: _pickerQuality)),
        'photo library',
      );

  @override
  Future<ChatAttachment?> photoFromCamera() => _guard(() async {
        final XFile? file = _onPhone
            ? await _images.pickImage(source: ImageSource.camera, maxWidth: _pickerEdge, maxHeight: _pickerEdge, imageQuality: _pickerQuality)
            : await _capture(video: false);
        return _photo(file);
      }, 'camera');

  // ------------------------------------------------------------ video

  /// The in-app camera screen, for the web and the computer.
  Future<XFile?> _capture({required bool video}) async {
    final navigator = navigatorKey?.currentState;
    if (navigator == null) throw const ChatDeviceException('The camera could not be opened from here.');
    return CameraCaptureScreen.open(navigator, video: video, videoBitrate: compressor.level.videoBitrate);
  }

  Future<ChatAttachment?> _video(XFile? file) async {
    if (file == null) return null;
    final length = await file.length();
    final canShrink = compressor.level != CompressionLevel.original && MediaCompressor.supportsVideoCompression;
    // Read into memory only after it fits, a video that cannot be made smaller must already be small enough.
    if (!canShrink && length > maxChatFileBytes) throw _tooBig;
    if (length > 600 * 1024 * 1024) throw const ChatDeviceException('That video is too large to prepare. Record a shorter one.');
    var source = file;
    if (canShrink && length > 2 * 1024 * 1024) {
      _working.value = 'Making the video smaller. This can take a minute...';
      try {
        source = (await compressor.video(file)).file;
      } finally {
        _working.value = null;
        unawaited(compressor.clearVideoCache());
      }
    }
    final bytes = await source.readAsBytes();
    if (bytes.length > maxChatFileBytes) throw _tooBig;
    final reported = source.mimeType;
    var type = _cleanType(mimeFor(source.name, reported: reported == null ? null : _cleanType(reported)));
    if (!type.startsWith('video/')) type = 'video/mp4';
    return ChatAttachment(kind: 'VIDEO', bytes: bytes, mimeType: type, fileName: _nameOf(source, 'video', type, 'mp4'));
  }

  @override
  Future<ChatAttachment?> videoFromGallery() => _guard(() async => _video(await _images.pickVideo(source: ImageSource.gallery)), 'video library');

  @override
  Future<ChatAttachment?> videoFromCamera() => _guard(() async {
        final XFile? file = _onPhone ? await _images.pickVideo(source: ImageSource.camera, maxDuration: const Duration(minutes: 2)) : await _capture(video: true);
        return _video(file);
      }, 'camera');

  // ------------------------------------------------------------ documents

  @override
  Future<ChatAttachment?> document() => _guard(() async {
        final chosen = await chooseFile();
        if (chosen == null) return null;
        final reported = chosen.mimeType;
        final type = _cleanType(mimeFor(chosen.name, reported: reported == null ? null : _cleanType(reported)));
        if (type.startsWith('image/')) return _shrinkPhoto(chosen.bytes, chosen.name, type);
        if (chosen.bytes.length > maxChatFileBytes) throw _tooBig;
        return ChatAttachment(kind: ChatAttachment.kindFor(type), bytes: chosen.bytes, mimeType: type, fileName: chosen.name);
      }, 'file chooser');

  // ------------------------------------------------------------ location

  @override
  Future<GeoPoint?> location() async {
    try {
      if (!await Geolocator.isLocationServiceEnabled()) {
        throw const LocationUnavailable('Location is switched off on this device. Turn it on, or type the place instead.');
      }
      var permission = await Geolocator.checkPermission();
      if (permission == LocationPermission.denied) permission = await Geolocator.requestPermission();
      if (permission == LocationPermission.denied || permission == LocationPermission.deniedForever) {
        throw const LocationUnavailable(
            'The app is not allowed to use your location. Allow it in the device settings (in a browser, from the lock icon next to the address), or type the place instead.');
      }
      final accuracy = _onPhone ? LocationAccuracy.high : LocationAccuracy.medium;
      final p = await Geolocator.getCurrentPosition(locationSettings: LocationSettings(accuracy: accuracy, timeLimit: const Duration(seconds: 20)));
      return GeoPoint(p.latitude, p.longitude);
    } on LocationUnavailable {
      rethrow;
    } on TimeoutException {
      throw const LocationUnavailable('Your position was not found in time. Try again, or type the place instead.');
    } on Object {
      throw LocationUnavailable(_onDesktop && defaultTargetPlatform == TargetPlatform.linux
          ? 'Your position could not be read. On Linux this needs the GeoClue service (package geoclue-2.0). You can also type the place instead.'
          : 'Your position could not be read here. You can type the place instead.');
    }
  }

  // ------------------------------------------------------------ voice

  /// The best sound format this device records: AAC on phones and Safari, Opus in Chrome and Firefox, WAV as the last resort.
  Future<_VoiceFormat> _voiceFormat() async {
    final choices = <_VoiceFormat>[
      const _VoiceFormat(AudioEncoder.aacLc, 'audio/mp4', 'm4a', 44100, 64000),
      const _VoiceFormat(AudioEncoder.opus, kIsWeb ? 'audio/webm' : 'audio/ogg', kIsWeb ? 'webm' : 'ogg', 48000, 32000),
      const _VoiceFormat(AudioEncoder.wav, 'audio/wav', 'wav', 16000, 128000),
    ];
    for (final choice in choices) {
      try {
        if (await _recorder.isEncoderSupported(choice.encoder)) return choice;
      } on Object {
        continue;
      }
    }
    return choices.first;
  }

  @override
  Future<void> startVoice() async {
    if (!await _recorder.hasPermission()) {
      throw const ChatDeviceException('The app is not allowed to use the microphone. Allow it in the device settings (in a browser, from the lock icon next to the address).');
    }
    final format = await _voiceFormat();
    final path = await DeviceFiles.recordingPath(format.ending);
    await _recorder.start(
      RecordConfig(encoder: format.encoder, bitRate: format.bitRate, sampleRate: format.sampleRate, numChannels: 1),
      path: path,
    );
    _recordingPath = path;
    _recordingFormat = format;
    _recordingStarted = DateTime.now();
  }

  @override
  Future<ChatAttachment?> stopVoice() async {
    final started = _recordingStarted;
    final format = _recordingFormat ?? const _VoiceFormat(AudioEncoder.aacLc, 'audio/mp4', 'm4a', 44100, 64000);
    final stopped = await _recorder.stop();
    final path = stopped ?? _recordingPath;
    _recordingStarted = null;
    _recordingPath = null;
    _recordingFormat = null;
    if (path == null || path.isEmpty) return null;
    try {
      final secs = started == null ? 0 : DateTime.now().difference(started).inSeconds;
      if (secs < 1) return null;
      // XFile reads a file path on a phone or computer and a blob address in a browser.
      final bytes = await XFile(path).readAsBytes();
      var type = format.mimeType;
      var ending = format.ending;
      if (format.encoder == AudioEncoder.opus) {
        // Chrome writes Opus in a WebM file and Firefox in an Ogg file: name what is really in the bytes.
        final sniffed = _soundContainer(bytes);
        if (sniffed != null) {
          type = sniffed.$1;
          ending = sniffed.$2;
        }
      }
      return ChatAttachment(kind: 'AUDIO', bytes: bytes, mimeType: type, fileName: 'Voice note ${secs}s.$ending', voiceNote: true);
    } finally {
      await DeviceFiles.discard(path);
    }
  }

  @override
  Future<void> cancelVoice() async {
    final path = _recordingPath;
    _recordingStarted = null;
    _recordingPath = null;
    _recordingFormat = null;
    try {
      await _recorder.cancel();
    } on Exception {
      // nothing was recording
    }
    if (path != null && path.isNotEmpty) await DeviceFiles.discard(path);
  }

  // ------------------------------------------------------------ playback and opening

  @override
  Future<void> play(Uint8List bytes, String mimeType) async {
    await stopPlayback();
    final done = Completer<void>();
    _playing = done;
    await _completeSub?.cancel();
    _completeSub = _player.onPlayerComplete.listen((_) {
      if (!done.isCompleted) done.complete();
    });
    await _player.play(BytesSource(bytes, mimeType: mimeType));
    await done.future;
  }

  @override
  Future<void> stopPlayback() async {
    await _player.stop();
    final p = _playing;
    if (p != null && !p.isCompleted) p.complete();
    _playing = null;
  }

  @override
  Future<void> open(Uint8List bytes, String fileName) => DeviceFiles.open(bytes, fileName);
}

/// The MIME type and file ending a recording really has, from its first bytes. Null when it is neither Ogg nor WebM.
(String, String)? _soundContainer(Uint8List bytes) {
  if (bytes.length < 4) return null;
  if (bytes[0] == 0x4F && bytes[1] == 0x67 && bytes[2] == 0x67 && bytes[3] == 0x53) return ('audio/ogg', 'ogg');
  if (bytes[0] == 0x1A && bytes[1] == 0x45 && bytes[2] == 0xDF && bytes[3] == 0xA3) return ('audio/webm', 'webm');
  return null;
}

/// A sound format a recording can be made in, with what the server needs to know about it.
class _VoiceFormat {
  const _VoiceFormat(this.encoder, this.mimeType, this.ending, this.sampleRate, this.bitRate);

  final AudioEncoder encoder;
  final String mimeType;
  final String ending;
  final int sampleRate;
  final int bitRate;
}
