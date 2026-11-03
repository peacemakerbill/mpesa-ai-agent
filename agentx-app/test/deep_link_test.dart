import 'package:flutter_test/flutter_test.dart';
import 'package:agentx_app/models/deep_link.dart';

void main() {
  const id = '44444444-4444-4444-4444-444444444444';

  test('an https link carries the request id', () {
    expect(parseActionLink(Uri.parse('https://agentx.example.com/a/$id')), id);
  });

  test('the app scheme link carries the request id', () {
    expect(parseActionLink(Uri.parse('agentx://a/$id')), id);
  });

  test('an upper case id is accepted and normalised', () {
    expect(parseActionLink(Uri.parse('https://x.test/a/${id.toUpperCase()}')), id);
  });

  test('anything else is ignored', () {
    expect(parseActionLink(Uri.parse('https://x.test/a/not-a-uuid')), isNull);
    expect(parseActionLink(Uri.parse('https://x.test/b/$id')), isNull);
    expect(parseActionLink(Uri.parse('https://x.test/a/$id/extra')), isNull);
    expect(parseActionLink(Uri.parse('agentx://other/$id')), isNull);
    expect(parseActionLink(Uri.parse('ftp://x.test/a/$id')), isNull);
    expect(parseActionLink(Uri.parse('agentx://a/../$id')), isNull);
  });
}
