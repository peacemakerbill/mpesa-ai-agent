import 'package:agentx_app/widgets/enter_to_send.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('the message box, with a keyboard', () {
    late TextEditingController text;
    late int sent;
    var canSend = true;
    var hasAttachment = false;

    Future<void> build(WidgetTester tester) async {
      text = TextEditingController();
      sent = 0;
      canSend = true;
      hasAttachment = false;
      addTearDown(text.dispose);
      final focus = FocusNode(
        onKeyEvent: (node, event) => handleEnterToSend(
          event,
          controller: text,
          canSend: canSend,
          hasAttachment: hasAttachment,
          send: () => sent++,
        ),
      );
      addTearDown(focus.dispose);
      await tester.pumpWidget(MaterialApp(
        home: Scaffold(body: TextField(controller: text, focusNode: focus, autofocus: true, minLines: 1, maxLines: 5)),
      ));
      await tester.pump();
    }

    Future<void> enterWith(WidgetTester tester, LogicalKeyboardKey? modifier, {LogicalKeyboardKey key = LogicalKeyboardKey.enter}) async {
      if (modifier != null) await tester.sendKeyDownEvent(modifier);
      await tester.sendKeyEvent(key);
      if (modifier != null) await tester.sendKeyUpEvent(modifier);
      await tester.pump();
    }

    testWidgets('Enter sends the message and leaves the words alone', (tester) async {
      await build(tester);
      await tester.enterText(find.byType(TextField), 'hello');

      await enterWith(tester, null);

      expect(sent, 1);
      expect(text.text, 'hello');
    });

    testWidgets('the numpad Enter sends too', (tester) async {
      await build(tester);
      await tester.enterText(find.byType(TextField), 'hello');

      await enterWith(tester, null, key: LogicalKeyboardKey.numpadEnter);

      expect(sent, 1);
      expect(text.text, 'hello');
    });

    testWidgets('Shift+Enter starts a new line and sends nothing', (tester) async {
      await build(tester);
      await tester.enterText(find.byType(TextField), 'hello');

      await enterWith(tester, LogicalKeyboardKey.shiftLeft);

      expect(sent, 0);
      expect(text.text, 'hello\n');
    });

    testWidgets('Alt+Enter starts a new line and sends nothing', (tester) async {
      await build(tester);
      await tester.enterText(find.byType(TextField), 'hello');

      await enterWith(tester, LogicalKeyboardKey.altLeft);

      expect(sent, 0);
      expect(text.text, 'hello\n');
      expect(text.selection, const TextSelection.collapsed(offset: 6));
    });

    testWidgets('Ctrl+Enter starts a new line as well', (tester) async {
      await build(tester);
      await tester.enterText(find.byType(TextField), 'hello');

      await enterWith(tester, LogicalKeyboardKey.controlLeft);

      expect(sent, 0);
      expect(text.text, 'hello\n');
    });

    testWidgets('the new line goes where the cursor is', (tester) async {
      await build(tester);
      await tester.enterText(find.byType(TextField), 'hello');
      text.selection = const TextSelection.collapsed(offset: 2);

      await enterWith(tester, LogicalKeyboardKey.altLeft);

      expect(text.text, 'he\nllo');
      expect(text.selection, const TextSelection.collapsed(offset: 3));
    });

    testWidgets('selected words are replaced by the new line', (tester) async {
      await build(tester);
      await tester.enterText(find.byType(TextField), 'hello');
      text.selection = const TextSelection(baseOffset: 1, extentOffset: 4);

      await enterWith(tester, LogicalKeyboardKey.shiftLeft);

      expect(text.text, 'h\no');
    });

    testWidgets('several new lines can be added one after the other', (tester) async {
      await build(tester);
      await tester.enterText(find.byType(TextField), 'a');

      await enterWith(tester, LogicalKeyboardKey.shiftLeft);
      await tester.enterText(find.byType(TextField), 'a\nb');
      await enterWith(tester, LogicalKeyboardKey.altLeft);

      expect(text.text, 'a\nb\n');
      expect(sent, 0);
    });

    testWidgets('Enter on an empty box sends nothing and adds no line', (tester) async {
      await build(tester);
      await tester.enterText(find.byType(TextField), '   ');

      await enterWith(tester, null);

      expect(sent, 0);
      expect(text.text, '   ');
    });

    testWidgets('a picture waiting in the tray can be sent without words', (tester) async {
      await build(tester);
      hasAttachment = true;

      await enterWith(tester, null);

      expect(sent, 1);
      expect(text.text, '');
    });

    testWidgets('Enter does not send twice while a send is on its way, and adds no line', (tester) async {
      await build(tester);
      await tester.enterText(find.byType(TextField), 'hello');
      canSend = false;

      await enterWith(tester, null);

      expect(sent, 0);
      expect(text.text, 'hello');
    });
  });

  group('the key handler on its own', () {
    KeyDownEvent down(LogicalKeyboardKey key) => KeyDownEvent(physicalKey: PhysicalKeyboardKey.enter, logicalKey: key, timeStamp: Duration.zero);

    test('other keys are left alone', () {
      final c = TextEditingController(text: 'x');
      final r = handleEnterToSend(down(LogicalKeyboardKey.keyA), controller: c, canSend: true, hasAttachment: false, send: () => fail('sent'));
      expect(r, KeyEventResult.ignored);
      expect(c.text, 'x');
    });

    test('Enter while a word is still being composed confirms the word and is left to the input method', () {
      final c = TextEditingController.fromValue(const TextEditingValue(text: 'ni', selection: TextSelection.collapsed(offset: 2), composing: TextRange(start: 0, end: 2)));
      final r = handleEnterToSend(down(LogicalKeyboardKey.enter), controller: c, canSend: true, hasAttachment: false, send: () => fail('sent'));
      expect(r, KeyEventResult.ignored);
      expect(c.text, 'ni');
    });

    test('a new line with no cursor goes at the end', () {
      final c = TextEditingController.fromValue(const TextEditingValue(text: 'abc'));
      insertNewLine(c);
      expect(c.text, 'abc\n');
      expect(c.selection, const TextSelection.collapsed(offset: 4));
    });

    test('a backwards selection is replaced too', () {
      final c = TextEditingController.fromValue(const TextEditingValue(text: 'abcd', selection: TextSelection(baseOffset: 3, extentOffset: 1)));
      insertNewLine(c);
      expect(c.text, 'a\nd');
    });
  });
}
