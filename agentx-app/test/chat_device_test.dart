import 'package:flutter_test/flutter_test.dart';
import 'package:agentx_app/chat/chat_device.dart';

void main() {
  test('MIME types come from the file ending', () {
    expect(mimeFor('photo.JPG'), 'image/jpeg');
    expect(mimeFor('Statement.pdf'), 'application/pdf');
    expect(mimeFor('clip.mp4'), 'video/mp4');
    expect(mimeFor('voice.m4a'), 'audio/mp4');
    expect(mimeFor('noending'), 'application/octet-stream');
    expect(mimeFor('weird.xyz'), 'application/octet-stream');
  });

  test('a type reported by the phone wins unless it is generic', () {
    expect(mimeFor('a.bin', reported: 'image/png'), 'image/png');
    expect(mimeFor('a.pdf', reported: 'application/octet-stream'), 'application/pdf');
  });

  test('a received file without a name gets a fitting ending', () {
    expect(extensionFor('image/png'), 'png');
    expect(extensionFor('application/pdf'), 'pdf');
    expect(extensionFor('something/else'), 'bin');
  });

  group('typed places', () {
    test('plain numbers, with a comma or a space', () {
      final a = parseCoordinates('-1.286389, 36.817223')!;
      expect(a.latitude, -1.286389);
      expect(a.longitude, 36.817223);
      final b = parseCoordinates('  -1.286389   36.817223 ')!;
      expect(b.latitude, -1.286389);
      expect(b.longitude, 36.817223);
    });

    test('a Google Maps link', () {
      final at = parseCoordinates('https://www.google.com/maps/@-1.2921,36.8219,15z')!;
      expect(at.latitude, -1.2921);
      expect(at.longitude, 36.8219);
      final pin = parseCoordinates('https://www.google.com/maps/place/Nairobi/data=!3d-1.2833!4d36.8167')!;
      expect(pin.latitude, -1.2833);
      expect(pin.longitude, 36.8167);
    });

    test('whole numbers need a comma, so a phone number is not a place', () {
      expect(parseCoordinates('1, 37')!.longitude, 37);
      expect(parseCoordinates('0712 345 678'), isNull);
    });

    test('numbers outside the globe and words are refused', () {
      expect(parseCoordinates('95.5, 36.8'), isNull);
      expect(parseCoordinates('1.5, 190.5'), isNull);
      expect(parseCoordinates('Nairobi'), isNull);
      expect(parseCoordinates(''), isNull);
    });

    test('a location problem is also a device problem, so older handlers still catch it', () {
      expect(const LocationUnavailable('x'), isA<ChatDeviceException>());
    });
  });
}
