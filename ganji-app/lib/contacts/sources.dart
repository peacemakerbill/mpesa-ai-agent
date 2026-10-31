import 'dart:convert';

import 'package:http/http.dart' as http;

import 'contact_models.dart';

/// Something went wrong reading contacts, with a message that can be shown as it is.
class ContactSourceException implements Exception {
  const ContactSourceException(this.message);

  final String message;

  @override
  String toString() => message;
}

/// The address book of this phone, including accounts it syncs with.
abstract class AddressBook {
  /// Asks for the permission if needed. False when the customer said no.
  Future<bool> ensurePermission();

  Future<List<ContactEntry>> all();

  /// The phone's own contact chooser. It needs no permission, the app sees only the person chosen.
  Future<ContactEntry?> chooseWithSystemPicker();
}

/// Lets the customer choose a .vcf or .csv file and returns its text.
abstract class ContactFileReader {
  Future<({String name, String text})?> pick();
}

/// Sign-in for the Google and Microsoft contacts. Each call returns a current access token or throws
/// [ContactSourceException]. The tokens are kept in memory only.
abstract class AccessTokens {
  bool get microsoftConfigured;

  Future<String> google();

  Future<String> microsoft();

  /// Forget a token the server refused, so the next call signs in again.
  Future<void> forget({required bool google});
}

bool _hasNumber(ContactEntry c) => c.phones.isNotEmpty;

/// Google Contacts and "other contacts" (people the customer has emailed) through the People API.
class GoogleContactsClient {
  GoogleContactsClient({required http.Client client, required AccessTokens tokens}) : _client = client, _tokens = tokens;

  final http.Client _client;
  final AccessTokens _tokens;
  bool _warm = false;

  static const _base = 'https://people.googleapis.com/v1/';

  Future<Map<String, dynamic>> _get(String token, String path) async {
    final http.Response r;
    try {
      r = await _client.get(Uri.parse('$_base$path'), headers: {'Authorization': 'Bearer $token'});
    } on Exception {
      throw const ContactSourceException('Could not reach Google. Check your internet connection.');
    }
    if (r.statusCode == 401) {
      await _tokens.forget(google: true);
      throw const ContactSourceException('Google sign-in expired. Search again to sign in.');
    }
    if (r.statusCode < 200 || r.statusCode >= 300) {
      var message = 'Google answered ${r.statusCode}.';
      try {
        final j = jsonDecode(utf8.decode(r.bodyBytes));
        final m = j is Map && j['error'] is Map ? (j['error'] as Map)['message'] : null;
        if (m is String && m.isNotEmpty) message = m;
      } on FormatException {
        // keep the generic message
      }
      throw ContactSourceException(message);
    }
    final j = jsonDecode(utf8.decode(r.bodyBytes));
    return j is Map<String, dynamic> ? j : <String, dynamic>{};
  }

  Future<List<ContactEntry>> search(String query) async {
    final token = await _tokens.google();
    // Google asks for one empty search before the first real one, so its search index is ready.
    if (!_warm) {
      _warm = true;
      try {
        await Future.wait([
          _get(token, 'people:searchContacts?query=&readMask=names'),
          _get(token, 'otherContacts:search?query=&readMask=names'),
        ]);
      } on ContactSourceException {
        // the real search below reports a real problem
      }
    }
    final q = Uri.encodeQueryComponent(query.trim());
    const mask = 'names,phoneNumbers';
    final mine = await _get(token, 'people:searchContacts?query=$q&readMask=$mask&pageSize=10');
    Map<String, dynamic> other;
    try {
      other = await _get(token, 'otherContacts:search?query=$q&readMask=$mask&pageSize=10');
    } on ContactSourceException {
      other = <String, dynamic>{};
    }
    final seen = <String>{};
    final out = <ContactEntry>[];
    for (final (res, label) in [(mine, 'Google contacts'), (other, 'Other Google contacts')]) {
      final results = res['results'];
      if (results is! List) continue;
      for (final item in results) {
        final person = item is Map ? item['person'] : null;
        if (person is! Map) continue;
        final names = person['names'];
        final name = names is List && names.isNotEmpty && names.first is Map ? ((names.first as Map)['displayName'] as String? ?? '') : '';
        final nums = person['phoneNumbers'];
        final phones = <ContactPhone>[];
        if (nums is List) {
          for (final n in nums) {
            if (n is! Map) continue;
            final v = (n['canonicalForm'] ?? n['value']) as String?;
            if (v != null && looksLikeNumber(v)) phones.add(ContactPhone(v, (n['type'] as String?) ?? ''));
          }
        }
        final entry = ContactEntry(name.isEmpty ? '(no name)' : name, phones, from: label);
        final key = '${entry.name}|${phones.map((p) => kenyanPhone(p.number)).join(',')}';
        if (_hasNumber(entry) && seen.add(key)) out.add(entry);
      }
    }
    return out;
  }
}

/// Outlook and Microsoft 365 contacts through Microsoft Graph. Graph cannot search contacts by name, so the
/// whole list is read once and searched on the phone.
class MicrosoftContactsClient {
  MicrosoftContactsClient({required http.Client client, required AccessTokens tokens}) : _client = client, _tokens = tokens;

  final http.Client _client;
  final AccessTokens _tokens;
  List<ContactEntry>? _all;

  static const _first = 'https://graph.microsoft.com/v1.0/me/contacts?\$select=displayName,mobilePhone,homePhones,businessPhones&\$top=200';

  Future<List<ContactEntry>> _load() async {
    final cached = _all;
    if (cached != null) return cached;
    final token = await _tokens.microsoft();
    final all = <ContactEntry>[];
    String? url = _first;
    for (var page = 0; url != null && page < 10; page++) {
      final http.Response r;
      try {
        r = await _client.get(Uri.parse(url), headers: {'Authorization': 'Bearer $token'});
      } on Exception {
        throw const ContactSourceException('Could not reach Microsoft. Check your internet connection.');
      }
      if (r.statusCode == 401) {
        await _tokens.forget(google: false);
        throw const ContactSourceException('Microsoft sign-in expired. Search again to sign in.');
      }
      if (r.statusCode < 200 || r.statusCode >= 300) {
        throw ContactSourceException('Microsoft answered ${r.statusCode}.');
      }
      final j = jsonDecode(utf8.decode(r.bodyBytes));
      final value = j is Map ? j['value'] : null;
      if (value is List) {
        for (final c in value) {
          if (c is! Map) continue;
          final phones = <ContactPhone>[];
          void add(Object? n, String label) {
            if (n is String && looksLikeNumber(n)) phones.add(ContactPhone(n, label));
          }

          add(c['mobilePhone'], 'mobile');
          for (final n in (c['homePhones'] as List? ?? const [])) {
            add(n, 'home');
          }
          for (final n in (c['businessPhones'] as List? ?? const [])) {
            add(n, 'work');
          }
          if (phones.isNotEmpty) all.add(ContactEntry((c['displayName'] as String?) ?? '(no name)', phones, from: 'Outlook'));
        }
      }
      url = j is Map ? j['@odata.nextLink'] as String? : null;
    }
    return _all = all;
  }

  Future<List<ContactEntry>> search(String query) async => filterContacts(await _load(), query);
}
