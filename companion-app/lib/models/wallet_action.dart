/// One request prepared by the assistant, as companion-api returns it.
class WalletAction {
  const WalletAction({
    required this.id,
    required this.type,
    required this.status,
    required this.amount,
    required this.summary,
    required this.details,
    required this.warnings,
    required this.needsContact,
    this.label,
    this.source,
    this.receipt,
    this.expiresAt,
    this.approvedAt,
    this.verification = 'NONE',
  });

  final String id;
  final String type;
  final String status;
  final int amount;
  final String? label;
  final String? source;
  final String summary;
  final Map<String, String> details;
  final List<String> warnings;
  final bool needsContact;
  final String? receipt;
  final DateTime? expiresAt;
  final DateTime? approvedAt;

  /// NONE, REPORTED, SMS_AMOUNT or SMS_MATCHED. Even the highest only means a message on this phone matched.
  final String verification;

  factory WalletAction.fromJson(Map<String, dynamic> json) {
    final rawDetails = json['details'];
    final details = <String, String>{};
    if (rawDetails is Map) {
      rawDetails.forEach((k, v) {
        if (v != null) details[k.toString()] = v.toString();
      });
    }
    final rawWarnings = json['warnings'];
    return WalletAction(
      id: json['id'] as String,
      type: json['type'] as String,
      status: json['status'] as String,
      amount: (json['amount'] as num).toInt(),
      label: json['label'] as String?,
      source: json['source'] as String?,
      summary: (json['summary'] as String?) ?? '',
      details: details,
      warnings: rawWarnings is List ? rawWarnings.map((e) => e.toString()).toList() : const [],
      needsContact: json['needsContact'] == true,
      receipt: json['receipt'] as String?,
      expiresAt: json['expiresAt'] == null ? null : DateTime.tryParse(json['expiresAt'] as String),
      approvedAt: json['approvedAt'] == null ? null : DateTime.tryParse(json['approvedAt'] as String),
      verification: (json['verification'] as String?) ?? 'NONE',
    );
  }

  bool get awaitingApproval => status == 'CREATED';
  bool get approved => status == 'APPROVED';
  bool get handedOff => status == 'HANDED_OFF';
  bool get canFinishInMpesa => approved || handedOff;
  bool get isOpen => awaitingApproval || canFinishInMpesa;

  bool get smsBacked => verification == 'SMS_MATCHED' || verification == 'SMS_AMOUNT';

  /// A message could still be matched: before it is done, after a "not done" report, or when only the customer said so.
  bool get canCheckSms =>
      type != 'SCHEDULE_RATIBA' && !smsBacked && (canFinishInMpesa || status == 'NOT_DONE' || status == 'DONE_REPORTED');

  String? get verificationText {
    switch (verification) {
      case 'SMS_MATCHED':
        return 'Matches the M-Pesa message on this phone, including who it went to.';
      case 'SMS_AMOUNT':
        return 'Matches the amount in the M-Pesa message on this phone. Check the name in that message.';
      default:
        return null;
    }
  }

  String get statusText {
    switch (status) {
      case 'CREATED':
        return 'Waiting for your approval';
      case 'APPROVED':
        return 'Approved, finish in M-Pesa';
      case 'HANDED_OFF':
        return 'Opened in M-Pesa';
      case 'DONE_REPORTED':
        return 'Done, reported by you';
      case 'NOT_DONE':
        return 'Not done';
      case 'DECLINED':
        return 'Declined';
      case 'CANCELLED':
        return 'Cancelled';
      case 'EXPIRED':
        return 'Expired';
      default:
        return status;
    }
  }
}

/// Values for opening M-Pesa. They come from the server so they can be corrected without a new app release.
class HandoffConfig {
  const HandoffConfig({
    required this.ussdMain,
    required this.mpesaAndroidPackage,
    required this.mpesaIosUrl,
    required this.ussdTemplates,
  });

  final String ussdMain;
  final String mpesaAndroidPackage;
  final String mpesaIosUrl;
  final Map<String, String> ussdTemplates;

  static const HandoffConfig fallback = HandoffConfig(
    ussdMain: '*334#',
    mpesaAndroidPackage: '',
    mpesaIosUrl: '',
    ussdTemplates: {},
  );

  factory HandoffConfig.fromJson(Map<String, dynamic> json) {
    final templates = <String, String>{};
    final raw = json['ussdTemplates'];
    if (raw is Map) {
      raw.forEach((k, v) {
        if (v is String && v.trim().isNotEmpty) templates[k.toString()] = v.trim();
      });
    }
    String text(String key, [String fallback = '']) {
      final v = json[key];
      return v is String && v.trim().isNotEmpty ? v.trim() : fallback;
    }

    return HandoffConfig(
      ussdMain: text('ussdMain', '*334#'),
      mpesaAndroidPackage: text('mpesaAndroidPackage'),
      mpesaIosUrl: text('mpesaIosUrl'),
      ussdTemplates: templates,
    );
  }
}
