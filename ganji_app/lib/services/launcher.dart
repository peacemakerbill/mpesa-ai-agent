// PLACE THIS FILE AT: ganji-app/lib/services/launcher.dart   (replaces the empty file of the same name)
import 'package:android_intent_plus/android_intent.dart';
import 'package:android_intent_plus/flag.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:url_launcher/url_launcher.dart';

/// Opens things outside the app: web links, the phone's dialler, the M-Pesa app, and the clipboard.
///
/// The methods answer true or false instead of throwing, so a screen can show one plain sentence when
/// something cannot be opened (for example the dialler on a computer, or an M-Pesa app that is not installed).
class Launcher {
  const Launcher();

  /// Opens an http or https link in the browser. Any other kind of link is refused.
  Future<bool> openUrl(String url) async {
    final uri = Uri.tryParse(url.trim());
    if (uri == null || !(uri.isScheme('https') || uri.isScheme('http'))) return false;
    return _open(uri);
  }

  /// Opens the dialler with a code already typed in. [telUri] looks like tel:*334%23.
  /// The customer still presses call. Computers have no dialler, so this answers false there.
  Future<bool> dial(String telUri) async {
    final uri = Uri.tryParse(telUri.trim());
    if (uri == null || !uri.isScheme('tel')) return false;
    return _open(uri);
  }

  /// Opens the M-Pesa app. Android needs the package name, iPhone needs the app's link.
  /// Both come from the server, so they can be corrected without a new release.
  Future<bool> openMpesaApp({required String androidPackage, required String iosUrl}) async {
    try {
      if (kIsWeb) return false;
      if (defaultTargetPlatform == TargetPlatform.android) {
        if (androidPackage.isEmpty) return false;
        final intent = AndroidIntent(
          action: 'android.intent.action.MAIN',
          category: 'android.intent.category.LAUNCHER',
          package: androidPackage,
          flags: <int>[Flag.FLAG_ACTIVITY_NEW_TASK],
        );
        if (await intent.canResolveActivity() != true) return false;
        await intent.launch();
        return true;
      }
      if (defaultTargetPlatform == TargetPlatform.iOS) {
        final uri = Uri.tryParse(iosUrl.trim());
        if (uri == null || iosUrl.trim().isEmpty) return false;
        return _open(uri);
      }
    } catch (_) {
      // Fall through: the caller shows a plain message.
    }
    return false;
  }

  /// Puts [text] on the clipboard, for the copy buttons next to the guided steps.
  Future<void> copy(String text) => Clipboard.setData(ClipboardData(text: text));

  Future<bool> _open(Uri uri) async {
    try {
      return await launchUrl(uri, mode: LaunchMode.externalApplication);
    } catch (_) {
      return false;
    }
  }
}
