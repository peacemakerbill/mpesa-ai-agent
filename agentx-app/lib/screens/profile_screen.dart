import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../api/api_client.dart';
import '../app_state.dart';
import '../chat/chat_device.dart';
import '../layout/responsive.dart';
import '../models/profile.dart';
import '../profile/profile_avatar.dart';
import '../profile/signup_flow.dart';
import '../theme/app_theme.dart';
import '../widgets/ui.dart';
import 'email_recipients_screen.dart';

/// The customer's own details: picture, names, email (with a code check), address, location and which emails they want.
///
/// Names, address and the email alert switches are saved with the Save button. The email address and the picture save at once,
/// because each has its own check. The phone number is the account and can not be changed here.
class ProfileScreen extends StatefulWidget {
  const ProfileScreen({super.key, required this.state});

  final AppState state;

  @override
  State<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends State<ProfileScreen> {
  final _first = TextEditingController();
  final _middle = TextEditingController();
  final _last = TextEditingController();
  final _address = TextEditingController();
  final _city = TextEditingController();
  final _county = TextEditingController();
  final _country = TextEditingController();

  Profile? _profile;
  String? _loadError;
  bool _saving = false;
  bool _busyPicture = false;
  bool _busyLocation = false;

  double? _latitude;
  double? _longitude;
  bool _clearLocation = false;
  bool _notifySignIn = true;
  bool _notifyPayments = true;

  ApiClient get _api => widget.state.api;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    for (final c in [_first, _middle, _last, _address, _city, _county, _country]) {
      c.dispose();
    }
    super.dispose();
  }

  Future<void> _load() async {
    setState(() => _loadError = null);
    try {
      final p = await _api.profile();
      if (!mounted) return;
      setState(() => _fill(p));
    } on ApiException catch (e) {
      if (mounted) setState(() => _loadError = e.message);
    }
  }

  /// Puts a profile from the server into the form.
  void _fill(Profile p) {
    _profile = p;
    _first.text = p.firstName;
    _middle.text = p.middleName;
    _last.text = p.lastName;
    _address.text = p.address;
    _city.text = p.city;
    _county.text = p.county;
    _country.text = p.country;
    _latitude = p.latitude;
    _longitude = p.longitude;
    _clearLocation = false;
    _notifySignIn = p.notifySignIn;
    _notifyPayments = p.notifyPayments;
  }

  /// Keeps what the server says about the email and the picture without throwing away what is typed in the other boxes.
  void _merge(Profile p) {
    setState(() => _profile = p);
  }

  void _say(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(SnackBar(content: Text(message)));
  }

  // ------------------------------------------------------------ save

  Future<void> _save() async {
    if (_first.text.trim().isEmpty || _last.text.trim().isEmpty) {
      _say('The first and last name can not be empty.');
      return;
    }
    setState(() => _saving = true);
    try {
      final saved = await _api.updateProfile(ProfileChanges(
        firstName: _first.text.trim(),
        middleName: _middle.text.trim(),
        lastName: _last.text.trim(),
        address: _address.text.trim(),
        city: _city.text.trim(),
        county: _county.text.trim(),
        country: _country.text.trim(),
        latitude: _clearLocation ? null : _latitude,
        longitude: _clearLocation ? null : _longitude,
        clearLocation: _clearLocation,
        notifySignIn: _notifySignIn,
        notifyPayments: _notifyPayments,
      ));
      if (!mounted) return;
      setState(() => _fill(saved));
      _say('Saved.');
    } on ApiException catch (e) {
      _say(e.message);
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }

  // ------------------------------------------------------------ location

  Future<void> _useMyLocation() async {
    setState(() => _busyLocation = true);
    try {
      final point = await widget.state.chatDevice.location();
      if (point == null || !mounted) return;
      setState(() {
        _latitude = point.latitude;
        _longitude = point.longitude;
        _clearLocation = false;
      });
      _say('Location added. Press Save to keep it.');
    } on ChatDeviceException catch (e) {
      _say(e.message);
    } finally {
      if (mounted) setState(() => _busyLocation = false);
    }
  }

  // ------------------------------------------------------------ picture

  Future<void> _choosePicture() async {
    final p = _profile;
    final choice = await showModalBottomSheet<String>(
      context: context,
      showDragHandle: true,
      builder: (c) => SafeArea(
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          ListTile(leading: const Icon(Icons.photo_camera_outlined), title: const Text('Take a photo'), onTap: () => Navigator.pop(c, 'camera')),
          ListTile(leading: const Icon(Icons.photo_library_outlined), title: const Text('Choose from your photos'), onTap: () => Navigator.pop(c, 'gallery')),
          if (p != null && p.hasPicture)
            ListTile(leading: const Icon(Icons.delete_outline), title: const Text('Remove the picture'), onTap: () => Navigator.pop(c, 'remove')),
        ]),
      ),
    );
    if (choice == null) return;
    setState(() => _busyPicture = true);
    try {
      if (choice == 'remove') {
        _merge(await _api.deleteProfilePicture());
        _say('Picture removed.');
        return;
      }
      final photo = choice == 'camera' ? await widget.state.chatDevice.photoFromCamera() : await widget.state.chatDevice.photoFromGallery();
      if (photo == null) return;
      final type = photo.mimeType.toLowerCase();
      if (type != 'image/jpeg' && type != 'image/jpg' && type != 'image/png') {
        _say('Choose a JPEG or PNG picture.');
        return;
      }
      _merge(await _api.uploadProfilePicture(photo.bytes, mimeType: type == 'image/png' ? 'image/png' : 'image/jpeg'));
      _say('Picture saved.');
    } on ChatDeviceException catch (e) {
      _say(e.message);
    } on ApiException catch (e) {
      _say(e.message);
    } finally {
      if (mounted) setState(() => _busyPicture = false);
    }
  }

  // ------------------------------------------------------------ email

  Future<void> _changeEmail() async {
    final email = await showDialog<String>(
      context: context,
      builder: (c) => _TextDialog(
        title: _profile?.hasEmail == true ? 'Change your email' : 'Add your email',
        label: 'Email address',
        initial: _profile?.pendingEmail.isNotEmpty == true ? _profile!.pendingEmail : '',
        keyboard: TextInputType.emailAddress,
        action: 'Send me a code',
        validate: (v) => looksLikeEmail(v) ? null : 'That email address does not look right.',
      ),
    );
    if (email == null) return;
    await _sendEmailCode(email.trim());
  }

  Future<void> _sendEmailCode(String email) async {
    try {
      await _api.requestEmailCode(email);
    } on ApiException catch (e) {
      _say(e.message);
      return;
    }
    if (!mounted) return;
    // The server keeps the new address as pending until the code is confirmed
    try {
      _merge(await _api.profile());
    } on ApiException {
      // the code dialog still works
    }
    if (!mounted) return;
    await _enterEmailCode(email);
  }

  Future<void> _enterEmailCode(String email) async {
    final verified = await showDialog<Profile>(
      context: context,
      builder: (c) => _CodeDialog(
        email: email,
        verify: (code) => _api.verifyEmail(code),
        resend: () => _api.requestEmailCode(email),
      ),
    );
    if (verified != null) {
      _merge(verified);
      _say('Your email is verified.');
    }
  }

  Future<void> _removeEmail() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (c) => AppDialog(
        title: const Text('Remove your email?'),
        content: const Text('Reports and alerts will stop reaching you by email. You can add it again at any time.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('Keep it')),
          TextButton(onPressed: () => Navigator.pop(c, true), child: const Text('Remove')),
        ],
      ),
    );
    if (ok != true) return;
    try {
      _merge(await _api.removeEmail());
      _say('Email removed.');
    } on ApiException catch (e) {
      _say(e.message);
    }
  }

  // ------------------------------------------------------------ build

  @override
  Widget build(BuildContext context) {
    final p = _profile;
    return Scaffold(
      appBar: AppBar(title: const Text('Your profile')),
      body: SafeArea(
        top: false,
        child: p == null
            ? (_loadError != null
                ? EmptyState(title: 'Could not load your profile', message: _loadError!, children: [
                    FilledButton(onPressed: _load, child: const Text('Try again')),
                  ])
                : const Center(child: CircularProgressIndicator()))
            : _form(context, p),
      ),
    );
  }

  Widget _form(BuildContext context, Profile p) {
    final text = Theme.of(context).textTheme;
    final t = context.tk;
    final spinner = SizedBox(height: 20, width: 20, child: CircularProgressIndicator(strokeWidth: 2, color: t.p.ink3));
    return CenteredList(maxWidth: 560, children: [
      Row(children: [
        Stack(clipBehavior: Clip.none, children: [
          ProfileAvatar(api: _api, profile: p, size: 76),
          Positioned(
            right: -6,
            bottom: -6,
            child: IconButton.filledTonal(
              onPressed: _busyPicture ? null : _choosePicture,
              icon: _busyPicture ? spinner : const Icon(Icons.edit_outlined, size: 18),
              tooltip: 'Change picture',
              visualDensity: VisualDensity.compact,
            ),
          ),
        ]),
        const SizedBox(width: 20),
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(p.fullName.isEmpty ? 'No name yet' : p.fullName, style: text.titleLarge),
            const SizedBox(height: 2),
            Text(p.phone, style: text.bodyMedium?.copyWith(color: t.p.ink2)),
          ]),
        ),
      ]),
      const SizedBox(height: 20),
      const SectionTitle('Your name'),
      _field(_first, 'First name', capitalize: true, hints: const [AutofillHints.givenName]),
      _field(_middle, 'Middle name (optional)', capitalize: true, hints: const [AutofillHints.middleName]),
      _field(_last, 'Last name', capitalize: true, hints: const [AutofillHints.familyName]),
      const SizedBox(height: 12),
      const SectionTitle('Email'),
      _emailPanel(context, p),
      const SizedBox(height: 12),
      const SectionTitle('Address'),
      _field(_address, 'Street or building', hints: const [AutofillHints.fullStreetAddress]),
      _field(_city, 'Town or city', capitalize: true, hints: const [AutofillHints.addressCity]),
      _field(_county, 'County', capitalize: true, hints: const [AutofillHints.addressState]),
      _field(_country, 'Country', capitalize: true, hints: const [AutofillHints.countryName]),
      _locationRow(context),
      const SizedBox(height: 12),
      const SectionTitle('Emails you want'),
      _switch('When I sign in', 'An email when your account signs in on a device.', _notifySignIn, p.hasEmail && p.emailVerified, (v) => setState(() => _notifySignIn = v)),
      _switch('When a payment is complete', 'An email with the details and the M-Pesa code.', _notifyPayments, p.hasEmail && p.emailVerified, (v) => setState(() => _notifyPayments = v)),
      if (!p.emailVerified) ...[
        const SizedBox(height: 4),
        Text('These work once your email is verified.', style: text.bodySmall),
      ],
      const SizedBox(height: 20),
      FilledButton(onPressed: _saving ? null : _save, child: _saving ? spinner : const Text('Save changes')),
      const SizedBox(height: 20),
      const SectionTitle('Reports by email'),
      Panel(
        onTap: () => Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => EmailRecipientsScreen(state: widget.state))),
        child: Row(children: [
          Icon(Icons.group_outlined, color: t.p.accent),
          const SizedBox(width: 14),
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text('Email addresses', style: text.titleMedium),
              const SizedBox(height: 2),
              Text('Keep the people you send reports to. In the chat say, for example, "send this report to my email as a PDF".', style: text.bodySmall),
            ]),
          ),
          const Icon(Icons.chevron_right),
        ]),
      ),
    ]);
  }

  Widget _field(TextEditingController controller, String label, {bool capitalize = false, List<String> hints = const []}) => Padding(
        padding: const EdgeInsets.only(bottom: 12),
        child: TextField(
          controller: controller,
          textCapitalization: capitalize ? TextCapitalization.words : TextCapitalization.sentences,
          textInputAction: TextInputAction.next,
          autofillHints: hints,
          decoration: InputDecoration(labelText: label),
        ),
      );

  Widget _switch(String title, String subtitle, bool value, bool enabled, ValueChanged<bool> onChanged) => SwitchListTile(
        contentPadding: EdgeInsets.zero,
        title: Text(title),
        subtitle: Text(subtitle),
        value: value,
        onChanged: enabled ? onChanged : null,
      );

  Widget _emailPanel(BuildContext context, Profile p) {
    final text = Theme.of(context).textTheme;
    final t = context.tk;
    return Panel(
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        if (!p.hasEmail && p.pendingEmail.isEmpty)
          Text('No email yet. Add one to get reports and alerts in your inbox.', style: text.bodyMedium)
        else if (p.hasEmail)
          Row(children: [
            Expanded(child: Text(p.email, style: text.titleMedium)),
            const SizedBox(width: 8),
            StatusTag(p.emailVerified ? 'Verified' : 'Not verified', tone: p.emailVerified ? Tone.good : Tone.warn),
          ]),
        if (p.pendingEmail.isNotEmpty) ...[
          const SizedBox(height: 8),
          Text('Waiting for the code sent to ${p.pendingEmail}.', style: text.bodyMedium?.copyWith(color: t.p.ink2)),
          Align(
            alignment: Alignment.centerLeft,
            child: TextButton(onPressed: () => _enterEmailCode(p.pendingEmail), child: const Text('Enter the code')),
          ),
        ],
        if (!p.emailDelivers) ...[
          const SizedBox(height: 8),
          const Callout('This server is not set up to send email yet, so codes and reports will not arrive. Ask the administrator to switch email on.', tone: Tone.warn),
        ],
        const SizedBox(height: 8),
        Wrap(spacing: 8, children: [
          OutlinedButton(onPressed: _changeEmail, child: Text(p.hasEmail ? 'Change email' : 'Add email')),
          if (p.hasEmail) TextButton(onPressed: _removeEmail, child: const Text('Remove')),
        ]),
      ]),
    );
  }

  Widget _locationRow(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final has = _latitude != null && _longitude != null && !_clearLocation;
    return Row(crossAxisAlignment: CrossAxisAlignment.center, children: [
      Expanded(
        child: Text(
          has ? 'Location saved (${_latitude!.toStringAsFixed(4)}, ${_longitude!.toStringAsFixed(4)})' : 'No location saved',
          style: text.bodyMedium,
        ),
      ),
      TextButton.icon(
        onPressed: _busyLocation ? null : _useMyLocation,
        icon: const Icon(Icons.my_location_outlined, size: 18),
        label: Text(has ? 'Update' : 'Use my location'),
      ),
      if (has) TextButton(onPressed: () => setState(() => _clearLocation = true), child: const Text('Clear')),
    ]);
  }
}

/// Asks for one line of text.
class _TextDialog extends StatefulWidget {
  const _TextDialog({
    required this.title,
    required this.label,
    required this.action,
    required this.validate,
    this.initial = '',
    this.keyboard = TextInputType.text,
  });

  final String title;
  final String label;
  final String action;
  final String initial;
  final TextInputType keyboard;
  final String? Function(String value) validate;

  @override
  State<_TextDialog> createState() => _TextDialogState();
}

class _TextDialogState extends State<_TextDialog> {
  late final TextEditingController _controller = TextEditingController(text: widget.initial);
  String? _error;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _submit() {
    final value = _controller.text.trim();
    final problem = widget.validate(value);
    if (problem != null) {
      setState(() => _error = problem);
      return;
    }
    Navigator.pop(context, value);
  }

  @override
  Widget build(BuildContext context) => AppDialog(
        title: Text(widget.title),
        content: TextField(
          controller: _controller,
          autofocus: true,
          keyboardType: widget.keyboard,
          decoration: InputDecoration(labelText: widget.label, errorText: _error),
          onSubmitted: (_) => _submit(),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
          TextButton(onPressed: _submit, child: Text(widget.action)),
        ],
      );
}

/// Asks for the 6 digit code that was emailed. Shows the server's reason when the code is wrong, and can send a new one.
class _CodeDialog extends StatefulWidget {
  const _CodeDialog({required this.email, required this.verify, required this.resend});

  final String email;
  final Future<Profile> Function(String code) verify;
  final Future<void> Function() resend;

  @override
  State<_CodeDialog> createState() => _CodeDialogState();
}

class _CodeDialogState extends State<_CodeDialog> {
  final _code = TextEditingController();
  bool _busy = false;
  String? _error;
  String? _info;

  @override
  void dispose() {
    _code.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final code = _code.text.trim();
    if (!RegExp(r'^\d{6}$').hasMatch(code)) {
      setState(() => _error = 'The code has 6 digits.');
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
      _info = null;
    });
    try {
      final profile = await widget.verify(code);
      if (mounted) Navigator.pop(context, profile);
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _again() async {
    setState(() {
      _busy = true;
      _error = null;
      _info = null;
    });
    try {
      await widget.resend();
      if (mounted) setState(() => _info = 'A new code was sent.');
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.message);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = context.tk;
    return AppDialog(
      title: const Text('Enter the code'),
      content: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text('We emailed a 6 digit code to ${widget.email}. Open your inbox and type it here. Check the spam folder if it is not there.'),
        const SizedBox(height: 12),
        TextField(
          controller: _code,
          autofocus: true,
          keyboardType: TextInputType.number,
          maxLength: 6,
          textAlign: TextAlign.center,
          style: t.numeric(24),
          inputFormatters: [FilteringTextInputFormatter.digitsOnly],
          decoration: InputDecoration(labelText: '6 digit code', counterText: '', errorText: _error),
          onSubmitted: (_) => _submit(),
        ),
        if (_info != null) Padding(padding: const EdgeInsets.only(top: 8), child: Text(_info!)),
      ]),
      actions: [
        TextButton(onPressed: _busy ? null : _again, child: const Text('Send a new code')),
        TextButton(onPressed: _busy ? null : () => Navigator.pop(context), child: const Text('Later')),
        TextButton(onPressed: _busy ? null : _submit, child: const Text('Verify')),
      ],
    );
  }
}
