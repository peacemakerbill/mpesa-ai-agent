import 'package:flutter/foundation.dart';

import 'contact_models.dart';
import 'parsers.dart';
import 'sources.dart';

enum ContactSourceKind { phone, google, microsoft, file, manual }

extension ContactSourceKindX on ContactSourceKind {
  String get label {
    switch (this) {
      case ContactSourceKind.phone:
        return 'This phone';
      case ContactSourceKind.google:
        return 'Google';
      case ContactSourceKind.microsoft:
        return 'Microsoft';
      case ContactSourceKind.file:
        return 'vCard or CSV';
      case ContactSourceKind.manual:
        return 'Type it';
    }
  }
}

/// What the contact finder shows and does, without any widgets so it can be tested.
class ContactFinderController extends ChangeNotifier {
  ContactFinderController({
    required this.addressBook,
    required this.google,
    required this.microsoft,
    required this.files,
    required this.microsoftConfigured,
    String query = '',
  }) : query = query;

  final AddressBook addressBook;
  final GoogleContactsClient google;
  final MicrosoftContactsClient microsoft;
  final ContactFileReader files;
  final bool microsoftConfigured;

  ContactSourceKind source = ContactSourceKind.phone;
  String query;
  List<ContactEntry> results = const [];
  bool busy = false;
  bool searched = false;
  String? error;

  /// True when the customer refused the contacts permission, so the screen can offer the phone's own chooser.
  bool phoneDenied = false;

  List<ContactEntry>? _phone;
  List<ContactEntry>? _file;
  String? fileName;

  bool get hasFile => _file != null;

  void selectSource(ContactSourceKind kind) {
    if (kind == source) return;
    source = kind;
    results = const [];
    error = null;
    searched = false;
    notifyListeners();
    if (kind == ContactSourceKind.file && _file != null) search();
  }

  void setQuery(String value) {
    query = value;
    // The phone and the file are searched on the phone, so results follow the typing.
    if (source == ContactSourceKind.file && _file != null) {
      results = filterContacts(_file!, query);
      searched = true;
      notifyListeners();
    } else if (source == ContactSourceKind.phone && _phone != null) {
      results = filterContacts(_phone!, query);
      searched = true;
      notifyListeners();
    }
  }

  Future<void> search() async {
    busy = true;
    error = null;
    notifyListeners();
    try {
      switch (source) {
        case ContactSourceKind.phone:
          if (_phone == null) {
            if (!await addressBook.ensurePermission()) {
              phoneDenied = true;
              throw const ContactSourceException('The app is not allowed to read contacts. Allow it in your phone settings, or choose a person with the phone\'s own chooser.');
            }
            phoneDenied = false;
            _phone = await addressBook.all();
          }
          results = filterContacts(_phone!, query);
        case ContactSourceKind.google:
          results = await google.search(query);
        case ContactSourceKind.microsoft:
          if (!microsoftConfigured) {
            throw const ContactSourceException('Microsoft contacts are not set up in this build of the app. See docs/CONTACTS.md.');
          }
          results = await microsoft.search(query);
        case ContactSourceKind.file:
          results = filterContacts(_file ?? const [], query);
        case ContactSourceKind.manual:
          results = const [];
      }
      searched = true;
    } on ContactSourceException catch (e) {
      error = e.message;
      results = const [];
    } on Exception catch (e) {
      error = 'Contacts could not be read: $e';
      results = const [];
    }
    busy = false;
    notifyListeners();
  }

  Future<void> importFile() async {
    try {
      final picked = await files.pick();
      if (picked == null) return;
      final list = parseContactsFile(picked.name, picked.text);
      _file = list;
      fileName = picked.name;
      error = list.isEmpty ? 'No contacts with a phone number were found in ${picked.name}.' : null;
      results = filterContacts(list, query);
      searched = true;
    } on Exception catch (e) {
      error = 'That file could not be read: $e';
    }
    notifyListeners();
  }

  /// The phone's own chooser. Returns the person chosen, or null when the customer backed out.
  Future<ContactEntry?> chooseWithSystemPicker() async {
    try {
      return await addressBook.chooseWithSystemPicker();
    } on Exception catch (e) {
      error = 'The contact chooser could not open: $e';
      notifyListeners();
      return null;
    }
  }
}
