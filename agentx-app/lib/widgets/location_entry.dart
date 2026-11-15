import 'package:flutter/material.dart';

import '../chat/chat_device.dart';
import 'ui.dart';

/// Asks the person to type or paste where they are, for a device that cannot read its own position (a computer with no
/// GPS, a browser that blocked location). Accepts "-1.286389, 36.817223" or a Google Maps link. Returns null if cancelled.
Future<GeoPoint?> askForCoordinates(BuildContext context, {String? reason}) {
  return showDialog<GeoPoint>(context: context, builder: (_) => _CoordinatesDialog(reason: reason));
}

class _CoordinatesDialog extends StatefulWidget {
  const _CoordinatesDialog({this.reason});

  final String? reason;

  @override
  State<_CoordinatesDialog> createState() => _CoordinatesDialogState();
}

class _CoordinatesDialogState extends State<_CoordinatesDialog> {
  final _text = TextEditingController();
  String? _problem;

  @override
  void dispose() {
    _text.dispose();
    super.dispose();
  }

  void _use() {
    final point = parseCoordinates(_text.text);
    if (point == null) {
      setState(() => _problem = 'Type the latitude and longitude, like -1.286389, 36.817223, or paste a Google Maps link.');
      return;
    }
    Navigator.of(context).pop(point);
  }

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    return AppDialog(
      title: const Text('Where are you?'),
      content: SingleChildScrollView(
        child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
          if (widget.reason != null) ...[Text(widget.reason!, style: text.bodySmall), const SizedBox(height: 12)],
          const Text('Open Google Maps, press and hold the place, and copy the numbers that appear. Or paste the link.'),
          const SizedBox(height: 12),
          TextField(
            controller: _text,
            autofocus: true,
            keyboardType: TextInputType.text,
            decoration: InputDecoration(labelText: 'Latitude, longitude or map link', errorText: _problem),
            onChanged: (_) {
              if (_problem != null) setState(() => _problem = null);
            },
            onSubmitted: (_) => _use(),
          ),
        ]),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.of(context).pop(), child: const Text('Cancel')),
        TextButton(onPressed: _use, child: const Text('Use this place')),
      ],
    );
  }
}
