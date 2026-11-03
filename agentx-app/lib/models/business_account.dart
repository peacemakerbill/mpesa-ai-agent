/// Which Safaricom environment an account belongs to.
enum DarajaEnv { sandbox, production }

extension DarajaEnvX on DarajaEnv {
  String get wire => this == DarajaEnv.sandbox ? 'SANDBOX' : 'PRODUCTION';
  String get label => this == DarajaEnv.sandbox ? 'Sandbox' : 'Production';

  static DarajaEnv parse(String? value) => value?.toUpperCase() == 'PRODUCTION' ? DarajaEnv.production : DarajaEnv.sandbox;
}

/// How customers pay the business.
enum ShortcodeKind { paybill, buyGoods }

extension ShortcodeKindX on ShortcodeKind {
  String get wire => this == ShortcodeKind.paybill ? 'PAYBILL' : 'BUY_GOODS';
  String get label => this == ShortcodeKind.paybill ? 'Paybill' : 'Buy Goods';

  static ShortcodeKind parse(String? value) => value?.toUpperCase() == 'BUY_GOODS' ? ShortcodeKind.buyGoods : ShortcodeKind.paybill;
}

/// One thing the saved credentials allow, for example STK Push. [needs] names what is missing when it is not ready.
class AccountCapability {
  const AccountCapability({required this.code, required this.label, required this.ready, required this.needs});

  final String code;
  final String label;
  final bool ready;
  final String needs;

  factory AccountCapability.fromJson(Map<String, dynamic> json) => AccountCapability(
        code: json['code'] as String? ?? '',
        label: json['label'] as String? ?? '',
        ready: json['ready'] == true,
        needs: json['needs'] as String? ?? '',
      );
}

/// A business account as agentx-api returns it. There are never any secrets in here, only whether each one is saved.
class BusinessAccount {
  const BusinessAccount({
    required this.id,
    required this.label,
    required this.environment,
    required this.kind,
    required this.shortcode,
    required this.tillNumber,
    required this.accountReference,
    required this.parentId,
    required this.inheritsCredentials,
    required this.consumerKeyHint,
    required this.hasConsumerSecret,
    required this.initiatorName,
    required this.hasInitiatorPassword,
    required this.hasPasskey,
    required this.capabilities,
    required this.verifiedAt,
  });

  final String id;
  final String label;
  final DarajaEnv environment;
  final ShortcodeKind kind;
  final String shortcode;
  final String tillNumber;
  final String accountReference;
  final String? parentId;

  /// True for a branch. The credentials shown belong to its parent.
  final bool inheritsCredentials;
  final String consumerKeyHint;
  final bool hasConsumerSecret;
  final String initiatorName;
  final bool hasInitiatorPassword;
  final bool hasPasskey;
  final List<AccountCapability> capabilities;
  final DateTime? verifiedAt;

  bool get isBranch => parentId != null;
  bool get hasKey => consumerKeyHint.isNotEmpty;

  /// The number the customer pays: the till for a Buy Goods branch, otherwise the shortcode.
  String get payTo => kind == ShortcodeKind.buyGoods && tillNumber.isNotEmpty ? tillNumber : shortcode;

  /// A short line for lists, for example "Paybill 174379" or "Till 123456 under 174379".
  String get headline {
    if (kind == ShortcodeKind.buyGoods) {
      return tillNumber.isEmpty ? 'Buy Goods $shortcode' : 'Till $tillNumber, shortcode $shortcode';
    }
    return accountReference.isEmpty ? 'Paybill $shortcode' : 'Paybill $shortcode, account $accountReference';
  }

  int get readyCount => capabilities.where((c) => c.ready).length;

  factory BusinessAccount.fromJson(Map<String, dynamic> json) {
    final caps = json['capabilities'];
    final verified = json['verifiedAt'];
    return BusinessAccount(
      id: json['id'] as String,
      label: json['label'] as String? ?? '',
      environment: DarajaEnvX.parse(json['environment'] as String?),
      kind: ShortcodeKindX.parse(json['shortcodeType'] as String?),
      shortcode: json['shortcode'] as String? ?? '',
      tillNumber: json['tillNumber'] as String? ?? '',
      accountReference: json['accountReference'] as String? ?? '',
      parentId: json['parentId'] as String?,
      inheritsCredentials: json['inheritsCredentials'] == true,
      consumerKeyHint: json['consumerKeyHint'] as String? ?? '',
      hasConsumerSecret: json['hasConsumerSecret'] == true,
      initiatorName: json['initiatorName'] as String? ?? '',
      hasInitiatorPassword: json['hasInitiatorPassword'] == true,
      hasPasskey: json['hasPasskey'] == true,
      capabilities: caps is List ? caps.whereType<Map<String, dynamic>>().map(AccountCapability.fromJson).toList() : const [],
      verifiedAt: verified is String ? DateTime.tryParse(verified) : null,
    );
  }
}

/// What asking Safaricom to check the saved key and secret returned.
class VerifyOutcome {
  const VerifyOutcome({required this.ok, required this.message, required this.securityCredentialReady, required this.securityCredentialMessage});

  final bool ok;
  final String message;
  final bool securityCredentialReady;
  final String securityCredentialMessage;

  factory VerifyOutcome.fromJson(Map<String, dynamic> json) => VerifyOutcome(
        ok: json['ok'] == true,
        message: json['message'] as String? ?? '',
        securityCredentialReady: json['securityCredentialReady'] == true,
        securityCredentialMessage: json['securityCredentialMessage'] as String? ?? '',
      );
}

/// One row of a CSV upload and what would happen to it.
class ImportRow {
  const ImportRow({required this.row, required this.label, required this.environment, required this.kind, required this.shortcode, required this.tillNumber, required this.accountReference, required this.parent, required this.action});

  final int row;
  final String label;
  final String environment;
  final String kind;
  final String shortcode;
  final String tillNumber;
  final String accountReference;
  final String parent;

  /// "create" or "update".
  final String action;

  factory ImportRow.fromJson(Map<String, dynamic> json) => ImportRow(
        row: (json['row'] as num?)?.toInt() ?? 0,
        label: json['label'] as String? ?? '',
        environment: json['environment'] as String? ?? '',
        kind: json['shortcodeType'] as String? ?? '',
        shortcode: json['shortcode'] as String? ?? '',
        tillNumber: json['tillNumber'] as String? ?? '',
        accountReference: json['accountReference'] as String? ?? '',
        parent: json['parent'] as String? ?? '',
        action: json['action'] as String? ?? '',
      );
}

/// One problem found in a CSV file. Row 1 is the header line, row 0 means the whole file.
class ImportIssue {
  const ImportIssue({required this.row, required this.field, required this.message});

  final int row;
  final String field;
  final String message;

  String get where => row <= 0 ? 'File' : (row == 1 ? 'Header' : 'Row $row');

  factory ImportIssue.fromJson(Map<String, dynamic> json) => ImportIssue(
        row: (json['row'] as num?)?.toInt() ?? 0,
        field: json['field'] as String? ?? '',
        message: json['message'] as String? ?? '',
      );
}

/// What a CSV upload did, or would do when [dryRun] is true. Nothing is saved while there is any issue.
class ImportResult {
  const ImportResult({required this.dryRun, required this.saved, required this.total, required this.created, required this.updated, required this.rows, required this.issues, required this.ignoredColumns});

  final bool dryRun;
  final bool saved;
  final int total;
  final int created;
  final int updated;
  final List<ImportRow> rows;
  final List<ImportIssue> issues;
  final List<String> ignoredColumns;

  bool get clean => issues.isEmpty;

  factory ImportResult.fromJson(Map<String, dynamic> json) {
    List<T> list<T>(String key, T Function(Map<String, dynamic>) make) {
      final raw = json[key];
      return raw is List ? raw.whereType<Map<String, dynamic>>().map(make).toList() : <T>[];
    }

    final ignored = json['ignoredColumns'];
    return ImportResult(
      dryRun: json['dryRun'] == true,
      saved: json['saved'] == true,
      total: (json['total'] as num?)?.toInt() ?? 0,
      created: (json['created'] as num?)?.toInt() ?? 0,
      updated: (json['updated'] as num?)?.toInt() ?? 0,
      rows: list('rows', ImportRow.fromJson),
      issues: list('issues', ImportIssue.fromJson),
      ignoredColumns: ignored is List ? ignored.map((e) => e.toString()).toList() : const [],
    );
  }
}

/// The columns of the CSV file, in the order of the template. Used by the instructions on the import screen.
class CsvColumn {
  const CsvColumn(this.name, this.need, this.help);

  final String name;
  final String need;
  final String help;
}

const List<CsvColumn> businessCsvColumns = [
  CsvColumn('label', 'Optional', 'A name you will recognise, for example "Main shop till".'),
  CsvColumn('environment', 'Head rows', 'sandbox or production. A branch row takes it from its head, fill it only when the head exists in both.'),
  CsvColumn('shortcode_type', 'Head rows', 'paybill or buy_goods. A branch row takes it from its head.'),
  CsvColumn('shortcode', 'Head rows', 'The 5 to 7 digit shortcode. Leave empty on a branch row, name the head in parent_shortcode.'),
  CsvColumn('till_number', 'Buy Goods branches', 'The 5 to 7 digit till of the branch. Leave empty on a head row.'),
  CsvColumn('account_reference', 'Paybill branches', 'Up to 12 letters or digits. Leave empty on a head row.'),
  CsvColumn('parent_shortcode', 'Branch rows', 'The shortcode of the head account this row belongs to. Leave empty on a head row.'),
  CsvColumn('consumer_key', 'Head rows', 'From your Daraja app. A branch row must leave it empty.'),
  CsvColumn('consumer_secret', 'Head rows', 'From your Daraja app. Leave empty on an existing account to keep the saved one.'),
  CsvColumn('initiator_name', 'For B2C, B2B, balance', 'The API operator username. Head rows only.'),
  CsvColumn('initiator_password', 'For B2C, B2B, balance', 'Saved encrypted. The security credential is made from it on the server.'),
  CsvColumn('passkey', 'For STK Push', 'The Lipa Na M-Pesa passkey. Head rows only.'),
];
