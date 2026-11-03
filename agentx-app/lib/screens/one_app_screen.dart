import 'package:flutter/material.dart';

import '../chat/one_app.dart';
import '../layout/responsive.dart';
import '../widgets/ui.dart';

/// How the chat relates to the person's own M-Pesa, and shortcuts to My OneApp and USSD.
///
/// There is deliberately no "connect my M-Pesa account" button. Safaricom publishes no way for another app to link to a
/// personal M-Pesa account and move money on its own, and Daraja Agent-X does not want to: every payment from a personal
/// account is approved here with fingerprint or phone lock and finished in M-Pesa with the PIN.
class OneAppScreen extends StatelessWidget {
  const OneAppScreen({super.key});

  Future<void> _openOneApp(BuildContext context) async {
    final result = await openOneApp();
    if (!context.mounted) return;
    final message = switch (result) {
      OneAppResult.opened => null,
      OneAppResult.openedStore => 'My OneApp did not open, so its store page was shown. Install or update it there.',
      OneAppResult.failed => 'This phone could not open My OneApp or its store page.',
    };
    if (message != null) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
  }

  Future<void> _dial(BuildContext context) async {
    final ok = await openMpesaUssd();
    if (!ok && context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('This device cannot dial. On a phone, dial *334#.')));
    }
  }

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    return Scaffold(
      appBar: AppBar(title: const Text('My OneApp and M-Pesa')),
      body: SafeArea(
        top: false,
        child: CenteredList(maxWidth: 560, children: [
          const SectionTitle('Your own M-Pesa'),
          Panel(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text('How a payment from the chat works', style: text.titleSmall),
              const SizedBox(height: 8),
              Text('1. You ask in the chat.\n'
                  '2. The assistant prepares the request. It cannot send anything.\n'
                  '3. You open the link here and approve with your fingerprint or phone lock.\n'
                  '4. You finish in My OneApp or on *334# by entering your M-Pesa PIN there.', style: text.bodyMedium),
              const SizedBox(height: 8),
              Text('Your PIN never comes to this app, the assistant or our servers.', style: text.bodySmall),
            ]),
          ),
          const SizedBox(height: 12),
          Wrap(spacing: 8, runSpacing: 8, children: [
            FilledButton.icon(onPressed: () => _openOneApp(context), icon: const Icon(Icons.open_in_new), label: const Text('Open My OneApp')),
            OutlinedButton.icon(onPressed: () => _dial(context), icon: const Icon(Icons.dialpad), label: const Text('Dial *334#')),
          ]),
          const SizedBox(height: 24),
          const SectionTitle('Connecting the two'),
          Panel(
            child: Text(
              'There is no button here to link your M-Pesa account to the chat, because Safaricom does not publish a way for another app to do that and move money on its own. '
              'That is also why every payment from your own account ends in M-Pesa with your PIN, and why you can keep using USSD exactly as before.\n\n'
              'For a business, the link is the Daraja credentials you save under Settings, Business accounts. They let the chat run STK Push, balance, B2C and more on your shortcode, '
              'and money going out still waits for your approval.',
              style: text.bodyMedium,
            ),
          ),
        ]),
      ),
    );
  }
}
