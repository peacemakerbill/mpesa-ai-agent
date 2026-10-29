import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:mpesa_companion/app_state.dart';
import 'package:mpesa_companion/services/secure_store.dart';
import 'package:mpesa_companion/theme/app_theme.dart';
import 'package:mpesa_companion/widgets/ui.dart';

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

  test('the saved look is loaded when the app starts', () async {
    final store = _FakeStore()..saved = 'pwani';
    final state = AppState(store: store);
    await state.start();
    expect(state.theme, ThemeKey.pwani);
    expect(state.linked, isFalse);
  });
}
