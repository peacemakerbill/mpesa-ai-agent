/// One chat in the customer's list of chats.
class ChatSession {
  const ChatSession({
    required this.id,
    required this.title,
    required this.pinned,
    required this.active,
    required this.createdAt,
    required this.updatedAt,
    required this.preview,
    required this.messages,
  });

  final String id;

  /// The name the customer gave it, or the first thing they said.
  final String title;
  final bool pinned;

  /// True for the chat the assistant is in right now, the one the chat screen shows.
  final bool active;
  final DateTime createdAt;

  /// When the last message was sent.
  final DateTime updatedAt;

  /// The last message, in a few words. Empty for a chat with no messages.
  final String preview;
  final int messages;

  bool get isEmpty => messages == 0;

  factory ChatSession.fromJson(Map<String, dynamic> json) {
    final title = json['title'];
    return ChatSession(
      id: json['id'] is String ? json['id'] as String : '',
      title: title is String && title.trim().isNotEmpty ? title.trim() : 'New chat',
      pinned: json['pinned'] == true,
      active: json['active'] == true,
      createdAt: DateTime.fromMillisecondsSinceEpoch((json['createdAt'] as num?)?.toInt() ?? 0),
      updatedAt: DateTime.fromMillisecondsSinceEpoch((json['updatedAt'] as num?)?.toInt() ?? 0),
      preview: json['preview'] is String ? json['preview'] as String : '',
      messages: (json['messages'] as num?)?.toInt() ?? 0,
    );
  }

  /// True when the words are in the name or in the last message. An empty search matches every chat.
  bool matches(String query) {
    final q = query.trim().toLowerCase();
    if (q.isEmpty) return true;
    return title.toLowerCase().contains(q) || preview.toLowerCase().contains(q);
  }
}

/// The customer's chats as the server sends them: pinned first, then the most recently used.
class ChatList {
  const ChatList(this.activeId, this.chats);

  final String? activeId;
  final List<ChatSession> chats;

  factory ChatList.fromJson(Map<String, dynamic> json) {
    final list = json['chats'];
    return ChatList(
      json['activeId'] is String ? json['activeId'] as String : null,
      [
        for (final c in list is List ? list : const [])
          if (c is Map<String, dynamic>) ChatSession.fromJson(c),
      ],
    );
  }
}

/// When a chat was last used, for the list: the time today, "Yesterday", or the date.
String chatWhen(DateTime at, DateTime now) {
  bool sameDay(DateTime a, DateTime b) => a.year == b.year && a.month == b.month && a.day == b.day;
  if (sameDay(at, now)) {
    return '${at.hour.toString().padLeft(2, '0')}:${at.minute.toString().padLeft(2, '0')}';
  }
  if (sameDay(at, now.subtract(const Duration(days: 1)))) return 'Yesterday';
  const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
  return '${at.day} ${months[at.month - 1]}${at.year == now.year ? '' : ' ${at.year}'}';
}