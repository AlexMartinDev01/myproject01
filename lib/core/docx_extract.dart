import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';
import 'package:archive/archive.dart';
import 'package:flutter/foundation.dart';
import 'package:xml/xml.dart';

/// Extracts DOCX paragraphs locally; never uploads private Word files.
Future<String> extractDocxText(File file) async {
  final text = await compute(parseDocxBytes, await file.readAsBytes());
  if (text.trim().isEmpty) throw const FormatException('Word 中未找到可导入的文字');
  return text;
}

String parseDocxBytes(Uint8List data) {
  final zip = ZipDecoder().decodeBytes(data);
  final xml = zip.findFile('word/document.xml');
  if (xml == null) throw const FormatException('不是有效的 DOCX');
  final bytes = xml.content as List<int>;
  if (bytes.length > 60 * 1024 * 1024) throw const FormatException('文件解压后过大');
  final root = XmlDocument.parse(utf8.decode(bytes));
  final lines = <String>[];
  for (final p in root.findAllElements('w:p')) {
    final line = p.findAllElements('w:t').map((t) => t.innerText).join();
    if (line.trim().isNotEmpty) lines.add(line);
  }
  return lines.join('\n');
}
