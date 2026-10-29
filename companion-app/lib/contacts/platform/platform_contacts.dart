import 'dart:convert';

import 'package:file_picker/file_picker.dart';
import 'package:flutter_contacts/flutter_contacts.dart';
import 'package:flutter_appauth/flutter_appauth.dart';
import 'package:google_sign_in/google_sign_in.dart';

import '../../config.dart';
import '../contact_models.dart';
import '../contact_services.dart';
import '../sources.dart';

/// The phone's own contacts, through flutter_contacts.
class PlatformAddressBook implements AddressBook {
  @override
  Future<bool> ensurePermission() => FlutterContacts.requestPermission(readonly: true);

  @override
  Future<List<ContactEntry>> all() async {
    final list = await FlutterContacts.getContacts(withProperties: true);
    return [
      for (final c in list)
        if (c.phones.any((p) => looksLikeNumber(p.number)))
          ContactEntry(c.displayName.isEmpty ? '(no name)' : c.displayName, [
            for (final p in c.phones)
              if (looksLikeNumber(p.number)) ContactPhone(p.number, p.label.name),
          ]),
    ];
  }

  @override
  Future<ContactEntry?> chooseWithSystemPicker() async {
    final picked = await FlutterContacts.openExternalPick();
    if (picked == null) return null;
    final full = picked.phones.isEmpty ? (await FlutterContacts.getContact(picked.id)) ?? picked : picked;
    final phones = [
      for (final p in full.phones)
        if (looksLikeNumber(p.number)) ContactPhone(p.number, p.label.name),
    ];
    return phones.isEmpty ? null : ContactEntry(full.displayName.isEmpty ? '(no name)' : full.displayName, phones);
  }
}

/// A .vcf or .csv file chosen with the system file chooser.
class PlatformContactFileReader implements ContactFileReader {
  @override
  Future<({String name, String text})?> pick() async {
    final result = await FilePicker.platform.pickFiles(type: FileType.custom, allowedExtensions: const ['vcf', 'vcard', 'csv'], withData: true);
    final file = result?.files.firstOrNull;
    final bytes = file?.bytes;
    if (file == null || bytes == null) return null;
    if (bytes.length > 20 * 1024 * 1024) throw const ContactSourceException('That file is larger than 20 MB.');
    return (name: file.name, text: utf8.decode(bytes, allowMalformed: true));
  }
}

/// Google sign-in for the contacts scopes (google_sign_in 6.x). Android needs the app's package name and signing
/// fingerprint registered as an OAuth client, iOS needs the client id in Info.plist, see docs/CONTACTS.md.
/// Microsoft sign-in uses AppAuth with a public client, so no secret is stored in the app.
class PlatformAccessTokens implements AccessTokens {
  PlatformAccessTokens({required this.microsoftClientId, required this.microsoftRedirectUrl});

  final String microsoftClientId;
  final String microsoftRedirectUrl;

  static const _googleScopes = [
    'https://www.googleapis.com/auth/contacts.readonly',
    'https://www.googleapis.com/auth/contacts.other.readonly',
  ];

  final GoogleSignIn _google = GoogleSignIn(scopes: _googleScopes);
  final FlutterAppAuth _appAuth = const FlutterAppAuth();
  String? _msToken;
  DateTime? _msExpires;

  @override
  bool get microsoftConfigured => microsoftClientId.isNotEmpty;

  @override
  Future<String> google() async {
    try {
      final account = await _google.signInSilently() ?? await _google.signIn();
      if (account == null) throw const ContactSourceException('Google sign-in was cancelled.');
      final token = (await account.authentication).accessToken;
      if (token == null) throw const ContactSourceException('Google did not give access.');
      return token;
    } on ContactSourceException {
      rethrow;
    } on Exception catch (e) {
      throw ContactSourceException('Google sign-in failed: $e');
    }
  }

  @override
  Future<String> microsoft() async {
    final cached = _msToken;
    if (cached != null && _msExpires != null && _msExpires!.isAfter(DateTime.now().add(const Duration(seconds: 30)))) return cached;
    if (!microsoftConfigured) throw const ContactSourceException('Microsoft contacts are not set up in this build of the app.');
    try {
      final result = await _appAuth.authorizeAndExchangeCode(AuthorizationTokenRequest(
        microsoftClientId,
        microsoftRedirectUrl,
        serviceConfiguration: const AuthorizationServiceConfiguration(
          authorizationEndpoint: 'https://login.microsoftonline.com/common/oauth2/v2.0/authorize',
          tokenEndpoint: 'https://login.microsoftonline.com/common/oauth2/v2.0/token',
        ),
        scopes: const ['Contacts.Read'],
        promptValues: const ['select_account'],
      ));
      final token = result?.accessToken;
      if (token == null) throw const ContactSourceException('Microsoft did not give access.');
      _msToken = token;
      _msExpires = result?.accessTokenExpirationDateTime;
      return token;
    } on ContactSourceException {
      rethrow;
    } on Exception catch (e) {
      throw ContactSourceException('Microsoft sign-in failed: $e');
    }
  }

  @override
  Future<void> forget({required bool google}) async {
    if (google) {
      try {
        await _google.signOut();
      } on Exception {
        // nothing to clear
      }
    } else {
      _msToken = null;
      _msExpires = null;
    }
  }
}

/// The real thing, used by the app. Tests build [ContactServices] with fakes instead.
ContactServices createPlatformContactServices() => ContactServices(
      addressBook: PlatformAddressBook(),
      files: PlatformContactFileReader(),
      tokens: PlatformAccessTokens(microsoftClientId: AppConfig.microsoftClientId, microsoftRedirectUrl: AppConfig.microsoftRedirectUrl),
    );
