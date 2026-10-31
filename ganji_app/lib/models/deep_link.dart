final RegExp _uuid = RegExp(
    r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$');

/// Reads the request id out of a link the assistant sent. Accepted shapes:
///   https://host/a/{id}   and   mganji://a/{id}
/// Returns null for anything else, so a strange link can never steer the app.
String? parseActionLink(Uri uri, {String scheme = 'mganji'}) {
  String? id;
  if (uri.scheme == 'https' || uri.scheme == 'http') {
    final s = uri.pathSegments;
    if (s.length == 2 && s[0] == 'a') id = s[1];
  } else if (uri.scheme == scheme) {
    if (uri.host == 'a' && uri.pathSegments.length == 1) id = uri.pathSegments[0];
  }
  if (id == null || !_uuid.hasMatch(id)) return null;
  return id.toLowerCase();
}
