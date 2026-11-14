import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// What the customer was doing when something went wrong with their contacts.
enum ContactAction { readPhone, chooser, readFile, google, microsoft }

/// True when this device has no code for the thing asked, for example the address book on a Linux or Windows computer,
/// where the plugin answers MissingPluginException. Trying again will never help, so the screen says so and points elsewhere.
bool contactFeatureMissing(Object error) => error is MissingPluginException || error is UnimplementedError || error is UnsupportedError;

/// A sentence for the customer. The technical text is only written to the debug log, never shown.
String contactProblem(Object error, ContactAction action) {
  debugPrint('Contacts ($action): $error');
  final missing = contactFeatureMissing(error);
  switch (action) {
    case ContactAction.readPhone:
      if (missing) return 'This device has no address book the app can read. Use Google, vCard or CSV, or Type it instead.';
      if (error is PlatformException) {
        return 'The app could not read the contacts. Check that it is allowed to, in your phone settings, or use the phone\'s own chooser.';
      }
      return 'Contacts could not be read. Try again, or use Google, vCard or CSV, or Type it instead.';
    case ContactAction.chooser:
      if (missing) return 'This device has no contact chooser. Use Google, vCard or CSV, or Type it instead.';
      return 'The phone\'s contact chooser could not open. Try again, or search by name instead.';
    case ContactAction.readFile:
      if (missing) return 'This device cannot open a file chooser. Use Type it instead.';
      return 'That file could not be read. Choose a .vcf or .csv file saved from your contacts.';
    case ContactAction.google:
      if (missing) return 'Google sign-in is not available on this device. Use vCard or CSV, or Type it instead.';
      return 'Google sign-in did not work. Check your internet connection and try again.';
    case ContactAction.microsoft:
      if (missing) return 'Microsoft sign-in is not available on this device. Use vCard or CSV, or Type it instead.';
      return 'Microsoft sign-in did not work. Check your internet connection and try again.';
  }
}
