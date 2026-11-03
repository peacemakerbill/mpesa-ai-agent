import 'dart:io' show Platform;

import 'package:flutter_sms_inbox/flutter_sms_inbox.dart';
import 'package:permission_handler/permission_handler.dart';

import '../models/sms_match.dart';

/// Reads M-Pesa messages from the phone's inbox, on request. Android only: iOS does not let apps read messages.
///
/// What it does not do: it does not run in the background, does not listen for new messages, does not read messages
/// from anyone but the sender "MPESA", and keeps nothing. The messages are looked at, matched on the phone, and dropped.
abstract class SmsReader {
  bool get supported;

  Future<bool> hasPermission();

  Future<bool> requestPermission();

  /// Messages from the M-Pesa sender received after [since], newest first.
  Future<List<RawSms>> recent({required DateTime since, int limit = 30});
}

class DeviceSmsReader implements SmsReader {
  @override
  bool get supported => Platform.isAndroid;

  @override
  Future<bool> hasPermission() async => supported && await Permission.sms.status.isGranted;

  @override
  Future<bool> requestPermission() async => supported && (await Permission.sms.request()).isGranted;

  @override
  Future<List<RawSms>> recent({required DateTime since, int limit = 30}) async {
    if (!await hasPermission()) return const [];
    final messages = await SmsQuery().querySms(kinds: [SmsQueryKind.inbox], address: 'MPESA', count: limit);
    return messages
        .where((m) => m.date != null && m.date!.isAfter(since))
        .map((m) => RawSms(address: m.address, body: m.body ?? '', date: m.date))
        .toList();
  }
}
