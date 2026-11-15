import 'package:flutter/material.dart';

/// How the logo moves.
enum BrandMotion {
  /// Drawn complete, nothing moves. For small marks in headers and lists.
  still,

  /// Draws itself once, then a coin crosses the bridge, then it rests. It stops by itself, so tests and screens settle.
  intro,

  /// Draws, rests, wipes off and starts again. For waiting.
  loop,
}

/// The Daraja Agent-X logo: a bridge arch (Daraja is Swahili for bridge) woven with a red curve, which together make an X.
/// The tile is M-Pesa green and the curve is M-Pesa red, on every theme. The same drawing exists as SVG in docs/assets.
///
/// It is drawn with a painter, not an image, so it is sharp at every size, needs no extra package and costs one small layer.
class BrandMark extends StatefulWidget {
  const BrandMark({super.key, this.size = 40, this.motion = BrandMotion.still, this.ring = false});

  final double size;
  final BrandMotion motion;

  /// A thin light ring round the tile, so it stands out on a band of the same green.
  final bool ring;

  @override
  State<BrandMark> createState() => _BrandMarkState();
}

class _BrandMarkState extends State<BrandMark> with SingleTickerProviderStateMixin {
  late final AnimationController _c = AnimationController(vsync: this);
  bool _reduced = false;
  bool _started = false;

  Duration get _length => widget.motion == BrandMotion.intro ? const Duration(milliseconds: 2600) : const Duration(milliseconds: 3200);

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final reduced = MediaQuery.maybeDisableAnimationsOf(context) ?? false;
    if (_started && reduced == _reduced) return;
    _started = true;
    _reduced = reduced;
    _run();
  }

  @override
  void didUpdateWidget(covariant BrandMark old) {
    super.didUpdateWidget(old);
    if (old.motion != widget.motion) _run();
  }

  void _run() {
    _c.stop();
    if (widget.motion == BrandMotion.still || _reduced) {
      _c.value = 1;
      return;
    }
    _c.duration = _length;
    _c.value = 0;
    if (widget.motion == BrandMotion.loop) {
      _c.repeat();
    } else {
      _c.forward();
    }
  }

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final still = widget.motion == BrandMotion.still || _reduced;
    return Semantics(
      label: 'Daraja Agent-X logo',
      image: true,
      child: SizedBox(
        width: widget.size,
        height: widget.size,
        child: RepaintBoundary(
          child: AnimatedBuilder(
            animation: _c,
            builder: (context, _) => CustomPaint(
              painter: _MarkPainter(
                timeline: still ? BrandTimeline.complete : BrandTimeline.at(widget.motion, _c.value),
                ring: widget.ring,
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// A spinner made of the logo, for the places that wait for the server. Use it in place of a page-sized progress circle.
class BrandLoader extends StatelessWidget {
  const BrandLoader({super.key, this.size = 64, this.label});

  final double size;

  /// A few words under the logo, for example "Loading your chats".
  final String? label;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    return Center(
      child: Semantics(
        label: label ?? 'Loading',
        liveRegion: true,
        child: ExcludeSemantics(
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            BrandMark(size: size, motion: BrandMotion.loop),
            if (label != null) ...[
              const SizedBox(height: 14),
              Text(label!, style: text.bodySmall, textAlign: TextAlign.center),
            ],
          ]),
        ),
      ),
    );
  }
}

/// How much of each part is drawn at one moment. Everything is a fraction from 0 to 1.
class BrandTimeline {
  const BrandTimeline({required this.tile, required this.archFrom, required this.archTo, required this.flowFrom, required this.flowTo, this.coin});

  /// The tile growing in: 0 is gone, 1 is full size.
  final double tile;

  /// The part of the white arch that is drawn, from and to along its length.
  final double archFrom;
  final double archTo;

  /// The part of the red curve that is drawn.
  final double flowFrom;
  final double flowTo;

  /// Where the coin is along the red curve, or null when it is not showing.
  final double? coin;

  static const BrandTimeline complete = BrandTimeline(tile: 1, archFrom: 0, archTo: 1, flowFrom: 0, flowTo: 1);

  /// The picture at [t] (0 to 1) of one run of a motion.
  static BrandTimeline at(BrandMotion motion, double t) {
    switch (motion) {
      case BrandMotion.still:
        return complete;
      case BrandMotion.intro:
        return BrandTimeline(
          tile: _span(t, 0, .14),
          archFrom: 0,
          archTo: _span(t, .08, .46),
          flowFrom: 0,
          flowTo: _span(t, .3, .68),
          coin: t < .72 ? null : _span(t, .72, 1),
        );
      case BrandMotion.loop:
        return BrandTimeline(
          tile: 1,
          archFrom: _span(t, .66, 1),
          archTo: _span(t, 0, .34),
          flowFrom: _span(t, .72, 1),
          flowTo: _span(t, .08, .42),
        );
    }
  }

  /// 0 before [from], 1 after [to], and an eased climb between.
  static double _span(double t, double from, double to) {
    if (t <= from) return 0;
    if (t >= to) return 1;
    return Curves.easeInOutCubic.transform((t - from) / (to - from));
  }
}

class _MarkPainter extends CustomPainter {
  const _MarkPainter({required this.timeline, required this.ring});

  final BrandTimeline timeline;
  final bool ring;

  static const Color _red = Color(0xFFE4202D);
  static const Color _deep = Color(0xFF0B7A38);
  static const Color _light = Color(0xFF5BC554);

  /// The white arch, in the 512 by 512 drawing the SVG logo also uses.
  static final Path _arch = Path()
    ..moveTo(92, 408)
    ..cubicTo(150, 140, 362, 140, 420, 408);

  /// The red curve: the arch turned upside down.
  static final Path _flow = Path()
    ..moveTo(92, 104)
    ..cubicTo(150, 372, 362, 372, 420, 104);

  /// The part of [path] from [from] to [to], as fractions of its length.
  static Path _part(Path path, double from, double to) {
    if (to <= from) return Path();
    final m = path.computeMetrics().first;
    return m.extractPath(m.length * from, m.length * to);
  }

  @override
  void paint(Canvas canvas, Size size) {
    final k = size.width / 512;
    canvas.save();
    canvas.scale(k);
    const full = Rect.fromLTWH(0, 0, 512, 512);
    final tile = timeline.tile;

    // The tile.
    canvas.save();
    canvas.translate(256, 256);
    canvas.scale(0.86 + 0.14 * tile);
    canvas.translate(-256, -256);
    final tileBox = RRect.fromRectAndRadius(full, const Radius.circular(116));
    canvas.saveLayer(full, Paint()..color = Colors.white.withAlpha((255 * tile).round()));
    canvas.drawRRect(
      tileBox,
      Paint()..shader = const LinearGradient(begin: Alignment.topLeft, end: Alignment.bottomRight, colors: [_light, _deep]).createShader(full),
    );
    if (ring) {
      canvas.drawRRect(
        tileBox.deflate(5),
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = 10
          ..color = Colors.white.withAlpha(150),
      );
    }
    canvas.restore();
    canvas.restore();

    if (tile < 0.5) {
      canvas.restore();
      return;
    }

    final arch = _part(_arch, timeline.archFrom, timeline.archTo);
    final flow = _part(_flow, timeline.flowFrom, timeline.flowTo);
    final strand = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 46
      ..strokeCap = StrokeCap.butt;
    final halo = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 78
      ..blendMode = BlendMode.clear;

    // The red curve, with a gap cut where the white arch passes over it on the right.
    canvas.saveLayer(full, Paint());
    canvas.drawPath(flow, strand..color = _red);
    canvas.save();
    canvas.clipRect(const Rect.fromLTWH(256, 0, 256, 512));
    canvas.drawPath(arch, halo);
    canvas.restore();
    canvas.restore();

    // The white arch, with a gap cut where the red curve passes over it on the left.
    canvas.saveLayer(full, Paint());
    canvas.drawPath(arch, strand..color = Colors.white);
    canvas.save();
    canvas.clipRect(const Rect.fromLTWH(0, 0, 256, 512));
    canvas.drawPath(flow, halo);
    canvas.restore();
    canvas.restore();

    // The coin that crosses the bridge.
    final coin = timeline.coin;
    if (coin != null) {
      final m = _flow.computeMetrics().first;
      final at = m.getTangentForOffset(m.length * coin)?.position;
      if (at != null) {
        final fade = (coin < .1 ? coin / .1 : coin > .9 ? (1 - coin) / .1 : 1.0).clamp(0.0, 1.0);
        canvas.drawCircle(at, 15, Paint()..color = Colors.white.withAlpha((255 * fade).round()));
        canvas.drawCircle(
          at,
          15,
          Paint()
            ..style = PaintingStyle.stroke
            ..strokeWidth = 5
            ..color = _deep.withAlpha((255 * fade).round()),
        );
      }
    }
    canvas.restore();
  }

  @override
  bool shouldRepaint(covariant _MarkPainter old) =>
      old.ring != ring ||
      old.timeline.tile != timeline.tile ||
      old.timeline.archFrom != timeline.archFrom ||
      old.timeline.archTo != timeline.archTo ||
      old.timeline.flowFrom != timeline.flowFrom ||
      old.timeline.flowTo != timeline.flowTo ||
      old.timeline.coin != timeline.coin;
}

/// The mark with the name beside it, for the first page and the settings page.
class BrandLockup extends StatelessWidget {
  const BrandLockup({super.key, this.size = 64, this.motion = BrandMotion.still, this.nameColor, this.xColor});

  /// The height of the mark. The words scale with it.
  final double size;
  final BrandMotion motion;
  final Color? nameColor;
  final Color? xColor;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final name = nameColor ?? scheme.onSurface;
    return Semantics(
      label: 'Daraja Agent-X',
      child: ExcludeSemantics(
        child: Row(mainAxisSize: MainAxisSize.min, children: [
          BrandMark(size: size, motion: motion),
          SizedBox(width: size * 0.26),
          Column(crossAxisAlignment: CrossAxisAlignment.start, mainAxisSize: MainAxisSize.min, children: [
            Text('Daraja', style: TextStyle(fontSize: size * 0.4, fontWeight: FontWeight.w800, height: 1.05, letterSpacing: -size * 0.01, color: name)),
            Text.rich(
              TextSpan(children: [
                TextSpan(text: 'Agent-', style: TextStyle(fontWeight: FontWeight.w600, color: name.withAlpha(210))),
                TextSpan(text: 'X', style: TextStyle(fontWeight: FontWeight.w800, color: xColor ?? const Color(0xFFE4202D))),
              ]),
              style: TextStyle(fontSize: size * 0.32, height: 1.1, letterSpacing: -size * 0.006),
            ),
          ]),
        ]),
      ),
    );
  }
}
