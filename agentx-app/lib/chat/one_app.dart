import 'dart:io';

import 'package:android_intent_plus/android_intent.dart';
import 'package:flutter/services.dart';
import 'package:url_launcher/url_launcher.dart';

/// Android package names to try, in order, when opening Safaricom's My OneApp (the M-PESA app and MySafaricom merged in April 2026).
/// The old M-PESA app's listing was converted into My OneApp, so its package id is expected to still work, but this has
/// NOT been confirmed from a device: check it with `adb shell pm list packages | grep safaricom` and edit this list.
/// When none opens, the Play Store search is shown instead, so a wrong id here never leaves the button dead.
const List<String> oneAppAndroidPackages = ['com.safaricom.mpesa.lifestyle'];

/// The App Store page of My OneApp (the listing that used to be MySafaricom).
final Uri oneAppIosStore = Uri.parse('https://apps.apple.com/app/id1496784664');

final Uri oneAppPlaySearch = Uri.parse('https://play.google.com/store/search?q=My%20OneApp&c=apps');

/// Opens the phone dialer with a USSD code filled in. The person still presses call.
Uri ussdUri(String code) => Uri(scheme: 'tel', path: code);

/// What happened when asking the phone to open My OneApp.
enum OneAppResult { opened, openedStore, failed }

/// Opens My OneApp, or its store page when it is not installed. Returns what happened.
/// Only a launcher intent is used: nothing is sent to the app and nothing is read from it.
Future<OneAppResult> openOneApp({List<String> packages = oneAppAndroidPackages}) async {
  if (Platform.isAndroid) {
    for (final pkg in packages) {
      try {
        await AndroidIntent(action: 'android.intent.action.MAIN', category: 'android.intent.category.LAUNCHER', package: pkg).launch();
        return OneAppResult.opened;
      } on PlatformException {
        continue;   // not installed, or not the right id
      }
    }
    return await launchUrl(oneAppPlaySearch, mode: LaunchMode.externalApplication) ? OneAppResult.openedStore : OneAppResult.failed;
  }
  return await launchUrl(oneAppIosStore, mode: LaunchMode.externalApplication) ? OneAppResult.openedStore : OneAppResult.failed;
}

/// Opens the dialer on *334#. Returns false when the phone cannot make calls.
Future<bool> openMpesaUssd() => launchUrl(ussdUri('*334#'));
