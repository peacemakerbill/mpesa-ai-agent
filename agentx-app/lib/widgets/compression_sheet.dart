import 'package:flutter/material.dart';

import '../app_state.dart';
import '../chat/media_compressor.dart';
import '../theme/app_theme.dart';
import 'ui.dart';

/// Lets the customer choose how small photos and videos are made before they are sent. Saved on this device.
Future<void> showCompressionSheet(BuildContext context, AppState state) {
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    constraints: const BoxConstraints(maxWidth: 560),
    builder: (sheetContext) => ListenableBuilder(
      listenable: state,
      builder: (context, _) {
        final t = context.tk;
        final text = Theme.of(context).textTheme;
        return SafeArea(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(20, 0, 20, 16),
            child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
              const SheetHeader(title: 'Photo and video size', subtitle: 'Applied before a file is sent. Saved on this device.'),
              const SizedBox(height: 12),
              Flexible(
                child: SingleChildScrollView(
                  child: Column(children: [
                    for (final level in CompressionLevel.values)
                      Padding(
                        padding: const EdgeInsets.only(bottom: 8),
                        child: Semantics(
                          button: true,
                          selected: state.compression == level,
                          label: '${level.label}. ${level.hint}',
                          child: Material(
                            color: state.compression == level ? t.p.accentSoft : t.p.surface,
                            shape: RoundedRectangleBorder(
                              borderRadius: t.corners,
                              side: BorderSide(color: state.compression == level ? t.p.accent : t.p.line, width: state.compression == level ? 2 : 1),
                            ),
                            clipBehavior: Clip.antiAlias,
                            child: InkWell(
                              onTap: () => state.setCompression(level),
                              child: Padding(
                                padding: const EdgeInsets.all(14),
                                child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                                  Expanded(
                                    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                                      Text(level.label, style: text.titleMedium),
                                      const SizedBox(height: 2),
                                      Text(level.hint, style: text.bodySmall),
                                    ]),
                                  ),
                                  if (state.compression == level) Icon(Icons.check_circle, color: t.p.accent),
                                ]),
                              ),
                            ),
                          ),
                        ),
                      ),
                    const SizedBox(height: 4),
                    Text(
                      'Documents are sent as they are. Voice notes are recorded small already.',
                      style: text.bodySmall,
                    ),
                  ]),
                ),
              ),
              const SizedBox(height: 12),
              const SheetCloseButton(),
            ]),
          ),
        );
      },
    ),
  );
}
