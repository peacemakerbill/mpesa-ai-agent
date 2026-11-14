import 'dart:math' as math;

import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

/// Makes the Enter key of a physical keyboard send the message, as in other chat apps. Shift+Enter and Alt+Enter start a new
/// line, and so do Ctrl+Enter and Cmd+Enter. Use it as the `onKeyEvent` of the message box's [FocusNode]. The on-screen Enter key
/// of a phone is not a key event here and still starts a new line, so a long message can be written there and sent with the
/// send button.
///
/// [controller] is the message box's own controller. The new line is put in at the cursor here, instead of relying on the text
/// field, which does not add one for every key combination on every platform. [canSend] is false while a send is already on its
/// way, in which case a plain Enter is swallowed so it does not add a blank line. [send] is called only when there is something
/// to send.
KeyEventResult handleEnterToSend(
  KeyEvent event, {
  required TextEditingController controller,
  required bool canSend,
  required bool hasAttachment,
  required VoidCallback send,
}) {
  if (event.logicalKey != LogicalKeyboardKey.enter && event.logicalKey != LogicalKeyboardKey.numpadEnter) return KeyEventResult.ignored;
  final value = controller.value;
  // While an input method is still composing a word (Chinese, Japanese, some Android keyboards) Enter confirms the word.
  if (value.composing.isValid && !value.composing.isCollapsed) return KeyEventResult.ignored;
  final keys = HardwareKeyboard.instance;
  if (keys.isShiftPressed || keys.isAltPressed || keys.isControlPressed || keys.isMetaPressed) {
    // A held key repeats, so the line breaks do too, like any other key. The release is swallowed.
    if (event is! KeyUpEvent) insertNewLine(controller);
    return KeyEventResult.handled;
  }
  // Act on the key going down only, but swallow the matching repeat and release too so no stray line break is added.
  if (event is! KeyDownEvent) return KeyEventResult.handled;
  if (canSend && (value.text.trim().isNotEmpty || hasAttachment)) send();
  return KeyEventResult.handled;
}

/// Puts a line break where the cursor is, in place of any selected text, and leaves the cursor after it.
void insertNewLine(TextEditingController controller) {
  final v = controller.value;
  final sel = v.selection;
  final start = sel.isValid ? math.min(sel.start, sel.end) : v.text.length;
  final end = sel.isValid ? math.max(sel.start, sel.end) : v.text.length;
  controller.value = v.copyWith(
    text: v.text.replaceRange(start, end, '\n'),
    selection: TextSelection.collapsed(offset: start + 1),
    composing: TextRange.empty,
  );
}
