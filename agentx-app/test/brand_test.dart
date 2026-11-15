import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:agentx_app/widgets/brand.dart';

Widget _host(Widget child, {bool reduceMotion = false}) => MaterialApp(
      home: Builder(
        builder: (context) => MediaQuery(
          data: MediaQuery.of(context).copyWith(disableAnimations: reduceMotion),
          child: Scaffold(body: Center(child: child)),
        ),
      ),
    );

void main() {
  group('BrandTimeline', () {
    test('the intro starts empty, draws the arch then the curve, and ends complete with the coin home', () {
      final start = BrandTimeline.at(BrandMotion.intro, 0);
      expect(start.tile, 0);
      expect(start.archTo, 0);
      expect(start.flowTo, 0);
      expect(start.coin, isNull);

      final middle = BrandTimeline.at(BrandMotion.intro, .3);
      expect(middle.archTo, greaterThan(.3));
      expect(middle.flowTo, lessThan(middle.archTo));

      final end = BrandTimeline.at(BrandMotion.intro, 1);
      expect(end.tile, 1);
      expect(end.archTo, 1);
      expect(end.flowTo, 1);
      expect(end.coin, 1);
    });

    test('the loop draws, rests complete, then wipes off from the start', () {
      final rest = BrandTimeline.at(BrandMotion.loop, .5);
      expect([rest.archFrom, rest.archTo, rest.flowFrom, rest.flowTo], [0, 1, 0, 1]);

      final wiping = BrandTimeline.at(BrandMotion.loop, .85);
      expect(wiping.archFrom, greaterThan(0));
      expect(wiping.archTo, 1);

      final begin = BrandTimeline.at(BrandMotion.loop, 0);
      expect(begin.archTo, 0);
      expect(begin.flowTo, 0);
    });

    test('still is always complete', () {
      final still = BrandTimeline.at(BrandMotion.still, .4);
      expect([still.tile, still.archFrom, still.archTo, still.flowFrom, still.flowTo], [1, 0, 1, 0, 1]);
      expect(still.coin, isNull);
    });
  });

  group('BrandMark', () {
    testWidgets('draws still at the size asked for', (tester) async {
      await tester.pumpWidget(_host(const BrandMark(size: 72)));
      expect(tester.takeException(), isNull);
      expect(tester.getSize(find.byType(BrandMark)), const Size(72, 72));
    });

    testWidgets('the intro plays once and then lets the screen settle', (tester) async {
      await tester.pumpWidget(_host(const BrandMark(size: 96, motion: BrandMotion.intro, ring: true)));
      await tester.pump(const Duration(milliseconds: 700));
      expect(tester.takeException(), isNull);
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
    });

    testWidgets('the loop keeps going and is torn down cleanly', (tester) async {
      await tester.pumpWidget(_host(const BrandMark(size: 96, motion: BrandMotion.loop)));
      for (var i = 0; i < 8; i++) {
        await tester.pump(const Duration(milliseconds: 450));
      }
      expect(tester.takeException(), isNull);
      // Removing it must not leave a running ticker behind.
      await tester.pumpWidget(_host(const SizedBox()));
      expect(tester.takeException(), isNull);
    });

    testWidgets('with reduced motion the loop is a still logo, so the screen settles', (tester) async {
      await tester.pumpWidget(_host(const BrandMark(size: 96, motion: BrandMotion.loop), reduceMotion: true));
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
    });

    testWidgets('changing the motion starts the new one', (tester) async {
      await tester.pumpWidget(_host(const BrandMark(motion: BrandMotion.loop)));
      await tester.pump(const Duration(milliseconds: 300));
      await tester.pumpWidget(_host(const BrandMark()));
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
    });
  });

  group('BrandLoader and BrandLockup', () {
    testWidgets('the loader says what it is waiting for', (tester) async {
      await tester.pumpWidget(_host(const BrandLoader(label: 'Loading your chats')));
      await tester.pump(const Duration(milliseconds: 500));
      expect(find.text('Loading your chats'), findsOneWidget);
      expect(tester.takeException(), isNull);
    });

    testWidgets('the loader without words still draws', (tester) async {
      await tester.pumpWidget(_host(const BrandLoader()));
      await tester.pump(const Duration(milliseconds: 500));
      expect(find.byType(BrandMark), findsOneWidget);
      expect(tester.takeException(), isNull);
    });

    testWidgets('the lockup shows the name and one X in red', (tester) async {
      await tester.pumpWidget(_host(const BrandLockup(size: 80)));
      expect(find.text('Daraja'), findsOneWidget);
      expect(find.textContaining('Agent-', findRichText: true), findsOneWidget);
      expect(tester.takeException(), isNull);
    });
  });
}
