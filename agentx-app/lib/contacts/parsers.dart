import 'dart:convert';

import 'contact_models.dart';

/// Reads a .vcf file (vCard 2.1, 3.0 or 4.0). People without a phone number are left out.
List<ContactEntry> parseVcf(String text) {
  final lines = text
      .replaceAll('\r\n', '\n')
      .replaceAll('\r', '\n')
      .replaceAll('=\n', '')
      .replaceAll(RegExp(r'\n[ \t]'), '')
      .split('\n');
  final out = <ContactEntry>[];
  String? name;
  String? alt;
  List<ContactPhone>? phones;

  String decode(String value, String head) {
    if (RegExp(r'ENCODING=QUOTED-PRINTABLE', caseSensitive: false).hasMatch(head)) {
      final bytes = <int>[];
      var last = 0;
      for (final m in RegExp(r'=([0-9A-Fa-f]{2})').allMatches(value)) {
        bytes
          ..addAll(utf8.encode(value.substring(last, m.start)))
          ..add(int.parse(m.group(1)!, radix: 16));
        last = m.end;
      }
      bytes.addAll(utf8.encode(value.substring(last)));
      return utf8.decode(bytes, allowMalformed: true);
    }
    return value.replaceAll(r'\,', ',').replaceAll(r'\;', ';').replaceAll(RegExp(r'\\n', caseSensitive: false), ' ');
  }

  for (final line in lines) {
    final i = line.indexOf(':');
    if (i < 0) continue;
    final head = line.substring(0, i);
    final value = line.substring(i + 1);
    final prop = head.split(';').first.replaceFirst(RegExp(r'^[\w-]+\.'), '').toUpperCase();
    if (prop == 'BEGIN') {
      name = '';
      alt = '';
      phones = [];
    } else if (prop == 'END') {
      if (phones != null && phones.isNotEmpty) {
        out.add(ContactEntry((name ?? '').isNotEmpty ? name! : ((alt ?? '').isNotEmpty ? alt! : '(no name)'), phones));
      }
      phones = null;
    } else if (phones != null) {
      if (prop == 'FN') {
        name = decode(value, head).trim();
      } else if (prop == 'N') {
        final p = decode(value, head).split(';');
        String at(int k) => k < p.length ? p[k] : '';
        alt = [at(1), at(2), at(0)].where((x) => x.isNotEmpty).join(' ').trim();
      } else if (prop == 'TEL') {
        final label = RegExp(r'TYPE=([^;:]+)', caseSensitive: false).firstMatch(head)?.group(1) ??
            RegExp(r';(CELL|HOME|WORK|VOICE)\b', caseSensitive: false).firstMatch(head)?.group(1) ??
            '';
        final n = value.replaceFirst(RegExp(r'^tel:', caseSensitive: false), '').trim();
        if (looksLikeNumber(n)) phones.add(ContactPhone(n, label.toLowerCase()));
      }
    }
  }
  return out;
}

/// Splits CSV text into rows, handling quoted cells with commas, quotes and line breaks inside.
List<List<String>> parseCsvRows(String text) {
  final rows = <List<String>>[];
  var row = <String>[];
  final cell = StringBuffer();
  var quoted = false;
  final s = text.startsWith('﻿') ? text.substring(1) : text;

  void endRow() {
    row.add(cell.toString());
    cell.clear();
    if (row.any((x) => x.trim().isNotEmpty)) rows.add(row);
    row = <String>[];
  }

  for (var i = 0; i < s.length; i++) {
    final c = s[i];
    if (quoted) {
      if (c == '"') {
        if (i + 1 < s.length && s[i + 1] == '"') {
          cell.write('"');
          i++;
        } else {
          quoted = false;
        }
      } else {
        cell.write(c);
      }
    } else if (c == '"') {
      quoted = true;
    } else if (c == ',') {
      row.add(cell.toString());
      cell.clear();
    } else if (c == '\n' || c == '\r') {
      if (c == '\r' && i + 1 < s.length && s[i + 1] == '\n') i++;
      endRow();
    } else {
      cell.write(c);
    }
  }
  if (cell.isNotEmpty || row.isNotEmpty) endRow();
  return rows;
}

/// Reads a contacts CSV from Google Contacts, Outlook, a phone or a CRM: a header row, a name or a first and
/// last name, and one or more phone columns. People without a phone number are left out.
List<ContactEntry> parseCsv(String text) {
  final rows = parseCsvRows(text);
  if (rows.length < 2) return const [];
  final head = rows.first.map((h) => h.trim().toLowerCase()).toList();
  List<int> idx(RegExp re) => [for (var i = 0; i < head.length; i++) if (re.hasMatch(head[i])) i];
  final nameCols = idx(RegExp(r'^(name|display name|full name|file as)$'));
  final parts = [
    idx(RegExp(r'^(first name|given name)$')),
    idx(RegExp(r'^(middle name|additional name)$')),
    idx(RegExp(r'^(last name|family name|surname)$')),
  ].where((l) => l.isNotEmpty).map((l) => l.first).toList();
  final phoneCols = [
    for (var i = 0; i < head.length; i++)
      if (RegExp(r'(phone|mobile|tel|cell)').hasMatch(head[i]) && !RegExp(r'(label|type|country|code)\b').hasMatch(head[i])) i
  ];
  String cellAt(List<String> r, int i) => i < r.length ? r[i].trim() : '';

  final out = <ContactEntry>[];
  for (final r in rows.skip(1)) {
    var name = '';
    for (final i in nameCols) {
      if (cellAt(r, i).isNotEmpty) {
        name = cellAt(r, i);
        break;
      }
    }
    if (name.isEmpty) name = parts.map((i) => cellAt(r, i)).where((x) => x.isNotEmpty).join(' ');
    final phones = <ContactPhone>[];
    for (final i in phoneCols) {
      for (final n in cellAt(r, i).split(RegExp(r'\s*:::\s*'))) {
        if (looksLikeNumber(n)) {
          final label = head[i].replaceFirst(RegExp(r'\s*-?\s*value$'), '').replaceFirst(RegExp(r'phone\s*\d*\s*'), '').trim();
          phones.add(ContactPhone(n.trim(), label));
        }
      }
    }
    if (phones.isNotEmpty) out.add(ContactEntry(name.isEmpty ? '(no name)' : name, phones));
  }
  return out;
}

/// Picks the right parser for a file: vCard when it says so, CSV otherwise.
List<ContactEntry> parseContactsFile(String fileName, String text) {
  final isVcf = RegExp(r'\.(vcf|vcard)$', caseSensitive: false).hasMatch(fileName) || text.contains('BEGIN:VCARD');
  return isVcf ? parseVcf(text) : parseCsv(text);
}
