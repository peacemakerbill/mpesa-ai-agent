import 'dart:typed_data';

import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../models/profile.dart';
import '../theme/app_theme.dart';

/// The customer's picture in a circle, or their initials when there is none.
///
/// The picture is fetched once for each [Profile.pictureVersion] and kept in memory, so lists and headers that show it do not
/// download it again. A new version (after the customer changes the picture) is fetched by itself.
class ProfileAvatar extends StatefulWidget {
  const ProfileAvatar({super.key, required this.api, required this.profile, this.size = 56});

  final ApiClient api;
  final Profile? profile;
  final double size;

  /// Picture bytes by version, for this run of the app. A sign out should call [forget].
  static final Map<int, Uint8List> _memory = <int, Uint8List>{};

  static void forget() => _memory.clear();

  @override
  State<ProfileAvatar> createState() => _ProfileAvatarState();
}

class _ProfileAvatarState extends State<ProfileAvatar> {
  Uint8List? _bytes;
  int _loadedFor = -1;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void didUpdateWidget(ProfileAvatar old) {
    super.didUpdateWidget(old);
    if ((widget.profile?.pictureVersion ?? 0) != _loadedFor || widget.profile?.hasPicture != old.profile?.hasPicture) _load();
  }

  Future<void> _load() async {
    final profile = widget.profile;
    if (profile == null || !profile.hasPicture) {
      if (mounted) setState(() => _bytes = null);
      _loadedFor = profile?.pictureVersion ?? 0;
      return;
    }
    final version = profile.pictureVersion;
    _loadedFor = version;
    final cached = ProfileAvatar._memory[version];
    if (cached != null) {
      if (mounted) setState(() => _bytes = cached);
      return;
    }
    try {
      final bytes = await widget.api.profilePicture();
      if (bytes == null || !mounted || _loadedFor != version) return;
      ProfileAvatar._memory[version] = bytes;
      setState(() => _bytes = bytes);
    } on ApiException {
      // initials are shown instead
    }
  }

  @override
  Widget build(BuildContext context) {
    final t = context.tk;
    final size = widget.size;
    final initials = widget.profile?.initials ?? '';
    final bytes = _bytes;
    return Semantics(
      label: 'Profile picture',
      image: true,
      child: Container(
        width: size,
        height: size,
        alignment: Alignment.center,
        clipBehavior: Clip.antiAlias,
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          color: t.p.accentSoft,
          border: Border.all(color: t.p.accentLine),
        ),
        child: bytes != null
            ? Image.memory(bytes, width: size, height: size, fit: BoxFit.cover, gaplessPlayback: true)
            : initials.isNotEmpty
                ? Text(initials, style: t.numeric(size * 0.36, color: t.p.accent))
                : Icon(Icons.person_outline, size: size * 0.5, color: t.p.accent),
      ),
    );
  }
}
