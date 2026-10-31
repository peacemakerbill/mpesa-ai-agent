import 'package:flutter_test/flutter_test.dart';
import 'package:ganji_app/chat/chat_device.dart';

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
}
