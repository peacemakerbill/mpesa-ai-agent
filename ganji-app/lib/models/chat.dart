/// One button under an assistant message.
class ChatButton {
  const ChatButton(this.id, this.title);

  final String id;
  final String title;
}

/// One row of a menu the assistant sends.
class ChatRow {
  const ChatRow(this.id, this.title, [this.description]);

  final String id;
  final String title;
  final String? description;
}

/// One line of the conversation. [mine] is true for what the customer wrote, false for the assistant.
class ChatMessage {
  const ChatMessage({
    required this.id,
    required this.mine,
    required this.kind,
    required this.at,
    this.text,
    this.footer,
    this.buttons = const [],
    this.listTitle,
    this.rows = const [],
    this.mediaId,
    this.mimeType,
    this.fileName,
    this.caption,
    this.voiceNote = false,
    this.latitude,
    this.longitude,
    this.locationLabel,
    this.contactsSummary,
    this.query,
    this.actionId,
    this.actionTitle,
  });

  final int id;
  final bool mine;

  /// TEXT, BUTTONS, LIST, IMAGE, DOCUMENT, AUDIO, VIDEO, LOCATION, CONTACTS, CONTACT_LOOKUP, BUTTON_REPLY or LIST_REPLY.
  final String kind;
  final DateTime at;
  final String? text;
  final String? footer;
  final List<ChatButton> buttons;
  final String? listTitle;
  final List<ChatRow> rows;
  final String? mediaId;
  final String? mimeType;
  final String? fileName;
  final String? caption;
  final bool voiceNote;
  final double? latitude;
  final double? longitude;
  final String? locationLabel;
  final String? contactsSummary;

  /// For CONTACT_LOOKUP: the name the assistant is looking for.
  final String? query;
  final String? actionId;
  final String? actionTitle;

  bool get isMedia => const {'IMAGE', 'DOCUMENT', 'AUDIO', 'VIDEO'}.contains(kind);

  /// The id of the request this message links to, when the assistant put a request link in its words.
  String? get requestId => mine ? null : requestIdIn(text);

  factory ChatMessage.fromJson(Map<String, dynamic> json) {
    String? str(String key) {
      final v = json[key];
      return v is String && v.isNotEmpty ? v : null;
    }

    double? num_(String key) => (json[key] as num?)?.toDouble();

    final buttons = <ChatButton>[];
    final rawButtons = json['buttons'];
    if (rawButtons is List) {
      for (final b in rawButtons) {
        if (b is Map && b['id'] is String && b['title'] is String) buttons.add(ChatButton(b['id'] as String, b['title'] as String));
      }
    }
    final rows = <ChatRow>[];
    final rawRows = json['rows'];
    if (rawRows is List) {
      for (final r in rawRows) {
        if (r is Map && r['id'] is String && r['title'] is String) {
          rows.add(ChatRow(r['id'] as String, r['title'] as String, r['description'] is String ? r['description'] as String : null));
        }
      }
    }
    return ChatMessage(
      id: (json['id'] as num).toInt(),
      mine: json['dir'] == 'in',
      kind: (json['kind'] as String?) ?? 'TEXT',
      at: DateTime.fromMillisecondsSinceEpoch((json['at'] as num?)?.toInt() ?? 0),
      text: str('text'),
      footer: str('footer'),
      buttons: buttons,
      listTitle: str('listTitle'),
      rows: rows,
      mediaId: str('mediaId'),
      mimeType: str('mimeType'),
      fileName: str('fileName'),
      caption: str('caption'),
      voiceNote: json['voiceNote'] == true,
      latitude: num_('latitude'),
      longitude: num_('longitude'),
      locationLabel: str('locationLabel'),
      contactsSummary: str('contactsSummary'),
      query: str('query'),
      actionId: str('actionId'),
      actionTitle: str('actionTitle'),
    );
  }
}

/// Messages newer than the one the app already has, and whether the assistant is working on an answer.
class ChatPage {
  const ChatPage(this.messages, {required this.typing, required this.lastId});

  final List<ChatMessage> messages;
  final bool typing;
  final int lastId;

  factory ChatPage.fromJson(Map<String, dynamic> json) {
    final list = json['messages'];
    return ChatPage(
      [if (list is List) for (final m in list) if (m is Map<String, dynamic>) ChatMessage.fromJson(m)],
      typing: json['typing'] == true,
      lastId: (json['lastId'] as num?)?.toInt() ?? 0,
    );
  }
}

final RegExp _requestLink = RegExp(r'/a/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})');

/// The request id inside a link like https://host/a/{id}, or null.
String? requestIdIn(String? text) {
  if (text == null) return null;
  final m = _requestLink.firstMatch(text);
  return m?.group(1)?.toLowerCase();
}

/// A run of text with one style, as the assistant writes it: *bold*, _italic_, `code` and links.
class RichSpan {
  const RichSpan(this.text, {this.bold = false, this.italic = false, this.code = false, this.link});

  final String text;
  final bool bold;
  final bool italic;
  final bool code;
  final String? link;

  @override
  bool operator ==(Object other) =>
      other is RichSpan && other.text == text && other.bold == bold && other.italic == italic && other.code == code && other.link == link;

  @override
  int get hashCode => Object.hash(text, bold, italic, code, link);

  @override
  String toString() => 'RichSpan($text${bold ? ', bold' : ''}${italic ? ', italic' : ''}${code ? ', code' : ''}${link != null ? ', link' : ''})';
}

final RegExp _rich = RegExp(
  r'\*\*(?<b2>[^*\n]+)\*\*'
  r'|(?<![\w*])\*(?<b1>[^*\n]+)\*(?![\w*])'
  r'|(?<![\w_])_(?<i>[^_\n]+)_(?![\w_])'
  r'|`(?<c>[^`\n]+)`'
  r'|(?<u>https?://[^\s<]*[^\s<.,;:!?)])',
);

/// Splits the assistant's text into styled runs. Anything that is not markup is kept exactly as written.
List<RichSpan> parseRich(String text) {
  final out = <RichSpan>[];
  var last = 0;
  for (final m in _rich.allMatches(text)) {
    if (m.start > last) out.add(RichSpan(text.substring(last, m.start)));
    final bold = m.namedGroup('b2') ?? m.namedGroup('b1');
    if (bold != null) {
      out.add(RichSpan(bold, bold: true));
    } else if (m.namedGroup('i') != null) {
      out.add(RichSpan(m.namedGroup('i')!, italic: true));
    } else if (m.namedGroup('c') != null) {
      out.add(RichSpan(m.namedGroup('c')!, code: true));
    } else {
      final url = m.namedGroup('u')!;
      out.add(RichSpan(url, link: url));
    }
    last = m.end;
  }
  if (last < text.length) out.add(RichSpan(text.substring(last)));
  return out;
}

/// "Mama Wanjiku 0712345678" splits into the name and the number, the number being the last word.
({String name, String phone}) splitContactSummary(String summary) {
  final s = summary.trim();
  final i = s.lastIndexOf(' ');
  if (i < 0) return (name: '', phone: s);
  return (name: s.substring(0, i).trim(), phone: s.substring(i + 1).trim());
}
