import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ganji_app/layout/responsive.dart';
import 'package:ganji_app/theme/app_theme.dart';
import 'package:ganji_app/widgets/ui.dart';

Widget _page(ThemeKey look, List<Widget> children, {double textScale = 1}) => MaterialApp(
      theme: AppTheme.build(look),
      builder: (context, child) => MediaQuery.withClampedTextScaling(minScaleFactor: textScale, maxScaleFactor: textScale, child: child!),
      home: Scaffold(body: CenteredList(children: children)),
    );

void main() {
  test('the three size classes start at 600 and 900', () {
    expect(Breakpoints.forWidth(320), ScreenSize.compact);
    expect(Breakpoints.forWidth(599), ScreenSize.compact);
    expect(Breakpoints.forWidth(600), ScreenSize.medium);
    expect(Breakpoints.forWidth(899), ScreenSize.medium);
    expect(Breakpoints.forWidth(900), ScreenSize.expanded);
    expect(Breakpoints.forWidth(2560), ScreenSize.expanded);
  });

  test('content is centred at its widest and keeps a gutter on a phone', () {
    expect(sidePad(320, maxWidth: 680), Breakpoints.gutter(320));
    expect(sidePad(1600, maxWidth: 680), 460);
    expect(sidePad(700, maxWidth: 680), Breakpoints.gutter(700));
  });

  testWidgets('request cards and the amount lay out on every screen size and at large text', (tester) async {
    addTearDown(() => tester.binding.setSurfaceSize(null));
    const sizes = [Size(320, 568), Size(412, 915), Size(820, 1180), Size(1600, 900)];
    for (final k in ThemeKey.values) {
      for (final size in sizes) {
        for (final scale in [1.0, 1.4]) {
          await tester.binding.setSurfaceSize(size);
          await tester.pumpWidget(_page(
            k,
            const [
              Panel(child: AmountHero(amount: 1234567, summary: 'Send KES 1,234,567 to a very long contact name 0712345678')),
              SizedBox(height: 12),
              TicketCard(
                highlight: true,
                main: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text('Send money'),
                  Text('Send KES 500 to Mama Wanjiku Kamau, 0712345678'),
                  StatusTag('Waiting for your approval', tone: Tone.info),
                ]),
                stub: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
                  Text('KES'),
                  FittedBox(fit: BoxFit.scaleDown, child: Text('1,234,567')),
                ]),
              ),
              SizedBox(height: 12),
              Callout('Check the name before you pay.', tone: Tone.warn),
              BrandMark(),
            ],
            textScale: scale,
          ));
          expect(tester.takeException(), isNull, reason: '${k.name} ${size.width}x${size.height} text $scale');
        }
      }
    }
  });

  testWidgets('the empty page and the pattern draw without errors', (tester) async {
    addTearDown(() => tester.binding.setSurfaceSize(null));
    for (final size in const [Size(320, 480), Size(1280, 800)]) {
      await tester.binding.setSurfaceSize(size);
      await tester.pumpWidget(MaterialApp(
        theme: AppTheme.build(ThemeKey.daylight),
        home: const Scaffold(
          body: Stack(fit: StackFit.expand, children: [
            Positioned.fill(child: BrandPattern()),
            EmptyState(title: 'No requests yet', message: 'Ask M-Ganji in the chat. The request shows up here, ready to approve.'),
          ]),
        ),
      ));
      expect(find.text('No requests yet'), findsOneWidget);
      expect(tester.takeException(), isNull, reason: 'width ${size.width}');
    }
  });
}
