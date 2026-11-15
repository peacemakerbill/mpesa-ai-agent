import 'dart:async';

import 'package:camera/camera.dart';
import 'package:flutter/material.dart';

/// A full screen camera for a photo or a video. Used on the web, Linux, Windows and macOS, where the system camera picker
/// does not exist. Android and iOS use the phone's own camera app instead.
///
/// Pops with the picture or the video, or null when the person closes it.
class CameraCaptureScreen extends StatefulWidget {
  const CameraCaptureScreen({super.key, required this.video, this.videoBitrate = 2500000, this.maxDuration = const Duration(minutes: 2), this.loadCameras = availableCameras});

  /// True to record a video, false to take a photo.
  final bool video;

  /// Bits per second a video is recorded at, from the compression level in Settings.
  final int videoBitrate;

  /// Recording stops by itself at this length.
  final Duration maxDuration;

  /// Lists the cameras. Only a test replaces it.
  final Future<List<CameraDescription>> Function() loadCameras;

  /// Opens the screen on [navigator] and completes with the file, or null when it was closed without one.
  static Future<XFile?> open(NavigatorState navigator, {required bool video, int videoBitrate = 2500000}) {
    return navigator.push<XFile>(MaterialPageRoute<XFile>(
      fullscreenDialog: true,
      builder: (_) => CameraCaptureScreen(video: video, videoBitrate: videoBitrate),
    ));
  }

  @override
  State<CameraCaptureScreen> createState() => _CameraCaptureScreenState();
}

class _CameraCaptureScreenState extends State<CameraCaptureScreen> {
  List<CameraDescription> _cameras = const [];
  CameraController? _controller;
  int _index = 0;
  String? _error;
  bool _starting = true;
  bool _recording = false;
  bool _busy = false;
  Duration _elapsed = Duration.zero;
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    _start();
  }

  @override
  void dispose() {
    _timer?.cancel();
    final c = _controller;
    _controller = null;
    if (c != null) unawaited(_shutdown(c, wasRecording: _recording));
    super.dispose();
  }

  /// Stops a recording that nobody asked for (the screen was closed) and lets the camera go.
  Future<void> _shutdown(CameraController c, {required bool wasRecording}) async {
    try {
      if (wasRecording) await c.stopVideoRecording();
    } on Object {
      // nothing was recording any more
    }
    try {
      await c.dispose();
    } on Object {
      // already released
    }
  }

  Future<void> _start() async {
    setState(() {
      _starting = true;
      _error = null;
    });
    try {
      if (_cameras.isEmpty) _cameras = await widget.loadCameras();
      if (_cameras.isEmpty) {
        _fail('No camera was found on this device. Plug one in, or use Photo to choose a file instead.');
        return;
      }
      await _open(_index);
    } on CameraException catch (e) {
      _fail(_explain(e));
    } on Object catch (e) {
      _fail('The camera could not start. $e');
    }
  }

  Future<void> _open(int index) async {
    final old = _controller;
    if (old != null) {
      setState(() => _controller = null);
      await old.dispose();
    }
    final c = CameraController(
      _cameras[index],
      widget.video ? ResolutionPreset.high : ResolutionPreset.veryHigh,
      enableAudio: widget.video,
      videoBitrate: widget.video ? widget.videoBitrate : null,
    );
    try {
      await c.initialize();
    } on Object {
      await c.dispose();
      rethrow;
    }
    if (!mounted) {
      await c.dispose();
      return;
    }
    setState(() {
      _controller = c;
      _index = index;
      _starting = false;
    });
  }

  void _fail(String message) {
    if (!mounted) return;
    setState(() {
      _error = message;
      _starting = false;
      _recording = false;
      _busy = false;
    });
  }

  String _explain(CameraException e) {
    final code = e.code.toLowerCase();
    if (code.contains('audio')) {
      return 'The app is not allowed to use the microphone, and a video needs it. Allow it and try again.';
    }
    if (code.contains('denied') || code.contains('permission') || code.contains('notallowed')) {
      return 'The app is not allowed to use the camera. Allow it for this app (in a browser, from the lock icon next to the address) and try again.';
    }
    if (code.contains('notfound') || code.contains('nocamera')) return 'No camera was found on this device.';
    if (code.contains('notreadable') || code.contains('inuse')) return 'Another app is using the camera. Close it and try again.';
    if (code.contains('security')) return 'A browser only opens the camera on a secure page. Open the app at an https:// address, or at localhost.';
    return e.description ?? 'The camera could not start (${e.code}).';
  }

  Future<void> _flip() async {
    if (_cameras.length < 2 || _recording || _busy) return;
    setState(() => _starting = true);
    try {
      await _open((_index + 1) % _cameras.length);
    } on CameraException catch (e) {
      _fail(_explain(e));
    } on Object catch (e) {
      _fail('The camera could not start. $e');
    }
  }

  Future<void> _snap() async {
    final c = _controller;
    if (c == null || _busy) return;
    setState(() => _busy = true);
    try {
      final file = await c.takePicture();
      if (mounted) Navigator.of(context).pop(file);
    } on CameraException catch (e) {
      _fail(_explain(e));
    } on Object catch (e) {
      _fail('The photo could not be taken. $e');
    }
  }

  Future<void> _toggleRecording() async {
    final c = _controller;
    if (c == null || _busy) return;
    if (_recording) {
      await _stopRecording(c);
      return;
    }
    setState(() => _busy = true);
    try {
      await c.startVideoRecording();
    } on CameraException catch (e) {
      _fail(_explain(e));
      return;
    } on Object catch (e) {
      _fail('The video could not start. $e');
      return;
    }
    _elapsed = Duration.zero;
    _timer = Timer.periodic(const Duration(seconds: 1), (_) {
      if (!mounted) return;
      setState(() => _elapsed += const Duration(seconds: 1));
      if (_elapsed >= widget.maxDuration) _toggleRecording();
    });
    setState(() {
      _recording = true;
      _busy = false;
    });
  }

  Future<void> _stopRecording(CameraController c) async {
    _timer?.cancel();
    setState(() => _busy = true);
    try {
      final file = await c.stopVideoRecording();
      _recording = false;
      if (mounted) Navigator.of(context).pop(file);
    } on CameraException catch (e) {
      _fail(_explain(e));
    } on Object catch (e) {
      _fail('The video could not be saved. $e');
    }
  }

  String _clock(Duration d) {
    final m = d.inMinutes.toString().padLeft(2, '0');
    final s = (d.inSeconds % 60).toString().padLeft(2, '0');
    return '$m:$s';
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: SafeArea(
        child: Stack(children: [
          Positioned.fill(child: _body(context)),
          Positioned(
            top: 4,
            left: 4,
            child: IconButton(
              onPressed: _busy && !_recording ? null : () => Navigator.of(context).pop(),
              icon: const Icon(Icons.close, color: Colors.white),
              tooltip: 'Close the camera',
            ),
          ),
          if (_recording)
            Positioned(
              top: 14,
              left: 0,
              right: 0,
              child: Center(
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
                  decoration: BoxDecoration(color: Colors.red.shade700, borderRadius: BorderRadius.circular(14)),
                  child: Text(_clock(_elapsed), style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600)),
                ),
              ),
            ),
        ]),
      ),
    );
  }

  Widget _body(BuildContext context) {
    final error = _error;
    if (error != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(28),
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 420),
            child: Column(mainAxisSize: MainAxisSize.min, children: [
              const Icon(Icons.videocam_off_outlined, color: Colors.white70, size: 40),
              const SizedBox(height: 12),
              Text(error, textAlign: TextAlign.center, style: const TextStyle(color: Colors.white)),
              const SizedBox(height: 16),
              Wrap(spacing: 12, children: [
                FilledButton(onPressed: _start, child: const Text('Try again')),
                OutlinedButton(
                  onPressed: () => Navigator.of(context).pop(),
                  style: OutlinedButton.styleFrom(foregroundColor: Colors.white),
                  child: const Text('Close'),
                ),
              ]),
            ]),
          ),
        ),
      );
    }
    final c = _controller;
    if (_starting || c == null) {
      return const Center(child: CircularProgressIndicator(color: Colors.white));
    }
    return Column(children: [
      Expanded(
        child: Center(
          // On the web and on desktop the preview is as wide as the picture the camera sends.
          child: AspectRatio(aspectRatio: c.value.aspectRatio, child: CameraPreview(c)),
        ),
      ),
      Padding(
        padding: const EdgeInsets.fromLTRB(24, 12, 24, 20),
        child: Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
          const SizedBox(width: 48),
          _shutter(),
          SizedBox(
            width: 48,
            child: _cameras.length > 1 && !_recording
                ? IconButton(onPressed: _flip, icon: const Icon(Icons.cameraswitch_outlined, color: Colors.white), tooltip: 'Use the other camera')
                : null,
          ),
        ]),
      ),
    ]);
  }

  Widget _shutter() {
    final video = widget.video;
    final label = video ? (_recording ? 'Stop recording' : 'Start recording') : 'Take the photo';
    return Semantics(
      button: true,
      label: label,
      child: Tooltip(
        message: label,
        child: GestureDetector(
          onTap: video ? _toggleRecording : _snap,
          child: Container(
            width: 72,
            height: 72,
            padding: const EdgeInsets.all(5),
            decoration: BoxDecoration(shape: BoxShape.circle, border: Border.all(color: Colors.white, width: 4)),
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 150),
              decoration: BoxDecoration(
                color: video ? Colors.red.shade600 : Colors.white,
                shape: _recording ? BoxShape.rectangle : BoxShape.circle,
                borderRadius: _recording ? BorderRadius.circular(8) : null,
              ),
              margin: EdgeInsets.all(_recording ? 14 : 0),
            ),
          ),
        ),
      ),
    );
  }
}
