import 'package:flutter/material.dart';

import '../app_state.dart';
import '../layout/responsive.dart';
import '../theme/app_theme.dart';
import 'ui.dart';

/// One line of reassurance on the brand panel: an icon and a few words.
class AuthPoint {
  const AuthPoint(this.icon, this.words);

  final IconData icon;
  final String words;
}

/// The frame around the pages people see before they are signed in, in the same look as the sign in page.
///
/// On a phone the brand sits in a band at the top, with a back button and the appearance button, and the form fills the rest.
/// On a wide window the brand fills the left side with a headline and a few points, and the form is on the right.
class AuthShell extends StatelessWidget {
  const AuthShell({
    super.key,
    required this.state,
    required this.headline,
    required this.blurb,
    required this.points,
    required this.children,
    this.onBack,
    this.title = 'Daraja Agent-X',
    this.tagline = 'Chat to pay. Approve on your phone.',
  });

  final AppState state;

  /// The large words on the wide brand panel. A line break is allowed.
  final String headline;
  final String blurb;
  final List<AuthPoint> points;

  /// The form, shown in a scrolling column.
  final List<Widget> children;

  /// What the back button does. Without it the page is closed.
  final VoidCallback? onBack;
  final String title;
  final String tagline;

  void _back(BuildContext context) {
    if (onBack != null) {
      onBack!();
    } else {
      Navigator.of(context).maybePop();
    }
  }

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, box) {
        if (box.maxWidth >= 860) {
          return Scaffold(
            body: Row(children: [
              Expanded(
                flex: 5,
                child: _Panel(state: state, headline: headline, blurb: blurb, points: points, title: title, onBack: () => _back(context)),
              ),
              Expanded(
                flex: 4,
                child: SafeArea(
                  child: Center(
                    child: SingleChildScrollView(
                      padding: const EdgeInsets.all(32),
                      child: ConstrainedBox(
                        constraints: const BoxConstraints(maxWidth: 440),
                        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: children),
                      ),
                    ),
                  ),
                ),
              ),
            ]),
          );
        }
        final keyboardOpen = MediaQuery.viewInsetsOf(context).bottom > 0;
        return Scaffold(
          body: Column(children: [
            _Band(state: state, title: title, tagline: tagline, compact: keyboardOpen, onBack: () => _back(context)),
            Expanded(
              child: SafeArea(
                top: false,
                child: CenteredList(maxWidth: 460, top: 22, bottom: 24, children: children),
              ),
            ),
          ]),
        );
      },
    );
  }
}

/// The band across the top on a phone.
class _Band extends StatelessWidget {
  const _Band({required this.state, required this.title, required this.tagline, required this.compact, required this.onBack});

  final AppState state;
  final String title;
  final String tagline;

  /// Hides the tagline while the keyboard is open, so the form keeps the room.
  final bool compact;
  final VoidCallback onBack;

  @override
  Widget build(BuildContext context) {
    final p = context.tk.p;
    final text = Theme.of(context).textTheme;
    final top = MediaQuery.paddingOf(context).top;
    return Container(
      width: double.infinity,
      color: p.headBg,
      child: Stack(children: [
        Positioned.fill(child: BrandPattern(color: p.headInk.withAlpha(22))),
        Padding(
          padding: EdgeInsets.fromLTRB(4, top + 6, 8, compact ? 8 : 16),
          child: Row(crossAxisAlignment: CrossAxisAlignment.center, children: [
            IconButton(onPressed: onBack, icon: Icon(Icons.arrow_back, color: p.headInk), tooltip: 'Back'),
            BrandMark(size: compact ? 28 : 36, ink: p.headInk, accent: p.headDark ? p.headInk : p.accent),
            const SizedBox(width: 12),
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, mainAxisSize: MainAxisSize.min, children: [
                Text(title, style: text.titleLarge?.copyWith(color: p.headInk)),
                if (!compact) Text(tagline, style: text.bodySmall?.copyWith(color: p.headInk.withAlpha(200))),
              ]),
            ),
            IconButton(
              onPressed: () => showThemeSheet(context, state),
              icon: Icon(Icons.palette_outlined, color: p.headInk),
              tooltip: 'Appearance',
            ),
          ]),
        ),
      ]),
    );
  }
}

/// The left side on a wide window.
class _Panel extends StatelessWidget {
  const _Panel({required this.state, required this.headline, required this.blurb, required this.points, required this.title, required this.onBack});

  final AppState state;
  final String headline;
  final String blurb;
  final List<AuthPoint> points;
  final String title;
  final VoidCallback onBack;

  @override
  Widget build(BuildContext context) {
    final p = context.tk.p;
    final text = Theme.of(context).textTheme;
    return Container(
      color: p.headBg,
      child: Stack(fit: StackFit.expand, children: [
        Positioned.fill(child: BrandPattern(color: p.headInk.withAlpha(22))),
        SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(40),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Row(children: [
                IconButton(onPressed: onBack, icon: Icon(Icons.arrow_back, color: p.headInk), tooltip: 'Back'),
                const SizedBox(width: 6),
                BrandMark(size: 44, ink: p.headInk, accent: p.headDark ? p.headInk : p.accent),
                const SizedBox(width: 14),
                Text(title, style: text.headlineSmall?.copyWith(color: p.headInk)),
                const Spacer(),
                IconButton(
                  onPressed: () => showThemeSheet(context, state),
                  icon: Icon(Icons.palette_outlined, color: p.headInk),
                  tooltip: 'Appearance',
                ),
              ]),
              const Spacer(),
              ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 520),
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text(headline, style: text.headlineLarge?.copyWith(color: p.headInk)),
                  const SizedBox(height: 14),
                  Text(blurb, style: text.bodyLarge?.copyWith(color: p.headInk.withAlpha(225))),
                  const SizedBox(height: 14),
                  for (final point in points)
                    Padding(
                      padding: const EdgeInsets.only(top: 14),
                      child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                        Icon(point.icon, size: 20, color: p.headInk),
                        const SizedBox(width: 12),
                        Expanded(child: Text(point.words, style: text.bodyLarge?.copyWith(color: p.headInk.withAlpha(225)))),
                      ]),
                    ),
                ]),
              ),
              const Spacer(flex: 2),
            ]),
          ),
        ),
      ]),
    );
  }
}
