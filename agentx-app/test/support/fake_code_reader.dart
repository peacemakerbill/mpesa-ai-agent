import 'dart:async';

import 'package:agentx_app/services/code_reader.dart';

/// Stands in for the phone's message reader.
class FakeCodeReader implements CodeReader {
  FakeCodeReader({this.hash = 'FA+9qCX9VSu', this.isSupported = true, this.hang = false});

  final String? hash;
  final bool isSupported;
  final bool hang;
  void Function(String code)? onCode;
  int listens = 0;
  int stops = 0;

  @override
  bool get supported => isSupported;

  @override
  Future<String?> listen(void Function(String code) onCode) async {
    listens++;
    if (hang) return Completer<String?>().future;
    if (!isSupported) return null;
    this.onCode = onCode;
    return hash;
  }

  @override
  Future<void> stop() async {
    stops++;
  }

  /// The message arrives.
  void arrives(String code) => onCode?.call(code);
}
