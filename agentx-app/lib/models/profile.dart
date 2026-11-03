/// The customer's profile as agentx-api returns it. The phone number is the account and can not be changed.
class Profile {
  const Profile({
    required this.phone,
    required this.firstName,
    required this.middleName,
    required this.lastName,
    required this.fullName,
    required this.initials,
    required this.email,
    required this.emailVerified,
    required this.pendingEmail,
    required this.address,
    required this.city,
    required this.county,
    required this.country,
    required this.latitude,
    required this.longitude,
    required this.hasPicture,
    required this.pictureVersion,
    required this.notifySignIn,
    required this.notifyPayments,
    required this.complete,
    required this.emailDelivers,
  });

  final String phone;
  final String firstName;
  final String middleName;
  final String lastName;
  final String fullName;
  final String initials;
  final String email;
  final bool emailVerified;

  /// An address that was asked for but not confirmed yet. The verified [email] stays in use until it is confirmed.
  final String pendingEmail;
  final String address;
  final String city;
  final String county;
  final String country;
  final double? latitude;
  final double? longitude;
  final bool hasPicture;

  /// Changes whenever the picture changes, so a saved copy knows when it is old.
  final int pictureVersion;
  final bool notifySignIn;
  final bool notifyPayments;
  final bool complete;

  /// False when the server only writes emails to its log. The app then says that emails are not set up.
  final bool emailDelivers;

  bool get hasEmail => email.isNotEmpty;
  bool get hasLocation => latitude != null && longitude != null;

  /// A short name for greetings: the first name, or the phone number when no name is saved yet.
  String get displayName => firstName.isNotEmpty ? firstName : phone;

  factory Profile.fromJson(Map<String, dynamic> json) => Profile(
        phone: _text(json['phone']),
        firstName: _text(json['firstName']),
        middleName: _text(json['middleName']),
        lastName: _text(json['lastName']),
        fullName: _text(json['fullName']),
        initials: _text(json['initials']),
        email: _text(json['email']),
        emailVerified: json['emailVerified'] == true,
        pendingEmail: _text(json['pendingEmail']),
        address: _text(json['address']),
        city: _text(json['city']),
        county: _text(json['county']),
        country: _text(json['country']),
        latitude: (json['latitude'] as num?)?.toDouble(),
        longitude: (json['longitude'] as num?)?.toDouble(),
        hasPicture: json['hasPicture'] == true,
        pictureVersion: (json['pictureVersion'] as num?)?.toInt() ?? 0,
        notifySignIn: json['notifySignIn'] != false,
        notifyPayments: json['notifyPayments'] != false,
        complete: json['complete'] == true,
        emailDelivers: json['emailDelivers'] != false,
      );

  static String _text(Object? value) => value is String ? value : '';
}

/// The changes the profile screen saves. A field left null stays as it is. An empty text clears an optional field.
class ProfileChanges {
  const ProfileChanges({
    this.firstName,
    this.middleName,
    this.lastName,
    this.address,
    this.city,
    this.county,
    this.country,
    this.latitude,
    this.longitude,
    this.clearLocation = false,
    this.notifySignIn,
    this.notifyPayments,
  });

  final String? firstName;
  final String? middleName;
  final String? lastName;
  final String? address;
  final String? city;
  final String? county;
  final String? country;
  final double? latitude;
  final double? longitude;
  final bool clearLocation;
  final bool? notifySignIn;
  final bool? notifyPayments;

  Map<String, dynamic> toJson() => {
        if (firstName != null) 'firstName': firstName,
        if (middleName != null) 'middleName': middleName,
        if (lastName != null) 'lastName': lastName,
        if (address != null) 'address': address,
        if (city != null) 'city': city,
        if (county != null) 'county': county,
        if (country != null) 'country': country,
        if (latitude != null && longitude != null) 'latitude': latitude,
        if (latitude != null && longitude != null) 'longitude': longitude,
        if (clearLocation) 'clearLocation': true,
        if (notifySignIn != null) 'notifySignIn': notifySignIn,
        if (notifyPayments != null) 'notifyPayments': notifyPayments,
      };
}

/// An email address the customer keeps to send reports to.
class EmailRecipient {
  const EmailRecipient({required this.id, required this.email, required this.name});

  final String id;
  final String email;
  final String name;

  String get label => name.isEmpty ? email : '$name ($email)';

  factory EmailRecipient.fromJson(Map<String, dynamic> json) => EmailRecipient(
        id: json['id'] as String? ?? '',
        email: json['email'] as String? ?? '',
        name: json['name'] as String? ?? '',
      );
}

/// What pasting or uploading a list of addresses did.
class RecipientImport {
  const RecipientImport({
    required this.added,
    required this.alreadySaved,
    required this.invalid,
    required this.invalidSamples,
    required this.limitReached,
  });

  final int added;
  final int alreadySaved;
  final int invalid;
  final List<String> invalidSamples;
  final bool limitReached;

  /// One or two plain sentences for the customer.
  String get summary {
    final parts = <String>[
      added == 0 ? 'No new addresses were saved.' : (added == 1 ? '1 address was saved.' : '$added addresses were saved.'),
      if (alreadySaved > 0) (alreadySaved == 1 ? '1 was already on your list.' : '$alreadySaved were already on your list.'),
      if (invalid > 0) (invalid == 1 ? '1 entry was not an email address.' : '$invalid entries were not email addresses.'),
      if (limitReached) 'Your list is full, so some addresses were left out.',
    ];
    return parts.join(' ');
  }

  factory RecipientImport.fromJson(Map<String, dynamic> json) => RecipientImport(
        added: (json['added'] as num?)?.toInt() ?? 0,
        alreadySaved: (json['alreadySaved'] as num?)?.toInt() ?? 0,
        invalid: (json['invalid'] as num?)?.toInt() ?? 0,
        invalidSamples: json['invalidSamples'] is List ? (json['invalidSamples'] as List).whereType<String>().toList() : const <String>[],
        limitReached: json['limitReached'] == true,
      );
}
