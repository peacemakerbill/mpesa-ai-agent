import 'dart:io';

import 'package:agentx_app/chat/chat_device.dart';
import 'package:agentx_app/models/chat.dart';
import 'package:agentx_app/theme/app_theme.dart';
import 'package:agentx_app/widgets/chat_bubbles.dart';
import 'package:agentx_app/widgets/ui.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

class _NoDevice implements ChatDevice {
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

BubbleActions _actions() => BubbleActions(
      onButton: (_) {},
      onRow: (_) {},
      onFindContact: (_) {},
      onOpenRequest: (_) {},
      onOpenBusiness: (_) {},
      onApproveWithOtp: (_, {required bool business}) {},
      onLink: (_) {},
      onError: (_) {},
      loadFile: (_) async => throw UnimplementedError(),
      device: _NoDevice(),
    );

/// The assistant wrote it when [mine] is false.
ChatMessage _message({bool mine = false, String kind = 'TEXT', Map<String, dynamic> extra = const {}}) =>
    ChatMessage.fromJson({'id': 1, 'dir': mine ? 'in' : 'out', 'kind': kind, 'at': 1, ...extra});

/// Records what is put on the clipboard.
List<String> _watchClipboard() {
  final copied = <String>[];
  TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, (call) async {
    if (call.method == 'Clipboard.setData') copied.add((call.arguments as Map)['text'] as String);
    return null;
  });
  addTearDown(() => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, null));
  return copied;
}

Future<void> _show(WidgetTester tester, Widget child, {ThemeKey look = ThemeKey.daylight}) {
  return tester.pumpWidget(MaterialApp(
    theme: AppTheme.build(look),
    home: Scaffold(body: SelectionArea(child: SingleChildScrollView(child: child))),
  ));
}

void main() {
  group('what Copy puts on the clipboard', () {
    test('a text message loses its markup but keeps its words and links', () {
      final m = _message(extra: {'text': 'Send *KES 500* to _Mama_ with `ref1`. See https://example.com/x'});
      expect(copyTextOf(m), 'Send KES 500 to Mama with ref1. See https://example.com/x');
    });

    test('a message with buttons copies its words and its footer', () {
      final m = _message(kind: 'BUTTONS', extra: {
        'text': 'Pay *300* to Till 123456?',
        'footer': 'Nothing moves until you approve',
        'buttons': [
          {'id': 'y', 'title': 'Yes'},
        ],
      });
      expect(copyTextOf(m), 'Pay 300 to Till 123456?\n\nNothing moves until you approve');
    });

    test('a photo copies its caption, a voice note has nothing to copy', () {
      expect(copyTextOf(_message(kind: 'IMAGE', extra: {'mediaId': 'm1', 'caption': 'The *till* sign'})), 'The till sign');
      expect(copyTextOf(_message(kind: 'IMAGE', extra: {'mediaId': 'm1'})), isNull);
      expect(copyTextOf(_message(kind: 'AUDIO', extra: {'mediaId': 'm2', 'voiceNote': true})), isNull);
    });

    test('a location copies its label and its coordinates', () {
      final m = _message(kind: 'LOCATION', extra: {'latitude': -1.28634, 'longitude': 36.82172, 'locationLabel': 'Nairobi CBD'});
      expect(copyTextOf(m), 'Nairobi CBD\n\n-1.28634, 36.82172');
    });

    test('a contact copies the name and number', () {
      expect(copyTextOf(_message(kind: 'CONTACTS', extra: {'contactsSummary': 'Mama Wanjiku 0712345678'})), 'Mama Wanjiku 0712345678');
    });
  });

  group('the Copy button', () {
    testWidgets('copies the message, shows a tick, then goes back', (tester) async {
      final copied = _watchClipboard();
      await _show(tester, MessageBubble(message: _message(extra: {'text': 'Your code is *48213*'}), live: true, actions: _actions()));

      expect(find.byIcon(Icons.content_copy_outlined), findsOneWidget);
      await tester.tap(find.byIcon(Icons.content_copy_outlined));
      await tester.pump();

      expect(copied, ['Your code is 48213']);
      expect(find.byIcon(Icons.check), findsOneWidget);

      await tester.pump(const Duration(seconds: 2));
      expect(find.byIcon(Icons.check), findsNothing);
      expect(find.byIcon(Icons.content_copy_outlined), findsOneWidget);
    });

    testWidgets('the customer\'s own message can be copied too', (tester) async {
      final copied = _watchClipboard();
      await _show(tester, MessageBubble(message: _message(mine: true, extra: {'text': 'Send 500 to Mama'}), live: false, actions: _actions()));

      await tester.tap(find.byIcon(Icons.content_copy_outlined));
      await tester.pump();
      expect(copied, ['Send 500 to Mama']);
      await tester.pump(const Duration(seconds: 2));
    });

    testWidgets('a voice note has no Copy button', (tester) async {
      await _show(tester, MessageBubble(message: _message(mine: true, kind: 'AUDIO', extra: {'mediaId': 'm2', 'voiceNote': true}), live: false, actions: _actions()));
      expect(find.byIcon(Icons.content_copy_outlined), findsNothing);
    });

    testWidgets('an M-Pesa code row has Copy and the other rows do not', (tester) async {
      final copied = _watchClipboard();
      await _show(tester, const KeyValueList([MapEntry('Till', '123456'), MapEntry('M-Pesa code', 'SGL7K2XYZ1')]));

      expect(find.byIcon(Icons.content_copy_outlined), findsOneWidget);
      await tester.tap(find.byIcon(Icons.content_copy_outlined));
      await tester.pump();
      expect(copied, ['SGL7K2XYZ1']);
      await tester.pump(const Duration(seconds: 2));
    });
  });

  group('the look of a message', () {
    bool filledWithAccent(WidgetTester tester, Color accent) => tester
        .widgetList<Container>(find.byType(Container))
        .any((c) => c.decoration is BoxDecoration && (c.decoration! as BoxDecoration).color == accent);

    testWidgets('on a modern look the reply sits beside the logo and the customer\'s words are filled', (tester) async {
      await _show(tester, Column(children: [
        MessageBubble(message: _message(extra: {'text': 'Hello from the assistant'}), live: true, actions: _actions()),
        MessageBubble(message: _message(mine: true, extra: {'text': 'Hello from me'}), live: true, actions: _actions()),
      ]));
      expect(find.byType(BrandMark), findsOneWidget);
      expect(find.text('Hello from the assistant'), findsOneWidget);
      expect(find.text('Hello from me'), findsOneWidget);
      expect(filledWithAccent(tester, Palette.daylight.accent), isTrue);
    });

    testWidgets('the logo shows once for a run of replies', (tester) async {
      await _show(tester, Column(children: [
        MessageBubble(message: _message(extra: {'text': 'One'}), live: true, actions: _actions()),
        MessageBubble(message: _message(extra: {'text': 'Two'}), live: true, actions: _actions(), startsGroup: false),
      ]));
      expect(find.byType(BrandMark), findsOneWidget);
    });

    testWidgets('a photo from the customer keeps a light bubble so the picture and its caption stay readable', (tester) async {
      await _show(tester, MessageBubble(message: _message(mine: true, kind: 'DOCUMENT', extra: {'mediaId': 'm', 'fileName': 'till.pdf', 'caption': 'The till'}), live: false, actions: _actions()));
      expect(filledWithAccent(tester, Palette.daylight.accent), isFalse);
    });

    for (final look in [ThemeKey.mono, ThemeKey.matatu, ThemeKey.blueprint, ThemeKey.receipt]) {
      testWidgets('${look.name} keeps boxed bubbles without the logo', (tester) async {
        await _show(
          tester,
          Column(children: [
            MessageBubble(message: _message(extra: {'text': 'Boxed reply'}), live: true, actions: _actions()),
            MessageBubble(message: _message(mine: true, extra: {'text': 'Boxed question'}), live: true, actions: _actions()),
          ]),
          look: look,
        );
        expect(find.byType(BrandMark), findsNothing);
        expect(find.text('Boxed reply'), findsOneWidget);
        expect(filledWithAccent(tester, Palette.of(look).accent), isFalse);
        expect(find.byIcon(Icons.content_copy_outlined), findsNWidgets(2));
      });
    }

    testWidgets('the typing dots sit beside the logo on a modern look', (tester) async {
      await _show(tester, const TypingBubble());
      expect(find.byType(BrandMark), findsOneWidget);
      await tester.pumpWidget(const SizedBox());
    });

    testWidgets('every look draws every kind of message without errors', (tester) async {
      final messages = [
        _message(extra: {'text': 'Plain *bold* _italic_ `code` https://example.com'}),
        _message(mine: true, extra: {'text': 'Mine with https://example.com'}),
        _message(kind: 'BUTTONS', extra: {
          'text': 'Choose',
          'buttons': [
            {'id': 'a', 'title': 'Yes'},
            {'id': 'b', 'title': 'No'},
          ],
        }),
        _message(kind: 'LOCATION', extra: {'latitude': -1.2, 'longitude': 36.8}),
        _message(kind: 'CONTACTS', extra: {'contactsSummary': 'Mama 0712345678'}),
        _message(mine: true, kind: 'BUTTON_REPLY', extra: {'actionTitle': 'Yes'}),
      ];
      for (final look in ThemeKey.values) {
        await _show(tester, Column(children: [for (final m in messages) MessageBubble(message: m, live: true, actions: _actions())]), look: look);
        expect(tester.takeException(), isNull, reason: look.name);
        expect(find.text('Choose'), findsOneWidget, reason: look.name);
      }
    });
  });

  group('selecting text', () {
    testWidgets('a message can sit inside a selection area and its buttons still work', (tester) async {
      final seen = <String>[];
      final actions = BubbleActions(
        onButton: (b) => seen.add(b.id),
        onRow: (_) {},
        onFindContact: (_) {},
        onOpenRequest: (_) {},
        onOpenBusiness: (_) {},
        onApproveWithOtp: (_, {required bool business}) {},
        onLink: (_) {},
        onError: (_) {},
        loadFile: (_) async => throw UnimplementedError(),
        device: _NoDevice(),
      );
      final m = _message(kind: 'BUTTONS', extra: {
        'text': 'Choose one',
        'buttons': [
          {'id': 'yes', 'title': 'Yes'},
        ],
      });
      await _show(tester, MessageBubble(message: m, live: true, actions: actions));

      expect(find.byType(SelectableRegion), findsOneWidget);
      await tester.tap(find.text('Yes'));
      await tester.pump();
      expect(seen, ['yes']);
    });

    testWidgets('a long press on the words selects them', (tester) async {
      await _show(tester, MessageBubble(message: _message(extra: {'text': 'Select these words'}), live: true, actions: _actions()));
      final gesture = await tester.startGesture(tester.getCenter(find.text('Select these words')));
      await tester.pump(const Duration(milliseconds: 700));
      await gesture.up();
      await tester.pump();
      expect(tester.takeException(), isNull);
      expect(find.byType(SelectableRegion), findsOneWidget);
    });
  });

  group('the typeface', () {
    test('Inter is bundled and declared', () {
      for (final f in ['Regular', 'Medium', 'SemiBold', 'Bold', 'Italic']) {
        expect(File('assets/fonts/inter/Inter-$f.ttf').existsSync(), isTrue, reason: 'Inter-$f.ttf');
      }
      expect(File('assets/fonts/inter/LICENSE.txt').existsSync(), isTrue);
      final pubspec = File('pubspec.yaml').readAsStringSync();
      expect(pubspec, contains('family: Inter'));
      for (final f in ['Regular', 'Medium', 'SemiBold', 'Bold', 'Italic']) {
        expect(pubspec, contains('assets/fonts/inter/Inter-$f.ttf'));
      }
    });
  });
}
