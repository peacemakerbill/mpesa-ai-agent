import 'package:flutter/material.dart';

import '../models/business_account.dart';
import '../theme/app_theme.dart';
import 'ui.dart';

/// Shows, for one business account, which credentials are saved and a masked clue for each one, so a person can
/// confirm that what they typed was kept. The secrets themselves never leave the server.
class CredentialStatusCard extends StatelessWidget {
  const CredentialStatusCard({super.key, required this.status, this.isBranch = false});

  final CredentialStatus status;
  final bool isBranch;

  @override
  Widget build(BuildContext context) {
    final t = context.tk;
    final text = Theme.of(context).textTheme;
    return Panel(
      padding: const EdgeInsets.all(14),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          Expanded(child: Text('Saved credentials', style: text.titleSmall)),
          StatusTag(
            status.savedCount == 0 ? 'Nothing saved' : '${status.savedCount} of ${status.items.length} saved',
            tone: status.problems > 0 ? Tone.warn : (status.savedCount == 0 ? Tone.neutral : Tone.good),
          ),
        ]),
        const SizedBox(height: 4),
        Text(
          'A clue is shown so you can recognise what you saved. The full value is encrypted on the server and never sent back.',
          style: text.bodySmall?.copyWith(color: t.p.ink2),
        ),
        const SizedBox(height: 10),
        if (!status.vaultConfigured) ...[
          const Callout('The server has no vault key (AGENTX_VAULT_KEY), so saved secrets cannot be opened. Set it and restart agentx-api.', tone: Tone.bad),
          const SizedBox(height: 10),
        ] else if (status.problems > 0) ...[
          const Callout(
            'Some saved values cannot be opened with the vault key the server has now. This happens when AGENTX_VAULT_KEY was changed after saving. Enter those values again to replace them.',
            tone: Tone.warn,
          ),
          const SizedBox(height: 10),
        ],
        for (final item in status.items) _Row(item: item, isBranch: isBranch),
      ]),
    );
  }
}

class _Row extends StatelessWidget {
  const _Row({required this.item, required this.isBranch});

  final CredentialItem item;
  final bool isBranch;

  @override
  Widget build(BuildContext context) {
    final t = context.tk;
    final text = Theme.of(context).textTheme;
    final IconData icon = !item.saved ? Icons.radio_button_unchecked : (item.readable ? Icons.check_circle : Icons.error_outline);
    final Color color = !item.saved ? t.p.ink2 : (item.readable ? t.p.good : t.p.warn);
    final String where = item.source == 'HEAD' ? 'From the head office' : (item.source == 'OWN' ? (isBranch ? 'On this account' : 'Saved') : 'Not saved');
    final String clue = !item.saved ? 'Not saved' : (item.readable ? item.mask : 'Cannot be opened');
    return Semantics(
      label: '${item.label}. $where. $clue',
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 5),
        child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Icon(icon, size: 18, color: color),
          const SizedBox(width: 10),
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(item.label, style: text.bodyMedium),
              Text(item.saved ? where : 'Not saved yet', style: text.bodySmall?.copyWith(color: t.p.ink2)),
            ]),
          ),
          const SizedBox(width: 8),
          Flexible(
            child: SelectableText(
              clue,
              textAlign: TextAlign.end,
              style: text.bodyMedium?.copyWith(
                color: item.saved && item.readable ? t.p.ink : t.p.ink2,
                fontFeatures: const [FontFeature.tabularFigures()],
                letterSpacing: item.saved && item.readable ? 0.5 : 0,
              ),
            ),
          ),
        ]),
      ),
    );
  }
}
