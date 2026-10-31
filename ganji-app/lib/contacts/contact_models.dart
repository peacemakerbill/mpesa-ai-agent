/// One phone number of a contact, with the label the address book gave it (mobile, home, work).
class ContactPhone {
  const ContactPhone(this.number, [this.label = '']);

  final String number;
  final String label;

  @override
  bool operator ==(Object other) => other is ContactPhone && other.number == number && other.label == label;

  @override
  int get hashCode => Object.hash(number, label);

  @override
  String toString() => '$number${label.isEmpty ? '' : ' ($label)'}';
}

/// A person found in an address book, a Google or Microsoft account, or a file.
class ContactEntry {
  const ContactEntry(this.name, this.phones, {this.from});

  final String name;
  final List<ContactPhone> phones;

  /// Where the person was found, for example "Other Google contacts".
  final String? from;

  @override
  String toString() => '$name $phones';
}

/// The number the customer chose, and who it belongs to. It stays on the phone except when the customer
/// sends it to the assistant as a shared contact.
class PickedContact {
  const PickedContact(this.name, this.phone);

  final String name;
  final String phone;
}

/// 0712345678 for Kenyan numbers in any common spelling, and +country digits for the rest.
String kenyanPhone(String raw) {
  var d = raw.replaceAll(RegExp(r'[^\d+]'), '');
  if (d.isEmpty) return '';
  if (d.startsWith('00')) d = '+${d.substring(2)}';
  if (d.startsWith('+')) {
    final rest = d.substring(1);
    return rest.startsWith('254') && rest.length == 12 ? '0${rest.substring(3)}' : d;
  }
  if (d.startsWith('254') && d.length == 12) return '0${d.substring(3)}';
  if (RegExp(r'^[17]\d{8}$').hasMatch(d)) return '0$d';
  return d;
}

/// A string that is plausibly a phone number: at least seven digits.
bool looksLikeNumber(String s) => s.replaceAll(RegExp(r'\D'), '').length >= 7;

String _fold(String s) {
  const from = 'àáâãäåçèéêëìíîïñòóôõöùúûüýÿ';
  const to = 'aaaaaaceeeeiiiinooooouuuuyy';
  final b = StringBuffer();
  for (final r in s.toLowerCase().runes) {
    final c = String.fromCharCode(r);
    final i = from.indexOf(c);
    b.write(i >= 0 ? to[i] : c);
  }
  return b.toString();
}

/// Keeps the people whose name or number contains every word of [query]. An empty query keeps the first [limit].
List<ContactEntry> filterContacts(List<ContactEntry> list, String query, {int limit = 50}) {
  final words = _fold(query).split(RegExp(r'\s+')).where((w) => w.isNotEmpty).toList();
  if (words.isEmpty) return list.take(limit).toList();
  bool matches(ContactEntry c) {
    final b = StringBuffer(_fold(c.name));
    for (final p in c.phones) {
      final digits = p.number.replaceAll(RegExp(r'\D'), '');
      final k = kenyanPhone(p.number).replaceAll(RegExp(r'\D'), '');
      b..write(' ')..write(digits)..write(' ')..write(k);
      if (RegExp(r'^0\d{9}$').hasMatch(k)) b..write(' 254')..write(k.substring(1));
    }
    final hay = b.toString();
    return words.every((w) => RegExp(r'^[\d+()-]+$').hasMatch(w) ? hay.contains(w.replaceAll(RegExp(r'\D'), '')) : hay.contains(w));
  }

  return list.where(matches).take(limit).toList();
}
