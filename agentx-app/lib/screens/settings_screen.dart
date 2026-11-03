import 'package:flutter/material.dart';

import '../app_state.dart';
import '../layout/responsive.dart';
import '../theme/app_theme.dart';
import '../widgets/ui.dart';
import '../chat/media_cache.dart';
import 'business_accounts_screen.dart';
import 'one_app_screen.dart';
import 'storage_screen.dart';

/// Where the customer sets things up: business accounts, the look of the app, and this device.
class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key, required this.state});

  final AppState state;

  Future<void> _signOut(BuildContext context) async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (c) => AppDialog(
        title: const Text('Sign out of this device?'),
        content: const Text('You will need a new code by SMS to sign in again.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('Stay signed in')),
          TextButton(onPressed: () => Navigator.pop(c, true), child: const Text('Sign out')),
        ],
      ),
    );
    if (ok == true) {
      try {
        await (await MediaCache.shared()).clear();   // do not leave this person's files on the phone
      } catch (_) {
        // the cache is best effort
      }
      await state.signOut();
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Settings')),
      body: SafeArea(
        top: false,
        child: ListenableBuilder(
          listenable: state,
          builder: (context, _) => CenteredList(maxWidth: 560, children: [
            const SectionTitle('For your business'),
            _Row(
              icon: Icons.storefront_outlined,
              title: 'Business accounts',
              subtitle: 'Paybill and Buy Goods shortcodes, tills and Daraja credentials, for STK Push, B2C, B2B, balance and more from the chat.',
              onTap: () => Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => BusinessAccountsScreen(state: state))),
            ),
            const SizedBox(height: 20),
            const SectionTitle('Your M-Pesa and your files'),
            _Row(
              icon: Icons.phone_android_outlined,
              title: 'My OneApp and M-Pesa',
              subtitle: 'How payments from your own M-Pesa work, and shortcuts to My OneApp and *334#.',
              onTap: () => Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => const OneAppScreen())),
            ),
            const SizedBox(height: 8),
            _Row(
              icon: Icons.folder_outlined,
              title: 'Files and storage',
              subtitle: 'Photos, voice notes and documents from the chat, on this phone and on the server.',
              onTap: () => Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => const StorageScreen())),
            ),
            const SizedBox(height: 20),
            const SectionTitle('This app'),
            _Row(
              icon: Icons.palette_outlined,
              title: 'Appearance',
              subtitle: state.theme.label,
              onTap: () => showThemeSheet(context, state),
            ),
            const SizedBox(height: 20),
            const SectionTitle('This device'),
            if (state.phone != null && state.phone!.isNotEmpty) ...[
              KeyValueList([MapEntry('Signed in as', state.phone!)]),
              const SizedBox(height: 8),
            ],
            _Row(icon: Icons.logout, title: 'Sign out', subtitle: 'You will need a new SMS code to come back.', onTap: () => _signOut(context)),
          ]),
        ),
      ),
    );
  }
}

class _Row extends StatelessWidget {
  const _Row({required this.icon, required this.title, required this.subtitle, required this.onTap});

  final IconData icon;
  final String title;
  final String subtitle;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final t = context.tk;
    final text = Theme.of(context).textTheme;
    return Panel(
      onTap: onTap,
      child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Icon(icon, color: t.p.accent),
        const SizedBox(width: 14),
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(title, style: text.titleMedium),
            const SizedBox(height: 2),
            Text(subtitle, style: text.bodySmall),
          ]),
        ),
        const SizedBox(width: 8),
        const Icon(Icons.chevron_right),
      ]),
    );
  }
}
