import 'package:flutter/material.dart';

import '../app_state.dart';
import '../models/progress.dart';
import '../theme/app_theme.dart';

/// A bordered surface. With [onTap] it is a button and shows the ripple.
class Panel extends StatelessWidget {
  const Panel({super.key, required this.child, this.padding = const EdgeInsets.all(16), this.onTap, this.edge, this.color});

  final Widget child;
  final EdgeInsetsGeometry padding;
  final VoidCallback? onTap;

  /// A coloured strip on the left edge, used to mark what needs the customer.
  final Color? edge;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    final t = context.tk;
    Widget content = Padding(padding: padding, child: child);
    if (edge != null) {
      content = IntrinsicHeight(
        child: Row(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          Container(width: 4, color: edge),
          Expanded(child: content),
        ]),
      );
    }
    return Material(
      color: color ?? t.p.surface,
      shape: t.shape(),
      clipBehavior: Clip.antiAlias,
      child: onTap == null ? content : InkWell(onTap: onTap, child: content),
    );
  }
}

enum Tone { neutral, good, warn, bad, info }

Color toneColor(Tokens t, Tone tone) {
  switch (tone) {
    case Tone.good:
      return t.p.good;
    case Tone.warn:
      return t.p.warn;
    case Tone.bad:
      return t.p.bad;
    case Tone.info:
      return t.p.accent;
    case Tone.neutral:
      return t.p.ink2;
  }
}

Tone toneForStatus(String status) {
  switch (status) {
    case 'DONE_REPORTED':
      return Tone.good;
    case 'HANDED_OFF':
      return Tone.warn;
    case 'DECLINED':
    case 'CANCELLED':
    case 'EXPIRED':
    case 'NOT_DONE':
      return Tone.bad;
    default:
      return Tone.info;
  }
}

/// A short status label with a tinted background.
class StatusTag extends StatelessWidget {
  const StatusTag(this.text, {super.key, this.tone = Tone.neutral});

  final String text;
  final Tone tone;

  @override
  Widget build(BuildContext context) {
    final t = context.tk;
    final c = toneColor(t, tone);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: t.square ? t.p.surface : t.tint(c),
        borderRadius: BorderRadius.circular(t.radius * 0.4),
        border: Border.all(color: t.square ? t.p.line : Color.alphaBlend(c.withAlpha(90), t.p.surface)),
      ),
      child: Text(text, style: Theme.of(context).textTheme.labelMedium?.copyWith(color: t.square ? t.p.ink : c, height: 1.1)),
    );
  }
}

/// A boxed note. Warnings and information use the same shape so the screen stays calm.
class Callout extends StatelessWidget {
  const Callout(this.text, {super.key, this.tone = Tone.neutral, this.icon});

  final String text;
  final Tone tone;
  final IconData? icon;

  @override
  Widget build(BuildContext context) {
    final t = context.tk;
    final c = toneColor(t, tone);
    final iconData = icon ?? (tone == Tone.warn ? Icons.warning_amber_rounded : tone == Tone.bad ? Icons.error_outline : tone == Tone.good ? Icons.check_circle_outline : Icons.info_outline);
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: tone == Tone.neutral ? t.p.surface2 : t.tint(c),
        borderRadius: t.corners,
        border: Border.all(color: tone == Tone.neutral ? t.p.line : Color.alphaBlend(c.withAlpha(70), t.p.surface)),
      ),
      child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Icon(iconData, size: 20, color: tone == Tone.neutral ? t.p.ink2 : c),
        const SizedBox(width: 10),
        Expanded(child: Text(text, style: Theme.of(context).textTheme.bodyMedium)),
      ]),
    );
  }
}

/// The amount, large, with the plain-words summary under it.
class AmountHero extends StatelessWidget {
  const AmountHero({super.key, required this.amount, required this.summary});

  final int amount;
  final String summary;

  @override
  Widget build(BuildContext context) {
    final t = context.tk;
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Text('Amount', style: Theme.of(context).textTheme.labelMedium),
      const SizedBox(height: 4),
      Row(crossAxisAlignment: CrossAxisAlignment.baseline, textBaseline: TextBaseline.alphabetic, children: [
        Text('KES', style: t.numeric(18, weight: FontWeight.w600, color: t.p.ink2)),
        const SizedBox(width: 8),
        Flexible(child: Text(formatKes(amount), style: t.numeric(46), overflow: TextOverflow.ellipsis)),
      ]),
      const SizedBox(height: 8),
      Text(summary, style: Theme.of(context).textTheme.bodyLarge?.copyWith(color: t.p.ink2)),
    ]);
  }
}

/// Where the request is on its way, top to bottom.
class ProgressTimeline extends StatelessWidget {
  const ProgressTimeline({super.key, required this.steps});

  final List<TimelineStep> steps;

  @override
  Widget build(BuildContext context) {
    final t = context.tk;
    final text = Theme.of(context).textTheme;
    return Column(children: [
      for (var i = 0; i < steps.length; i++)
        IntrinsicHeight(
          child: Row(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            SizedBox(
              width: 22,
              child: Column(children: [
                const SizedBox(height: 3),
                _Dot(steps[i].state),
                if (i < steps.length - 1)
                  Expanded(
                    child: Center(
                      child: Container(width: 2, color: steps[i].state == StepState.done ? t.p.accent : t.p.lineStrong),
                    ),
                  ),
              ]),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Padding(
                padding: EdgeInsets.only(bottom: i < steps.length - 1 ? 16 : 0),
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text(
                    steps[i].label,
                    style: text.bodyMedium?.copyWith(
                      color: steps[i].state == StepState.todo ? t.p.ink3 : t.p.ink,
                      fontWeight: steps[i].state == StepState.now ? FontWeight.w700 : FontWeight.w500,
                    ),
                  ),
                  if (steps[i].note != null) Text(steps[i].note!, style: text.bodySmall?.copyWith(color: t.p.ink3)),
                ]),
              ),
            ),
          ]),
        ),
    ]);
  }
}

class _Dot extends StatelessWidget {
  const _Dot(this.state);

  final StepState state;

  @override
  Widget build(BuildContext context) {
    final t = context.tk;
    final Color fill;
    final Color edge;
    switch (state) {
      case StepState.done:
        fill = t.p.accent;
        edge = t.p.accent;
        break;
      case StepState.bad:
        fill = t.p.bad;
        edge = t.p.bad;
        break;
      case StepState.now:
        fill = t.p.bg;
        edge = t.p.accent;
        break;
      case StepState.todo:
        fill = t.p.bg;
        edge = t.p.lineStrong;
        break;
    }
    return Container(
      width: 18,
      height: 18,
      decoration: BoxDecoration(
        color: fill,
        shape: t.square ? BoxShape.rectangle : BoxShape.circle,
        border: Border.all(color: edge, width: 2),
        boxShadow: state == StepState.now && !t.square ? [BoxShadow(color: t.p.accentSoft, spreadRadius: 4)] : null,
      ),
    );
  }
}

/// A two column list of label and value, like a receipt.
class KeyValueList extends StatelessWidget {
  const KeyValueList(this.rows, {super.key});

  final List<MapEntry<String, String>> rows;

  @override
  Widget build(BuildContext context) {
    final t = context.tk;
    final text = Theme.of(context).textTheme;
    return Panel(
      padding: EdgeInsets.zero,
      child: Column(children: [
        for (var i = 0; i < rows.length; i++) ...[
          if (i > 0) Divider(height: 1, color: t.p.line),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
            child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Expanded(flex: 4, child: Text(rows[i].key, style: text.bodyMedium?.copyWith(color: t.p.ink2))),
              const SizedBox(width: 12),
              Expanded(flex: 5, child: Text(rows[i].value, textAlign: TextAlign.right, style: text.bodyMedium?.copyWith(fontWeight: FontWeight.w600))),
            ]),
          ),
        ],
      ]),
    );
  }
}

/// A heading above a group of controls.
class SectionTitle extends StatelessWidget {
  const SectionTitle(this.text, {super.key});

  final String text;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(top: 8, bottom: 8),
        child: Text(text, style: Theme.of(context).textTheme.titleMedium),
      );
}

/// The small logo: an outlined square with a drawn M.
class BrandMark extends StatelessWidget {
  const BrandMark({super.key, this.size = 40});

  final double size;

  @override
  Widget build(BuildContext context) {
    final t = context.tk;
    return SizedBox(width: size, height: size, child: CustomPaint(painter: _MarkPainter(ink: t.p.ink, accent: t.p.accent, square: t.square)));
  }
}

class _MarkPainter extends CustomPainter {
  const _MarkPainter({required this.ink, required this.accent, required this.square});

  final Color ink;
  final Color accent;
  final bool square;

  @override
  void paint(Canvas canvas, Size size) {
    final u = size.width / 24;
    final box = Rect.fromLTWH(1.5 * u, 1.5 * u, 21 * u, 21 * u);
    final outline = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.6 * u
      ..color = ink;
    canvas.drawRRect(RRect.fromRectAndRadius(box, Radius.circular(square ? 0 : 5 * u)), outline);
    final m = Path()
      ..moveTo(6 * u, 16.5 * u)
      ..lineTo(6 * u, 8 * u)
      ..lineTo(12 * u, 14 * u)
      ..lineTo(18 * u, 8 * u)
      ..lineTo(18 * u, 16.5 * u);
    final stroke = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2.2 * u
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round
      ..color = accent;
    canvas.drawPath(m, stroke);
  }

  @override
  bool shouldRepaint(covariant _MarkPainter old) => old.ink != ink || old.accent != accent || old.square != square;
}

/// An alert dialog that follows the theme's corners and colours.
class AppDialog extends StatelessWidget {
  const AppDialog({super.key, this.title, this.content, this.actions});

  final Widget? title;
  final Widget? content;
  final List<Widget>? actions;

  @override
  Widget build(BuildContext context) {
    final t = context.tk;
    final text = Theme.of(context).textTheme;
    return AlertDialog(
      backgroundColor: t.p.surface,
      surfaceTintColor: Colors.transparent,
      shape: t.shape(scale: 1.4),
      title: title,
      content: content,
      actions: actions,
      titleTextStyle: text.titleLarge,
      contentTextStyle: text.bodyMedium,
    );
  }
}

/// Lets the customer pick one of the ten looks. The choice is saved on the phone.
Future<void> showThemeSheet(BuildContext context, AppState state) {
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    builder: (sheetContext) => ListenableBuilder(
      listenable: state,
      builder: (context, _) {
        final t = context.tk;
        final text = Theme.of(context).textTheme;
        return SafeArea(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(20, 0, 20, 16),
            child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text('Appearance', style: text.titleLarge),
              const SizedBox(height: 4),
              Text('Saved on this phone.', style: text.bodySmall),
              const SizedBox(height: 12),
              Flexible(
                child: SingleChildScrollView(
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    for (final k in ThemeKey.values) ...[
                      if (k.index == 0 || ThemeKey.values[k.index - 1].group != k.group)
                        Padding(
                          padding: EdgeInsets.only(top: k.index == 0 ? 0 : 10, bottom: 8),
                          child: Text(k.group, style: text.labelMedium),
                        ),
                      Padding(
                        padding: const EdgeInsets.only(bottom: 8),
                        child: _ThemeRow(themeKey: k, selected: state.theme == k, onTap: () => state.setTheme(k)),
                      ),
                    ],
                  ]),
                ),
              ),
              SizedBox(height: t.square ? 0 : 4),
            ]),
          ),
        );
      },
    ),
  );
}

class _ThemeRow extends StatelessWidget {
  const _ThemeRow({required this.themeKey, required this.selected, required this.onTap});

  final ThemeKey themeKey;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final t = context.tk;
    final text = Theme.of(context).textTheme;
    final p = Palette.of(themeKey);
    return Semantics(
      button: true,
      selected: selected,
      label: '${themeKey.label}. ${themeKey.description}',
      child: Material(
        color: selected ? t.p.accentSoft : t.p.surface,
        shape: RoundedRectangleBorder(
          borderRadius: t.corners,
          side: BorderSide(color: selected ? t.p.accent : t.p.line, width: selected ? 2 : 1),
        ),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Row(children: [
              ExcludeSemantics(
                child: Container(
                  width: 54,
                  height: 36,
                  decoration: BoxDecoration(border: Border.all(color: t.p.lineStrong), borderRadius: BorderRadius.circular(t.radius * 0.4)),
                  clipBehavior: Clip.antiAlias,
                  child: Row(children: [
                    Expanded(child: ColoredBox(color: p.headBg, child: const SizedBox.expand())),
                    Expanded(child: ColoredBox(color: p.accent, child: const SizedBox.expand())),
                    Expanded(child: ColoredBox(color: p.bg, child: const SizedBox.expand())),
                  ]),
                ),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text(themeKey.label, style: text.titleMedium),
                  Text(themeKey.description, style: text.bodySmall),
                ]),
              ),
              if (selected) Icon(Icons.check, color: t.p.accent),
            ]),
          ),
        ),
      ),
    );
  }
}
