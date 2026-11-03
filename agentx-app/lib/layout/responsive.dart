import 'dart:math' as math;

import 'package:flutter/material.dart';

/// The three layouts of the app.
///
/// compact: a phone. One column and a bottom bar.
/// medium: a large phone on its side, or a small tablet. One column and a side rail.
/// expanded: a big tablet or a desktop window. Chat and requests side by side.
enum ScreenSize { compact, medium, expanded }

class Breakpoints {
  const Breakpoints._();

  static const double medium = 600;
  static const double expanded = 900;

  static ScreenSize forWidth(double width) {
    if (width >= expanded) return ScreenSize.expanded;
    if (width >= medium) return ScreenSize.medium;
    return ScreenSize.compact;
  }

  /// The space left and right of the content, for a given width.
  static double gutter(double width) {
    if (width < 360) return 12;
    if (width < medium) return 16;
    if (width < expanded) return 24;
    return 32;
  }
}

extension LayoutContext on BuildContext {
  /// The size class of the whole window. Inside a pane, use a LayoutBuilder instead, because the pane is narrower than the window.
  ScreenSize get screenSize => Breakpoints.forWidth(MediaQuery.sizeOf(this).width);
}

/// The side padding that keeps content at most [maxWidth] wide and centred, but never closer to the edge than the gutter.
double sidePad(double width, {double maxWidth = 680}) => math.max(Breakpoints.gutter(width), (width - maxWidth) / 2);

/// A scrolling column that stays readable on any width: full width on a phone, a centred column of [maxWidth] on a big screen.
class CenteredList extends StatelessWidget {
  const CenteredList({
    super.key,
    required this.children,
    this.maxWidth = 680,
    this.top = 16,
    this.bottom = 28,
    this.physics,
    this.controller,
  });

  final List<Widget> children;
  final double maxWidth;
  final double top;
  final double bottom;
  final ScrollPhysics? physics;
  final ScrollController? controller;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, box) {
        final side = sidePad(box.maxWidth, maxWidth: maxWidth);
        return ListView(
          controller: controller,
          physics: physics,
          padding: EdgeInsets.fromLTRB(side, top, side, bottom),
          children: children,
        );
      },
    );
  }
}

/// Lets a screen ask the home screen to open a request. In the two pane layout the request opens in the right pane,
/// on a phone the function is null and the screen pushes the request itself.
class RequestPane extends InheritedWidget {
  const RequestPane({super.key, required this.open, required super.child});

  final void Function(String requestId)? open;

  static void Function(String requestId)? openerOf(BuildContext context) =>
      context.getInheritedWidgetOfExactType<RequestPane>()?.open;

  @override
  bool updateShouldNotify(RequestPane oldWidget) => open != oldWidget.open;
}
