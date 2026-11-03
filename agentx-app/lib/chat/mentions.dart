import '../contacts/contact_models.dart';
import '../models/business_account.dart';

/// An @ the customer is typing, and the words after it so far.
class MentionQuery {
  const MentionQuery(this.start, this.query);

  /// Where the @ sits in the text.
  final int start;

  /// The text between the @ and the cursor.
  final String query;
}

/// The longest name we look for after an @. Account names can have spaces, so the search runs across a few words.
const int maxMentionLength = 28;

/// Finds the @ being typed at [cursor], or null when the cursor is not inside one.
///
/// An @ counts only at the start of the text or after a space or new line, so an email address never opens the picker.
MentionQuery? activeMention(String text, int cursor) {
  if (cursor < 1 || cursor > text.length) return null;
  final before = text.substring(0, cursor);
  final at = before.lastIndexOf('@');
  if (at < 0) return null;
  if (at > 0 && !RegExp(r'\s').hasMatch(before[at - 1])) return null;
  final query = before.substring(at + 1);
  if (query.contains('\n') || query.length > maxMentionLength) return null;
  return MentionQuery(at, query);
}

/// The text after choosing a mention: the typed @ and query are replaced by [insert], and the cursor goes after it.
({String text, int cursor}) applyMention(String text, int cursor, MentionQuery q, String insert) {
  final head = text.substring(0, q.start);
  final tail = text.substring(cursor);
  final glue = insert.isEmpty || tail.startsWith(' ') ? '' : ' ';
  final result = '$head$insert$glue$tail';
  return (text: result, cursor: head.length + insert.length + glue.length);
}

enum MentionKind { account, contacts }

/// One row in the picker.
class MentionItem {
  const MentionItem.account(BusinessAccount this.account)
      : kind = MentionKind.account,
        query = '';
  const MentionItem.contacts(this.query)
      : kind = MentionKind.contacts,
        account = null;

  final MentionKind kind;
  final BusinessAccount? account;

  /// For the contacts row: what was typed, so the finder opens already searching for it.
  final String query;

  String get title => kind == MentionKind.account ? account!.label : (query.isEmpty ? 'Choose a contact' : 'Find "$query" in contacts');

  String get subtitle {
    if (kind == MentionKind.contacts) return 'Your phone, Google, Microsoft or a file. Only the person you pick is shared.';
    final a = account!;
    return '${a.environment.label}. ${a.headline}';
  }

  /// What goes into the message for an account. Contacts are added as a shared contact instead.
  String get insertText => kind == MentionKind.account ? '@${account!.label}' : '';
}

String _fold(String s) => s.toLowerCase().replaceAll(RegExp(r'\s+'), ' ').trim();

/// The rows to show for what has been typed after the @. Accounts that match come first, best match first. The contacts row is
/// always last, so a name that is not a business account can still be found.
List<MentionItem> mentionSuggestions(List<BusinessAccount> accounts, String query, {int limit = 5}) {
  final q = _fold(query);
  int score(BusinessAccount a) {
    final label = _fold(a.label);
    if (q.isEmpty) return 1;
    if (label == q) return 100;
    if (label.startsWith(q)) return 80;
    if (label.split(' ').any((w) => w.startsWith(q))) return 60;
    if (label.contains(q)) return 40;
    if (a.tillNumber.startsWith(query.trim()) && query.trim().isNotEmpty) return 30;
    if (a.shortcode.startsWith(query.trim()) && query.trim().isNotEmpty) return 20;
    return 0;
  }

  final scored = <(int, BusinessAccount)>[
    for (final a in accounts)
      if (score(a) > 0) (score(a), a),
  ]..sort((x, y) => y.$1.compareTo(x.$1));
  return [
    for (final s in scored.take(limit)) MentionItem.account(s.$2),
    MentionItem.contacts(query.trim()),
  ];
}
