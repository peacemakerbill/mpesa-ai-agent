import 'package:http/http.dart' as http;

import 'finder_controller.dart';
import 'sources.dart';

/// Everything the contact finder needs, put together once so screens and tests can swap parts.
class ContactServices {
  ContactServices({
    required this.addressBook,
    required this.files,
    required this.tokens,
    http.Client? httpClient,
  })  : google = GoogleContactsClient(client: httpClient ?? http.Client(), tokens: tokens),
        microsoft = MicrosoftContactsClient(client: httpClient ?? http.Client(), tokens: tokens);

  final AddressBook addressBook;
  final ContactFileReader files;
  final AccessTokens tokens;
  final GoogleContactsClient google;
  final MicrosoftContactsClient microsoft;

  ContactFinderController newFinder({String query = ''}) => ContactFinderController(
        addressBook: addressBook,
        google: google,
        microsoft: microsoft,
        files: files,
        microsoftConfigured: tokens.microsoftConfigured,
        query: query,
      );
}
