import 'dart:ui' show FontFeature;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// The ten looks of the app. The same ten exist in the browser console (console/console.html).
/// The first seven are everyday looks, the last three borrow the green, red and white of M-Pesa.
enum ThemeKey { daylight, graphite, pwani, mono, matatu, kahawa, blueprint, savanna, lipa, receipt }

extension ThemeKeyX on ThemeKey {
  String get label {
    switch (this) {
      case ThemeKey.daylight:
        return 'Daylight';
      case ThemeKey.graphite:
        return 'Graphite';
      case ThemeKey.pwani:
        return 'Pwani';
      case ThemeKey.mono:
        return 'Mono';
      case ThemeKey.matatu:
        return 'Matatu';
      case ThemeKey.kahawa:
        return 'Kahawa';
      case ThemeKey.blueprint:
        return 'Blueprint';
      case ThemeKey.savanna:
        return 'Savanna';
      case ThemeKey.lipa:
        return 'Lipa';
      case ThemeKey.receipt:
        return 'Receipt';
    }
  }

  /// The heading the picker files this look under.
  String get group => index < ThemeKey.savanna.index ? 'Everyday' : 'M-Pesa colours';

  String get description {
    switch (this) {
      case ThemeKey.daylight:
        return 'Light, cool grey, cobalt';
      case ThemeKey.graphite:
        return 'Dark, neutral, amber';
      case ThemeKey.pwani:
        return 'Coastal teal, soft white';
      case ThemeKey.mono:
        return 'Black on white, serif headings';
      case ThemeKey.matatu:
        return 'Street yellow, black, red';
      case ThemeKey.kahawa:
        return 'Espresso brown, teal';
      case ThemeKey.blueprint:
        return 'Drafting navy, grid, cyan';
      case ThemeKey.savanna:
        return 'Bright M-Pesa green, red marks';
      case ThemeKey.lipa:
        return 'White with a red lead';
      case ThemeKey.receipt:
        return 'Paper slip, typewriter type';
    }
  }

  /// Reads a saved name. Anything unknown falls back to Daylight.
  static ThemeKey parse(String? name) {
    for (final k in ThemeKey.values) {
      if (k.name == name) return k;
    }
    return ThemeKey.daylight;
  }
}

/// The colours of one theme. Screens never hard-code a colour, they read these.
class Palette {
  const Palette({
    required this.bg,
    required this.surface,
    required this.surface2,
    required this.line,
    required this.lineStrong,
    required this.ink,
    required this.ink2,
    required this.ink3,
    required this.accent,
    required this.accentInk,
    required this.accentSoft,
    required this.accentLine,
    required this.good,
    required this.warn,
    required this.bad,
    required this.headBg,
    required this.headInk,
    required this.headLine,
  });

  final Color bg, surface, surface2, line, lineStrong;
  final Color ink, ink2, ink3;
  final Color accent, accentInk, accentSoft, accentLine;
  final Color good, warn, bad;
  final Color headBg, headInk, headLine;

  bool get dark => bg.computeLuminance() < ink.computeLuminance();
  bool get headDark => headBg.computeLuminance() < 0.4;

  static const daylight = Palette(
    bg: Color(0xFFF3F4F7),
    surface: Color(0xFFFFFFFF),
    surface2: Color(0xFFF0F2F6),
    line: Color(0xFFE1E4EA),
    lineStrong: Color(0xFFC9CED8),
    ink: Color(0xFF11151B),
    ink2: Color(0xFF4A5362),
    ink3: Color(0xFF646E7F),
    accent: Color(0xFF2457D6),
    accentInk: Color(0xFFFFFFFF),
    accentSoft: Color(0xFFE7EDFC),
    accentLine: Color(0xFFB6C8F4),
    good: Color(0xFF137A4A),
    warn: Color(0xFF9A5D00),
    bad: Color(0xFFC42B3A),
    headBg: Color(0xFFFFFFFF),
    headInk: Color(0xFF11151B),
    headLine: Color(0xFFE1E4EA),
  );

  static const graphite = Palette(
    bg: Color(0xFF0D0F12),
    surface: Color(0xFF14171C),
    surface2: Color(0xFF1A1E24),
    line: Color(0xFF242932),
    lineStrong: Color(0xFF343B46),
    ink: Color(0xFFE7E9EE),
    ink2: Color(0xFFA0A8B5),
    ink3: Color(0xFF808895),
    accent: Color(0xFFF0A63A),
    accentInk: Color(0xFF1B1304),
    accentSoft: Color(0xFF2A2110),
    accentLine: Color(0xFF5A4318),
    good: Color(0xFF52C68F),
    warn: Color(0xFFF0A63A),
    bad: Color(0xFFF07272),
    headBg: Color(0xFF101317),
    headInk: Color(0xFFE7E9EE),
    headLine: Color(0xFF242932),
  );

  static const pwani = Palette(
    bg: Color(0xFFEDF3F3),
    surface: Color(0xFFFFFFFF),
    surface2: Color(0xFFE4EDED),
    line: Color(0xFFD3E0E0),
    lineStrong: Color(0xFFAEC6C7),
    ink: Color(0xFF0F2B33),
    ink2: Color(0xFF3B5961),
    ink3: Color(0xFF567078),
    accent: Color(0xFF0A7480),
    accentInk: Color(0xFFFFFFFF),
    accentSoft: Color(0xFFD9EEF0),
    accentLine: Color(0xFF8FC6CC),
    good: Color(0xFF0B6B4B),
    warn: Color(0xFF8A4B1F),
    bad: Color(0xFFB3261E),
    headBg: Color(0xFF0A5C66),
    headInk: Color(0xFFFFFFFF),
    headLine: Color(0xFF0A5C66),
  );

  static const mono = Palette(
    bg: Color(0xFFFFFFFF),
    surface: Color(0xFFFFFFFF),
    surface2: Color(0xFFF3F3F3),
    line: Color(0xFF111111),
    lineStrong: Color(0xFF111111),
    ink: Color(0xFF000000),
    ink2: Color(0xFF2E2E2E),
    ink3: Color(0xFF5C5C5C),
    accent: Color(0xFF000000),
    accentInk: Color(0xFFFFFFFF),
    accentSoft: Color(0xFFECECEC),
    accentLine: Color(0xFF000000),
    good: Color(0xFF0A6A2C),
    warn: Color(0xFF8A5300),
    bad: Color(0xFFB00020),
    headBg: Color(0xFFFFFFFF),
    headInk: Color(0xFF000000),
    headLine: Color(0xFF000000),
  );

  static const savanna = Palette(
    bg: Color(0xFFF6F8F5),
    surface: Color(0xFFFFFFFF),
    surface2: Color(0xFFEDF2EE),
    line: Color(0xFFDCE5DE),
    lineStrong: Color(0xFFB9C9BE),
    ink: Color(0xFF10201A),
    ink2: Color(0xFF3F5348),
    ink3: Color(0xFF586C60),
    accent: Color(0xFF087F36),
    accentInk: Color(0xFFFFFFFF),
    accentSoft: Color(0xFFE1F3E7),
    accentLine: Color(0xFF9FD3B1),
    good: Color(0xFF087F36),
    warn: Color(0xFF8F5B00),
    bad: Color(0xFFC8102E),
    headBg: Color(0xFF087F36),
    headInk: Color(0xFFFFFFFF),
    headLine: Color(0xFF087F36),
  );

  static const lipa = Palette(
    bg: Color(0xFFF4F4F5),
    surface: Color(0xFFFFFFFF),
    surface2: Color(0xFFEEEEF0),
    line: Color(0xFFE2E2E5),
    lineStrong: Color(0xFFC8C8CE),
    ink: Color(0xFF17171A),
    ink2: Color(0xFF4F4F57),
    ink3: Color(0xFF676770),
    accent: Color(0xFFC8001A),
    accentInk: Color(0xFFFFFFFF),
    accentSoft: Color(0xFFFBE6E9),
    accentLine: Color(0xFFF0A8B1),
    good: Color(0xFF0A7A3B),
    warn: Color(0xFF8F5B00),
    bad: Color(0xFFC8001A),
    headBg: Color(0xFFFFFFFF),
    headInk: Color(0xFF17171A),
    headLine: Color(0xFFC8001A),
  );

  static const matatu = Palette(
    bg: Color(0xFFFFD400),
    surface: Color(0xFFFFFFFF),
    surface2: Color(0xFFFFF3B0),
    line: Color(0xFF111111),
    lineStrong: Color(0xFF111111),
    ink: Color(0xFF111111),
    ink2: Color(0xFF2B2B2B),
    ink3: Color(0xFF4A4400),
    accent: Color(0xFFD4002A),
    accentInk: Color(0xFFFFFFFF),
    accentSoft: Color(0xFFFFE7A1),
    accentLine: Color(0xFF111111),
    good: Color(0xFF0A6B2F),
    warn: Color(0xFF7A4A00),
    bad: Color(0xFFB00020),
    headBg: Color(0xFF111111),
    headInk: Color(0xFFFFD400),
    headLine: Color(0xFFD4002A),
  );

  static const kahawa = Palette(
    bg: Color(0xFF1B1410),
    surface: Color(0xFF251C16),
    surface2: Color(0xFF2E231C),
    line: Color(0xFF3A2D24),
    lineStrong: Color(0xFF5A4636),
    ink: Color(0xFFF3E9DC),
    ink2: Color(0xFFCDBBA6),
    ink3: Color(0xFFA8927B),
    accent: Color(0xFF6CC4B5),
    accentInk: Color(0xFF0D1F1C),
    accentSoft: Color(0xFF17302C),
    accentLine: Color(0xFF2F6B62),
    good: Color(0xFF86D0A0),
    warn: Color(0xFFE6B450),
    bad: Color(0xFFF08A7C),
    headBg: Color(0xFF140E0B),
    headInk: Color(0xFFF3E9DC),
    headLine: Color(0xFF3A2D24),
  );

  static const blueprint = Palette(
    bg: Color(0xFF0E2A47),
    surface: Color(0xFF123354),
    surface2: Color(0xFF173C63),
    line: Color(0xFF2D5B8C),
    lineStrong: Color(0xFF4A7DB3),
    ink: Color(0xFFE8F1FB),
    ink2: Color(0xFFB4CDE6),
    ink3: Color(0xFF93B3D4),
    accent: Color(0xFF7FD6FF),
    accentInk: Color(0xFF06223A),
    accentSoft: Color(0xFF16436E),
    accentLine: Color(0xFF3D7DB3),
    good: Color(0xFF7BE0A8),
    warn: Color(0xFFFFD24A),
    bad: Color(0xFFFF8F8F),
    headBg: Color(0xFF0A2037),
    headInk: Color(0xFFE8F1FB),
    headLine: Color(0xFF2D5B8C),
  );

  static const receipt = Palette(
    bg: Color(0xFFECEBE6),
    surface: Color(0xFFFBFBF8),
    surface2: Color(0xFFF1F0EB),
    line: Color(0xFFB5B4AE),
    lineStrong: Color(0xFF6B6A65),
    ink: Color(0xFF1D1D1B),
    ink2: Color(0xFF45453F),
    ink3: Color(0xFF62625C),
    accent: Color(0xFF0B6B2E),
    accentInk: Color(0xFFFFFFFF),
    accentSoft: Color(0xFFE4EEE2),
    accentLine: Color(0xFF9DBE9A),
    good: Color(0xFF0B6B2E),
    warn: Color(0xFF8A5300),
    bad: Color(0xFFB3001B),
    headBg: Color(0xFFFBFBF8),
    headInk: Color(0xFF1D1D1B),
    headLine: Color(0xFF1D1D1B),
  );

  static Palette of(ThemeKey key) {
    switch (key) {
      case ThemeKey.daylight:
        return daylight;
      case ThemeKey.graphite:
        return graphite;
      case ThemeKey.pwani:
        return pwani;
      case ThemeKey.mono:
        return mono;
      case ThemeKey.matatu:
        return matatu;
      case ThemeKey.kahawa:
        return kahawa;
      case ThemeKey.blueprint:
        return blueprint;
      case ThemeKey.savanna:
        return savanna;
      case ThemeKey.lipa:
        return lipa;
      case ThemeKey.receipt:
        return receipt;
    }
  }
}

/// What the palette alone cannot say: corner size, the heading typeface, and a few helpers. Read it with `context.tk`.
@immutable
class Tokens extends ThemeExtension<Tokens> {
  const Tokens({
    required this.key,
    required this.p,
    required this.radius,
    this.headFont,
    this.headFallback = const [],
    this.bodyFont,
    this.bodyFallback = const [],
  });

  final ThemeKey key;
  final Palette p;
  final double radius;
  final String? headFont;
  final List<String> headFallback;

  /// Only the Receipt look sets a body typeface, the others use the phone's own.
  final String? bodyFont;
  final List<String> bodyFallback;

  bool get square => radius == 0;
  BorderRadius get corners => BorderRadius.circular(radius);

  /// A soft tint of [c] on the card colour, for tags and callouts.
  Color tint(Color c) => Color.alphaBlend(c.withAlpha(p.dark ? 40 : 28), p.surface);

  RoundedRectangleBorder shape({bool outline = true, double scale = 1}) => RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(radius * scale),
        side: outline ? BorderSide(color: p.line) : BorderSide.none,
      );

  /// Figures that line up in columns, for amounts and codes.
  TextStyle numeric(double size, {FontWeight weight = FontWeight.w700, Color? color}) => TextStyle(
        fontFamily: headFont,
        fontFamilyFallback: headFallback,
        fontSize: size,
        fontWeight: weight,
        color: color ?? p.ink,
        letterSpacing: -size * 0.02,
        height: 1.05,
        fontFeatures: const [FontFeature.tabularFigures()],
      );

  static Tokens of(ThemeKey key) {
    final p = Palette.of(key);
    switch (key) {
      case ThemeKey.daylight:
        return Tokens(key: key, p: p, radius: 12);
      case ThemeKey.graphite:
        return Tokens(key: key, p: p, radius: 12);
      case ThemeKey.pwani:
        return Tokens(key: key, p: p, radius: 12);
      case ThemeKey.mono:
        return Tokens(key: key, p: p, radius: 0, headFont: 'Georgia', headFallback: const ['Times New Roman', 'Noto Serif', 'serif']);
      case ThemeKey.matatu:
        return Tokens(key: key, p: p, radius: 5, headFont: 'Arial Black', headFallback: const ['Roboto Black', 'Impact', 'sans-serif']);
      case ThemeKey.kahawa:
        return Tokens(key: key, p: p, radius: 12, headFont: 'Georgia', headFallback: const ['Times New Roman', 'Noto Serif', 'serif']);
      case ThemeKey.blueprint:
        return Tokens(key: key, p: p, radius: 3, headFont: 'Menlo', headFallback: const ['Roboto Mono', 'Courier New', 'monospace']);
      case ThemeKey.savanna:
        return Tokens(key: key, p: p, radius: 16);
      case ThemeKey.lipa:
        return Tokens(key: key, p: p, radius: 10);
      case ThemeKey.receipt:
        const mono = ['Courier New', 'Liberation Mono', 'Noto Sans Mono', 'monospace'];
        return Tokens(key: key, p: p, radius: 0, headFont: 'Courier', headFallback: mono, bodyFont: 'Courier', bodyFallback: mono);
    }
  }

  @override
  Tokens copyWith({ThemeKey? key, Palette? p, double? radius}) =>
      Tokens(
        key: key ?? this.key,
        p: p ?? this.p,
        radius: radius ?? this.radius,
        headFont: headFont,
        headFallback: headFallback,
        bodyFont: bodyFont,
        bodyFallback: bodyFallback,
      );

  @override
  Tokens lerp(ThemeExtension<Tokens>? other, double t) => other is Tokens && t >= 0.5 ? other : this;
}

extension TokensContext on BuildContext {
  Tokens get tk => Theme.of(this).extension<Tokens>() ?? Tokens.of(ThemeKey.daylight);
}

class AppTheme {
  const AppTheme._();

  static final Map<ThemeKey, ThemeData> _cache = {};

  /// One ThemeData per look, built once.
  static ThemeData build(ThemeKey key) => _cache.putIfAbsent(key, () => _make(key));

  static ThemeData _make(ThemeKey key) {
    final tk = Tokens.of(key);
    final p = tk.p;
    final r = tk.corners;

    final scheme = ColorScheme(
      brightness: p.dark ? Brightness.dark : Brightness.light,
      primary: p.accent,
      onPrimary: p.accentInk,
      primaryContainer: p.accentSoft,
      onPrimaryContainer: p.ink,
      secondary: p.accent,
      onSecondary: p.accentInk,
      secondaryContainer: p.accentSoft,
      onSecondaryContainer: p.ink,
      tertiary: p.warn,
      onTertiary: p.accentInk,
      error: p.bad,
      onError: p.dark ? const Color(0xFF1A0505) : const Color(0xFFFFFFFF),
      surface: p.surface,
      onSurface: p.ink,
      onSurfaceVariant: p.ink2,
      surfaceContainerLowest: p.bg,
      surfaceContainerLow: p.bg,
      surfaceContainer: p.surface,
      surfaceContainerHigh: p.surface,
      surfaceContainerHighest: p.surface2,
      outline: p.lineStrong,
      outlineVariant: p.line,
      surfaceTint: Colors.transparent,
    );

    TextStyle body(double size, {FontWeight weight = FontWeight.w400, Color? color, double height = 1.4}) =>
        TextStyle(
          fontFamily: tk.bodyFont,
          fontFamilyFallback: tk.bodyFallback,
          fontSize: size,
          fontWeight: weight,
          color: color ?? p.ink,
          height: height,
        );
    TextStyle head(double size, {FontWeight weight = FontWeight.w700, double spacing = 0, Color? color}) => TextStyle(
          fontFamily: tk.headFont,
          fontFamilyFallback: tk.headFallback,
          fontSize: size,
          fontWeight: weight,
          color: color ?? p.ink,
          letterSpacing: spacing,
          height: 1.2,
        );

    final text = TextTheme(
      headlineLarge: head(34, spacing: -0.8),
      headlineMedium: head(28, spacing: -0.5),
      headlineSmall: head(22, spacing: -0.3),
      titleLarge: head(19, weight: FontWeight.w700, spacing: -0.2),
      titleMedium: body(16, weight: FontWeight.w600),
      titleSmall: body(14, weight: FontWeight.w600),
      bodyLarge: body(16, height: 1.45),
      bodyMedium: body(14, height: 1.45),
      bodySmall: body(12, color: p.ink2),
      labelLarge: body(14, weight: FontWeight.w600),
      labelMedium: body(12, weight: FontWeight.w600, color: p.ink2),
      labelSmall: body(11, weight: FontWeight.w600, color: p.ink2),
    );

    final buttonShape = RoundedRectangleBorder(borderRadius: r);
    const minSize = Size(64, 52);

    return ThemeData(
      useMaterial3: true,
      colorScheme: scheme,
      brightness: scheme.brightness,
      scaffoldBackgroundColor: p.bg,
      canvasColor: p.bg,
      textTheme: text,
      splashFactory: tk.square ? NoSplash.splashFactory : null,
      extensions: <ThemeExtension<dynamic>>[tk],
      appBarTheme: AppBarTheme(
        backgroundColor: p.headBg,
        foregroundColor: p.headInk,
        elevation: 0,
        scrolledUnderElevation: 0,
        surfaceTintColor: Colors.transparent,
        centerTitle: false,
        titleTextStyle: head(20, color: p.headInk, spacing: -0.2),
        iconTheme: IconThemeData(color: p.headInk),
        actionsIconTheme: IconThemeData(color: p.headInk),
        shape: Border(bottom: BorderSide(color: p.headLine)),
        systemOverlayStyle: p.headDark ? SystemUiOverlayStyle.light : SystemUiOverlayStyle.dark,
      ),
      dividerTheme: DividerThemeData(color: p.line, space: 1, thickness: 1),
      progressIndicatorTheme: ProgressIndicatorThemeData(color: p.accent, linearTrackColor: p.surface2),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          backgroundColor: p.accent,
          foregroundColor: p.accentInk,
          disabledBackgroundColor: p.surface2,
          disabledForegroundColor: p.ink3,
          minimumSize: minSize,
          shape: buttonShape,
          elevation: 0,
          textStyle: body(15, weight: FontWeight.w600),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: p.ink,
          disabledForegroundColor: p.ink3,
          side: BorderSide(color: p.lineStrong),
          minimumSize: minSize,
          shape: buttonShape,
          textStyle: body(15, weight: FontWeight.w600),
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          foregroundColor: tk.square ? p.ink : p.accent,
          shape: buttonShape,
          textStyle: body(14, weight: FontWeight.w600),
        ),
      ),
      iconButtonTheme: IconButtonThemeData(style: IconButton.styleFrom(shape: buttonShape)),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: p.surface,
        contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 16),
        labelStyle: body(15, color: p.ink2),
        floatingLabelStyle: body(13, weight: FontWeight.w600, color: tk.square ? p.ink : p.accent),
        hintStyle: body(15, color: p.ink3),
        counterStyle: body(12, color: p.ink3),
        border: OutlineInputBorder(borderRadius: r, borderSide: BorderSide(color: p.lineStrong)),
        enabledBorder: OutlineInputBorder(borderRadius: r, borderSide: BorderSide(color: p.lineStrong)),
        focusedBorder: OutlineInputBorder(borderRadius: r, borderSide: BorderSide(color: p.accent, width: 2)),
        errorBorder: OutlineInputBorder(borderRadius: r, borderSide: BorderSide(color: p.bad)),
        focusedErrorBorder: OutlineInputBorder(borderRadius: r, borderSide: BorderSide(color: p.bad, width: 2)),
      ),
      snackBarTheme: SnackBarThemeData(
        behavior: SnackBarBehavior.floating,
        backgroundColor: p.ink,
        contentTextStyle: body(14, color: p.bg),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(tk.radius * 0.75)),
      ),
      popupMenuTheme: PopupMenuThemeData(
        color: p.surface,
        surfaceTintColor: Colors.transparent,
        shape: tk.shape(),
        textStyle: body(14),
      ),
      bottomSheetTheme: BottomSheetThemeData(
        backgroundColor: p.surface,
        surfaceTintColor: Colors.transparent,
        showDragHandle: true,
        dragHandleColor: p.lineStrong,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(tk.radius * 1.6)),
          side: BorderSide(color: p.line),
        ),
      ),
    );
  }
}
