import 'package:flutter/material.dart';

import '../chat/mentions.dart';
import '../theme/app_theme.dart';

/// The list that opens above the message box while the customer types an @: their business accounts, and a row to pick a contact.
/// It does not read contacts itself. The contacts row opens the contact finder, so the app sees only the person the customer picks.
class MentionPanel extends StatelessWidget {
  const MentionPanel({super.key, required this.items, required this.loading, required this.onPick});

  final List<MentionItem> items;

  /// True while the business accounts are still being fetched, so the panel does not look empty.
  final bool loading;
  final void Function(MentionItem item) onPick;

  @override
  Widget build(BuildContext context) {
    final t = context.tk;
    final text = Theme.of(context).textTheme;
    final accounts = items.where((i) => i.kind == MentionKind.account).length;
    return Container(
      constraints: const BoxConstraints(maxHeight: 300),
      decoration: BoxDecoration(color: t.p.surface, border: Border(top: BorderSide(color: t.p.line))),
      child: ListView(
        shrinkWrap: true,
        padding: const EdgeInsets.symmetric(vertical: 4),
        children: [
          if (loading && accounts == 0)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 10, 16, 6),
              child: Text('Loading your business accounts', style: text.bodySmall?.copyWith(color: t.p.ink2)),
            ),
          for (final item in items)
            ListTile(
              dense: true,
              leading: Icon(item.kind == MentionKind.account ? Icons.storefront_outlined : Icons.person_search_outlined, color: t.p.ink),
              title: Text(item.title, maxLines: 1, overflow: TextOverflow.ellipsis),
              subtitle: Text(item.subtitle, maxLines: 2, overflow: TextOverflow.ellipsis),
              onTap: () => onPick(item),
            ),
        ],
      ),
    );
  }
}
