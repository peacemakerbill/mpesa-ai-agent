final RegExp _uuid = RegExp(r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$');

/// The id in a link the assistant sent for a business request. Accepted shapes:
///   https://host/b/{id}   and   agentx://b/{id}
/// Returns null for anything else, so a strange link can never steer the app.
String? parseBusinessLink(Uri uri, {String scheme = 'agentx'}) {
  String? id;
  if (uri.scheme == 'https' || uri.scheme == 'http') {
    final s = uri.pathSegments;
    if (s.length == 2 && s[0] == 'b') id = s[1];
  } else if (uri.scheme == scheme || uri.scheme == 'agentx') {
    if (uri.host == 'b' && uri.pathSegments.length == 1) id = uri.pathSegments[0];
  }
  if (id == null || !_uuid.hasMatch(id)) return null;
  return id.toLowerCase();
}

/// Like [parseBusinessLink] for a link that sits inside other text, such as a message in the chat.
String? businessIdIn(String text, {String scheme = 'agentx'}) {
  for (final word in text.split(RegExp(r'\s+'))) {
    final uri = Uri.tryParse(word.replaceAll(RegExp(r'[).,;]+$'), ''));
    if (uri == null || !uri.hasScheme) continue;
    final id = parseBusinessLink(uri, scheme: scheme);
    if (id != null) return id;
  }
  return null;
}

/// Where a request on a business account has got to.
enum OperationStage { waiting, sent, done, failed, declined, expired, unclear }

/// One thing asked of Safaricom on a business account from the chat: a payout, an STK Push, a balance check.
/// There are no secrets in here. The server never sends them.
class BusinessOperation {
  const BusinessOperation({
    required this.id,
    required this.accountLabel,
    required this.type,
    required this.typeLabel,
    required this.status,
    required this.amount,
    required this.summary,
    required this.params,
    required this.needsApproval,
    required this.receipt,
    required this.resultText,
    required this.failure,
    required this.createdAt,
    required this.expiresAt,
    required this.completedAt,
  });

  final String id;
  final String accountLabel;
  final String type;
  final String typeLabel;
  final String status;
  final int amount;
  final String summary;
  final Map<String, String> params;

  /// True while the server is waiting for this phone to approve it.
  final bool needsApproval;
  final String receipt;
  final String resultText;
  final String failure;
  final DateTime? createdAt;
  final DateTime? expiresAt;
  final DateTime? completedAt;

  factory BusinessOperation.fromJson(Map<String, dynamic> json) {
    final raw = json['params'];
    return BusinessOperation(
      id: json['id'] as String? ?? '',
      accountLabel: json['accountLabel'] as String? ?? '',
      type: json['type'] as String? ?? '',
      typeLabel: json['typeLabel'] as String? ?? '',
      status: json['status'] as String? ?? '',
      amount: (json['amount'] as num?)?.toInt() ?? 0,
      summary: json['summary'] as String? ?? '',
      params: raw is Map ? {for (final e in raw.entries) '${e.key}': '${e.value}'} : const {},
      needsApproval: json['needsApproval'] == true,
      receipt: json['receipt'] as String? ?? '',
      resultText: json['resultText'] as String? ?? '',
      failure: json['failure'] as String? ?? '',
      createdAt: _time(json['createdAt']),
      expiresAt: _time(json['expiresAt']),
      completedAt: _time(json['completedAt']),
    );
  }

  static DateTime? _time(Object? value) => value is String ? DateTime.tryParse(value)?.toLocal() : null;

  OperationStage get stage {
    switch (status) {
      case 'PENDING_APPROVAL':
        return OperationStage.waiting;
      case 'SUBMITTED':
        return OperationStage.sent;
      case 'SUCCEEDED':
        return OperationStage.done;
      case 'FAILED':
        return OperationStage.failed;
      case 'DECLINED':
        return OperationStage.declined;
      case 'EXPIRED':
        return OperationStage.expired;
      default:
        return OperationStage.unclear;
    }
  }

  /// Approve and Decline are offered only while the server is waiting and the time has not run out.
  bool canDecide(DateTime now) => needsApproval && stage == OperationStage.waiting && (expiresAt == null || now.isBefore(expiresAt!));

  /// Money leaves the business account. Shown with a firmer warning.
  bool get movesMoneyOut => type == 'B2C' || type == 'B2POCHI' || type == 'B2B' || type == 'REVERSAL';

  String get stageLabel {
    switch (stage) {
      case OperationStage.waiting:
        return 'Needs your approval';
      case OperationStage.sent:
        return 'Sent to Safaricom';
      case OperationStage.done:
        return 'Done';
      case OperationStage.failed:
        return 'Failed';
      case OperationStage.declined:
        return 'Declined';
      case OperationStage.expired:
        return 'Expired';
      case OperationStage.unclear:
        return 'Not sure';
    }
  }

  /// The details to show as rows, with the plain names the person knows. Values come from what was prepared.
  List<MapEntry<String, String>> get details {
    const names = {
      'phone': 'Phone',
      'reference': 'Reference',
      'description': 'Description',
      'remarks': 'Note',
      'occasion': 'Occasion',
      'command': 'Payout type',
      'receiver': 'Paybill or Till',
      'accountReference': 'Account number',
      'receipt': 'M-Pesa code',
      'start': 'From',
      'end': 'To',
      'merchantName': 'Shown on QR',
    };
    final rows = <MapEntry<String, String>>[];
    for (final e in names.entries) {
      final v = params[e.key];
      if (v != null && v.isNotEmpty) rows.add(MapEntry(e.value, v));
    }
    return rows;
  }
}
