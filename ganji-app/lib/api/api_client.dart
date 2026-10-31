import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:http/http.dart' as http;

import '../models/chat.dart';
import '../models/mpesa_sms.dart';
import '../models/wallet_action.dart';

class ApiException implements Exception {
  ApiException(this.status, this.message);

  final int status;
  final String message;

  bool get unauthorized => status == 401;

  @override
  String toString() => message;
}

class LinkResult {
  const LinkResult(this.token, this.phone, this.expiresAt);

  final String token;
  final String phone;
  final DateTime expiresAt;
}

/// What asking for a sign-in code returns. [devCode] is only filled when the server runs in test mode.
class CodeRequest {
  const CodeRequest(this.expiresAt, this.devCode);

  final DateTime expiresAt;
  final String? devCode;
}

/// A file from the chat, with the type the server stored it under.
class ChatFile {
  const ChatFile(this.bytes, this.mimeType);

  final Uint8List bytes;
  final String mimeType;
}

/// Talks to ganji-api. The phone number is never sent after linking, the server reads it from the token.
class ApiClient {
  ApiClient({required this.baseUrl, http.Client? client, this.tokenProvider})
      : _client = client ?? http.Client();

  final String baseUrl;
  final http.Client _client;
  final Future<String?> Function()? tokenProvider;

  static const _timeout = Duration(seconds: 20);

  /// Asks for a 6 digit code to be sent to [phone] by SMS.
  Future<CodeRequest> requestCode(String phone) async {
    final json = await _send('POST', '/api/auth/request-code', body: {'phone': phone}, auth: false);
    final devCode = json['devCode'];
    return CodeRequest(DateTime.parse(json['expiresAt'] as String), devCode is String && devCode.isNotEmpty ? devCode : null);
  }

  Future<LinkResult> verify({
    required String phone,
    required String code,
    required String deviceId,
    String? deviceName,
  }) async {
    final json = await _send('POST', '/api/auth/verify', body: {
      'phone': phone,
      'code': code,
      'deviceId': deviceId,
      if (deviceName != null && deviceName.isNotEmpty) 'deviceName': deviceName,
    }, auth: false);
    return LinkResult(
      json['token'] as String,
      json['phone'] as String,
      DateTime.parse(json['expiresAt'] as String),
    );
  }

  Future<void> signOut() async {
    await _send('DELETE', '/api/devices/me');
  }

  // ------------------------------------------------------------ chat

  /// Messages after number [after], oldest first. Call again with the last id you got.
  Future<ChatPage> chatMessages({int after = 0, int limit = 100}) async =>
      ChatPage.fromJson(await _send('GET', '/api/chat/messages?after=$after&limit=$limit'));

  /// Sends one message. [fields] has `kind` and what belongs to it, see ganji-api ChatController.
  Future<void> sendChat(Map<String, dynamic> fields) async {
    await _send('POST', '/api/chat/messages', body: fields);
  }

  /// Uploads a file and returns the media id to name in the next message.
  Future<String> uploadChatFile(Uint8List bytes, {required String mimeType, required String fileName}) async {
    final response = await _exec('POST', '/api/chat/media',
        bytes: bytes, contentType: mimeType, headers: {'X-File-Name': _asciiName(fileName)});
    final json = _decode(response);
    final id = json is Map ? json['mediaId'] : null;
    if (id is! String) throw ApiException(response.statusCode, 'The file was not accepted.');
    return id;
  }

  Future<ChatFile> chatFile(String mediaId) async {
    final response = await _exec('GET', '/api/chat/media/${Uri.encodeComponent(mediaId)}');
    return ChatFile(response.bodyBytes, (response.headers['content-type'] ?? 'application/octet-stream').split(';').first.trim());
  }

  Future<void> clearChat() async {
    await _send('DELETE', '/api/chat/messages');
  }

  /// HTTP header values must be plain ASCII, so other letters in a file name become underscores.
  static String _asciiName(String name) => name.replaceAll(RegExp(r'[^\x20-\x7e]'), '_');

  Future<HandoffConfig> config() async {
    final json = await _send('GET', '/api/config', auth: false);
    return HandoffConfig.fromJson(json);
  }

  Future<List<WalletAction>> actions() async {
    final json = await _send('GET', '/api/actions');
    final list = json['_list'] as List;
    return list.map((e) => WalletAction.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<WalletAction> action(String id) async => _action(await _send('GET', '/api/actions/$id'));

  Future<WalletAction> approve(String id, {required bool biometric}) async => _action(await _send(
      'POST', '/api/actions/$id/approve',
      body: {'method': biometric ? 'BIOMETRIC' : 'DEVICE_CREDENTIAL'}));

  Future<WalletAction> handOff(String id) async => _action(await _send('POST', '/api/actions/$id/handoff'));

  Future<WalletAction> decline(String id) async => _action(await _send('POST', '/api/actions/$id/decline'));

  Future<WalletAction> report(String id, {required bool done, String? receipt}) async => _action(await _send(
      'POST', '/api/actions/$id/report',
      body: {'outcome': done ? 'DONE' : 'NOT_DONE', if (receipt != null && receipt.isNotEmpty) 'receipt': receipt}));

  /// Sends what was read from an M-Pesa message on this phone. The message text itself is not sent.
  Future<WalletAction> smsEvidence(String id, MpesaSms sms) async => _action(await _send(
      'POST', '/api/actions/$id/sms-evidence',
      body: {
        'code': sms.code,
        'kind': sms.kindName,
        'amount': sms.amount,
        if (sms.payee != null) 'payee': sms.payee,
        if (sms.account != null) 'account': sms.account,
        'smsAt': sms.receivedAt.millisecondsSinceEpoch,
      }));

  WalletAction _action(Map<String, dynamic> json) => WalletAction.fromJson(json);

  Future<Map<String, dynamic>> _send(String method, String path, {Map<String, dynamic>? body, bool auth = true}) async {
    final response = await _exec(method, path, jsonBody: body, auth: auth);
    final decoded = _decode(response);
    if (decoded is Map<String, dynamic>) return decoded;
    if (decoded is List) return {'_list': decoded};
    return <String, dynamic>{};
  }

  dynamic _decode(http.Response response) {
    final text = utf8.decode(response.bodyBytes, allowMalformed: true);
    if (text.isEmpty) return null;
    try {
      return jsonDecode(text);
    } on FormatException {
      return null;
    }
  }

  /// Sends one request. Returns the response for a 2xx answer and throws [ApiException] for anything else.
  Future<http.Response> _exec(
    String method,
    String path, {
    Map<String, dynamic>? jsonBody,
    List<int>? bytes,
    String? contentType,
    Map<String, String>? headers,
    bool auth = true,
  }) async {
    final all = <String, String>{'Accept': '*/*'};
    if (jsonBody != null) all['Content-Type'] = 'application/json';
    if (bytes != null) all['Content-Type'] = contentType ?? 'application/octet-stream';
    if (headers != null) all.addAll(headers);
    if (auth) {
      final token = tokenProvider == null ? null : await tokenProvider!();
      if (token == null) throw ApiException(401, 'Please sign in first.');
      all['Authorization'] = 'Bearer $token';
    }
    final request = http.Request(method, Uri.parse('$baseUrl$path'))..headers.addAll(all);
    if (jsonBody != null) request.body = jsonEncode(jsonBody);
    if (bytes != null) request.bodyBytes = bytes;

    final http.Response response;
    try {
      response = await http.Response.fromStream(await _client.send(request).timeout(_timeout));
    } on TimeoutException {
      throw ApiException(0, 'The service took too long to answer. Please try again.');
    } on Exception {
      throw ApiException(0, 'Could not reach the service. Check your internet connection.');
    }
    if (response.statusCode >= 200 && response.statusCode < 300) return response;

    final decoded = _decode(response);
    var message = decoded is Map && decoded['message'] is String ? decoded['message'] as String : '';
    if (message.isEmpty) {
      message = response.statusCode == 401
          ? 'Please sign in again.'
          : response.statusCode == 429
              ? 'Too many tries. Please wait a moment.'
              : response.statusCode >= 500
                  ? 'The service had a problem. Please try again.'
                  : 'The request was not accepted.';
    }
    throw ApiException(response.statusCode, message);
  }
}
