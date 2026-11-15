import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:agentx_app/app_state.dart';
import 'package:agentx_app/services/secure_store.dart';
import 'package:agentx_app/theme/app_theme.dart';
import 'package:agentx_app/widgets/ui.dart';

double contrast(Color a, Color b) {
  final l1 = a.computeLuminance();
  final l2 = b.computeLuminance();
  final hi = l1 > l2 ? l1 : l2;
  final lo = l1 > l2 ? l2 : l1;
  return (hi + 0.05) / (lo + 0.05);
}

/// Keeps the chosen look in memory, so the tests never touch the phone's secure storage.
class _FakeStore extends SecureStore {
  String? saved;

  @override
  Future<String?> token() async => null;

  @override
  Future<String?> phone() async => null;

  @override
  Future<String?> theme() async => saved;

  @override
  Future<void> setTheme(String name) async {
    saved = name;
  }
}

void main() {
  test('there are ten looks, each with a name, a description and a group', () {
    expect(ThemeKey.values.map((k) => k.label), ['Daylight', 'Graphite', 'Pwani', 'Mono', 'Matatu', 'Kahawa', 'Blueprint', 'Savanna', 'Lipa', 'Receipt']);
    expect(ThemeKey.values.where((k) => k.group == 'Everyday'), hasLength(7));
    for (final k in ThemeKey.values) {
      expect(k.description, isNotEmpty);
    }
  });

  test('a saved name is read back, anything else falls back to Daylight', () {
    expect(ThemeKeyX.parse('graphite'), ThemeKey.graphite);
    expect(ThemeKeyX.parse('mono'), ThemeKey.mono);
    expect(ThemeKeyX.parse('kahawa'), ThemeKey.kahawa);
    expect(ThemeKeyX.parse(null), ThemeKey.daylight);
    expect(ThemeKeyX.parse('jacaranda'), ThemeKey.daylight);
  });

  test('each look builds a theme that carries its tokens', () {
    for (final k in ThemeKey.values) {
      final theme = AppTheme.build(k);
      final tokens = theme.extension<Tokens>();
      expect(tokens, isNotNull, reason: '${k.name} has tokens');
      expect(tokens!.key, k);
      expect(theme.scaffoldBackgroundColor, Palette.of(k).bg);
      expect(theme.colorScheme.primary, Palette.of(k).accent);
      expect(theme.colorScheme.brightness, (k == ThemeKey.graphite || k == ThemeKey.kahawa || k == ThemeKey.blueprint) ? Brightness.dark : Brightness.light);
      expect(identical(AppTheme.build(k), theme), isTrue, reason: 'built once');
    }
  });

  test('Mono and Receipt have square corners and their own type, the others are rounded', () {
    final mono = Tokens.of(ThemeKey.mono);
    expect(mono.square, isTrue);
    expect(mono.headFont, isNotNull);
    final receipt = Tokens.of(ThemeKey.receipt);
    expect(receipt.square, isTrue);
    expect(receipt.bodyFont, isNotNull);
    for (final k in [ThemeKey.daylight, ThemeKey.graphite, ThemeKey.pwani, ThemeKey.matatu, ThemeKey.kahawa, ThemeKey.blueprint, ThemeKey.savanna, ThemeKey.lipa]) {
      expect(Tokens.of(k).square, isFalse);
    }
  });

  test('text is readable on every look (WCAG AA, 4.5 to 1)', () {
    for (final k in ThemeKey.values) {
      final p = Palette.of(k);
      void atLeast(Color a, Color b, String what) {
        expect(contrast(a, b), greaterThanOrEqualTo(4.5), reason: '${k.name}: $what');
      }

      atLeast(p.ink, p.bg, 'text on background');
      atLeast(p.ink2, p.bg, 'secondary text on background');
      atLeast(p.ink3, p.bg, 'hint text on background');
      atLeast(p.ink3, p.surface, 'hint text on a card');
      atLeast(p.ink, p.accentSoft, 'text on the soft accent');
      atLeast(p.accentInk, p.accent, 'text on a filled button');
      atLeast(p.headInk, p.headBg, 'text on the app bar');
      atLeast(p.good, p.surface, 'good on a card');
      atLeast(p.warn, p.surface, 'warning on a card');
      atLeast(p.bad, p.surface, 'error on a card');
    }
  });

  test('six looks are modern (flat replies, soft shadows), four keep their boxed character', () {
    const modern = {ThemeKey.daylight, ThemeKey.graphite, ThemeKey.pwani, ThemeKey.kahawa, ThemeKey.savanna, ThemeKey.lipa};
    for (final k in ThemeKey.values) {
      final t = Tokens.of(k);
      expect(t.modern, modern.contains(k), reason: k.name);
      expect(t.shadow.isNotEmpty, modern.contains(k), reason: '${k.name} shadow');
      expect(t.cardShadow.isNotEmpty, modern.contains(k), reason: '${k.name} card shadow');
    }
  });

  test('every look reads in Inter, which is bundled, except Receipt with its typewriter face', () {
    for (final k in ThemeKey.values) {
      final t = Tokens.of(k);
      expect(t.bodyFont, k == ThemeKey.receipt ? 'Courier' : 'Inter', reason: k.name);
      expect(AppTheme.build(k).textTheme.bodyLarge?.fontFamily, t.bodyFont, reason: k.name);
    }
  });

  test('the small print on the customer\'s filled bubble and selected text stay readable', () {
    for (final k in ThemeKey.values) {
      final p = Palette.of(k);
      // The time and the copy icon are drawn at 235 of 255 over the accent colour.
      final small = Color.alphaBlend(p.accentInk.withAlpha(235), p.accent);
      expect(contrast(small, p.accent), greaterThanOrEqualTo(4.5), reason: '${k.name}: time on a filled bubble');

      final selection = AppTheme.build(k).textSelectionTheme.selectionColor;
      expect(selection, isNotNull, reason: '${k.name} has a selection colour');
      for (final base in [p.bg, p.surface, p.accentSoft]) {
        expect(contrast(p.ink, Color.alphaBlend(selection!, base)), greaterThanOrEqualTo(4.5), reason: '${k.name}: selected text');
      }
    }
  });

  testWidgets('the shared widgets draw in every look without errors', (tester) async {
    for (final k in ThemeKey.values) {
      await tester.pumpWidget(MaterialApp(
        theme: AppTheme.build(k),
        home: const Scaffold(
          body: SingleChildScrollView(
            child: Column(children: [
              AmountHero(amount: 1200, summary: 'Send KES 1,200 to 0712345678'),
              StatusTag('Waiting for your approval', tone: Tone.info),
              Callout('Check the name before you pay.', tone: Tone.warn),
              BrandMark(),
            ]),
          ),
        ),
      ));
      expect(find.text('1,200'), findsOneWidget, reason: k.name);
      expect(find.text('Waiting for your approval'), findsOneWidget);
      expect(tester.takeException(), isNull, reason: k.name);
    }
  });

  testWidgets('the appearance sheet switches the look and saves it', (tester) async {
    final store = _FakeStore();
    final state = AppState(store: store);
    await tester.pumpWidget(ListenableBuilder(
      listenable: state,
      builder: (context, _) => MaterialApp(
        theme: AppTheme.build(state.theme),
        home: Builder(
          builder: (context) => Scaffold(
            body: Center(child: FilledButton(onPressed: () => showThemeSheet(context, state), child: const Text('Open'))),
          ),
        ),
      ),
    ));

    await tester.tap(find.text('Open'));
    await tester.pumpAndSettle();
    expect(find.text('Appearance'), findsOneWidget);
    for (final k in ThemeKey.values) {
      expect(find.text(k.label), findsOneWidget);
    }

    await tester.tap(find.text('Graphite'));
    await tester.pumpAndSettle();
    expect(state.theme, ThemeKey.graphite);
    expect(store.saved, 'graphite');
  });

  testWidgets('the appearance sheet closes from the top and from the bottom', (tester) async {
    final state = AppState(store: _FakeStore());
    await tester.pumpWidget(MaterialApp(
      theme: AppTheme.build(state.theme),
      home: Builder(
        builder: (context) => Scaffold(
          body: Center(child: FilledButton(onPressed: () => showThemeSheet(context, state), child: const Text('Open'))),
        ),
      ),
    ));

    await tester.tap(find.text('Open'));
    await tester.pumpAndSettle();
    expect(find.text('Appearance'), findsOneWidget);
    await tester.tap(find.byTooltip('Close'));
    await tester.pumpAndSettle();
    expect(find.text('Appearance'), findsNothing);

    await tester.tap(find.text('Open'));
    await tester.pumpAndSettle();
    expect(find.text('Appearance'), findsOneWidget);
    await tester.tap(find.widgetWithText(OutlinedButton, 'Close'));
    await tester.pumpAndSettle();
    expect(find.text('Appearance'), findsNothing);
  });

  test('the saved look is loaded when the app starts', () async {
    final store = _FakeStore()..saved = 'pwani';
    final state = AppState(store: store);
    await state.start();
    expect(state.theme, ThemeKey.pwani);
    expect(state.linked, isFalse);
  });
}
