import 'dart:async';
import 'dart:io';
import 'dart:typed_data';

import 'package:audioplayers/audioplayers.dart';
import 'package:file_picker/file_picker.dart';
import 'package:geolocator/geolocator.dart';
import 'package:image_picker/image_picker.dart';
import 'package:open_filex/open_filex.dart';
import 'package:path_provider/path_provider.dart';
import 'package:record/record.dart';

import 'chat_controller.dart';
import 'chat_device.dart';

/// The real phone behind [ChatDevice]. Needs the permissions listed in docs/PLATFORM_SETUP.md.
class PlatformChatDevice implements ChatDevice {
  final ImagePicker _images = ImagePicker();
  final AudioRecorder _recorder = AudioRecorder();
  final AudioPlayer _player = AudioPlayer();
  DateTime? _recordingStarted;
  String? _recordingPath;
  Completer<void>? _playing;
  StreamSubscription<void>? _completeSub;

  Future<ChatAttachment?> _fromXFile(XFile? file, String kind) async {
    if (file == null) return null;
    final bytes = await file.readAsBytes();
    if (bytes.length > maxChatFileBytes) throw const ChatDeviceException('That file is larger than 30 MB.');
    final name = file.name.isEmpty ? '$kind-${DateTime.now().millisecondsSinceEpoch}' : file.name;
    return ChatAttachment(kind: kind, bytes: bytes, mimeType: mimeFor(name, reported: file.mimeType), fileName: name);
  }

  @override
  Future<ChatAttachment?> photoFromGallery() async =>
      _fromXFile(await _images.pickImage(source: ImageSource.gallery, maxWidth: 2048, imageQuality: 88), 'IMAGE');

  @override
  Future<ChatAttachment?> photoFromCamera() async =>
      _fromXFile(await _images.pickImage(source: ImageSource.camera, maxWidth: 2048, imageQuality: 88), 'IMAGE');

  @override
  Future<ChatAttachment?> videoFromGallery() async => _fromXFile(await _images.pickVideo(source: ImageSource.gallery), 'VIDEO');

  @override
  Future<ChatAttachment?> videoFromCamera() async =>
      _fromXFile(await _images.pickVideo(source: ImageSource.camera, maxDuration: const Duration(minutes: 2)), 'VIDEO');

  @override
  Future<ChatAttachment?> document() async {
    final result = await FilePicker.platform.pickFiles(withData: true);
    final file = result?.files.firstOrNull;
    final bytes = file?.bytes;
    if (file == null || bytes == null) return null;
    if (bytes.length > maxChatFileBytes) throw const ChatDeviceException('That file is larger than 30 MB.');
    final type = mimeFor(file.name);
    return ChatAttachment(kind: ChatAttachment.kindFor(type), bytes: bytes, mimeType: type, fileName: file.name);
  }

  @override
  Future<GeoPoint?> location() async {
    if (!await Geolocator.isLocationServiceEnabled()) {
      throw const ChatDeviceException('Location is switched off on this phone. Turn it on and try again.');
    }
    var permission = await Geolocator.checkPermission();
    if (permission == LocationPermission.denied) permission = await Geolocator.requestPermission();
    if (permission == LocationPermission.denied || permission == LocationPermission.deniedForever) {
      throw const ChatDeviceException('The app is not allowed to use your location. You can allow it in your phone settings.');
    }
    final p = await Geolocator.getCurrentPosition(locationSettings: const LocationSettings(accuracy: LocationAccuracy.high, timeLimit: Duration(seconds: 15)));
    return GeoPoint(p.latitude, p.longitude);
  }

  @override
  Future<void> startVoice() async {
    if (!await _recorder.hasPermission()) {
      throw const ChatDeviceException('The app is not allowed to use the microphone. You can allow it in your phone settings.');
    }
    final dir = await getTemporaryDirectory();
    final path = '${dir.path}/voice-${DateTime.now().millisecondsSinceEpoch}.m4a';
    await _recorder.start(const RecordConfig(encoder: AudioEncoder.aacLc, bitRate: 64000, sampleRate: 44100), path: path);
    _recordingPath = path;
    _recordingStarted = DateTime.now();
  }

  @override
  Future<ChatAttachment?> stopVoice() async {
    final started = _recordingStarted;
    final stopped = await _recorder.stop();
    final path = stopped ?? _recordingPath;
    _recordingStarted = null;
    _recordingPath = null;
    if (path == null) return null;
    final file = File(path);
    try {
      final secs = started == null ? 0 : DateTime.now().difference(started).inSeconds;
      if (secs < 1) return null;
      final bytes = await file.readAsBytes();
      return ChatAttachment(kind: 'AUDIO', bytes: bytes, mimeType: 'audio/mp4', fileName: 'Voice note ${secs}s.m4a', voiceNote: true);
    } finally {
      try {
        await file.delete();
      } on Exception {
        // a temporary file, the phone clears it later
      }
    }
  }

  @override
  Future<void> cancelVoice() async {
    final path = _recordingPath;
    _recordingStarted = null;
    _recordingPath = null;
    try {
      await _recorder.cancel();
    } on Exception {
      // nothing was recording
    }
    if (path != null) {
      try {
        await File(path).delete();
      } on Exception {
        // already gone
      }
    }
  }

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
  Future<void> open(Uint8List bytes, String fileName) async {
    final dir = await getTemporaryDirectory();
    final safe = fileName.replaceAll(RegExp(r'[^\w.\- ]'), '_');
    final file = File('${dir.path}/$safe');
    await file.writeAsBytes(bytes, flush: true);
    final result = await OpenFilex.open(file.path);
    if (result.type != ResultType.done) {
      throw ChatDeviceException(result.type == ResultType.noAppToOpen ? 'No app on this phone can open that file.' : 'The file could not be opened.');
    }
  }
}
