import 'package:agentx_app/chat/camera_capture_screen.dart';
import 'package:camera/camera.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

Future<List<CameraDescription>> _none() async => const [];

Future<List<CameraDescription>> _denied() async => throw CameraException('cameraPermission', 'denied');

void main() {
  testWidgets('with no camera the screen says so and offers Try again and Close', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: CameraCaptureScreen(video: false, loadCameras: _none)));
    await tester.pump();
    await tester.pump();
    expect(find.textContaining('No camera was found'), findsOneWidget);
    expect(find.text('Try again'), findsOneWidget);
    expect(find.text('Close'), findsOneWidget);
    expect(find.byType(CircularProgressIndicator), findsNothing);
  });

  testWidgets('a refused camera permission is explained', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: CameraCaptureScreen(video: true, loadCameras: _denied)));
    await tester.pump();
    await tester.pump();
    expect(find.textContaining('not allowed to use the camera'), findsOneWidget);
  });

  testWidgets('Close leaves the screen and returns nothing', (tester) async {
    Object? result = 'unset';
    await tester.pumpWidget(MaterialApp(
      home: Builder(
        builder: (context) => TextButton(
          onPressed: () async => result = await Navigator.of(context).push<Object>(
            MaterialPageRoute<Object>(builder: (_) => const CameraCaptureScreen(video: true, loadCameras: _none)),
          ),
          child: const Text('open'),
        ),
      ),
    ));
    await tester.tap(find.text('open'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Close'));
    await tester.pumpAndSettle();
    expect(result, isNull);
  });
}
