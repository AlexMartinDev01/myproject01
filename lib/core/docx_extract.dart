import 'dart:convert';
import 'dart:io';
import 'package:archive/archive.dart';
import 'package:flutter/foundation.dart';
import 'package:xml/xml.dart';

/// Extracts text from a DOCX package locally. It includes paragraphs, tables,
/// headers, footers, footnotes and endnotes while preserving document order
/// within each OOXML part. Private files are never uploaded.
Future<String> extractDocxText(File file) async {
  final text=await compute(parseDocxBytes,await file.readAsBytes());
  if(text.trim().isEmpty) {
    throw const FormatException('Word 文件中没有识别到正文文字。'
      '请确认它是 .docx 格式；旧版 .doc 需要先另存为 .docx。');
  }
  return text;
}

String _extractPart(Archive archive,String name) {
  final item=archive.findFile(name);
  if(item==null)return '';
  if(item.size>60*1024*1024) {
    throw FormatException('Word 文档部件解压后过大：$name');
  }
  final root=XmlDocument.parse(utf8.decode(item.content,allowMalformed:true));
  final lines=<String>[];
  // Read every paragraph in XML order, including paragraphs nested in tables.
  for(final paragraph in root.findAllElements('w:p')) {
    final buffer=StringBuffer();
    for(final node in paragraph.descendants) {
      if(node is XmlElement) {
        if(node.name.local=='t') buffer.write(node.innerText);
        if(node.name.local=='tab') buffer.write('\t');
        if(node.name.local=='br' || node.name.local=='cr') buffer.write('\n');
      }
    }
    final line=buffer.toString().trimRight();
    if(line.trim().isNotEmpty)lines.add(line);
  }
  return lines.join('\n');
}

String parseDocxBytes(Uint8List data) {
  if(data.isEmpty)throw const FormatException('Word 文件为空');
  final zip=ZipDecoder().decodeBytes(data,verify:true);
  final main=zip.findFile('word/document.xml');
  if(main==null)throw const FormatException('文件不是有效的 DOCX（缺少 document.xml）');
  var inflated=0;
  for(final file in zip.files.where((f)=>f.isFile)) {
    inflated+=file.size;
    if(inflated>120*1024*1024) {
      throw const FormatException('Word 解压后的内容超过 120 MB，已停止解析');
    }
  }
  final sections=<String>[];
  final body=_extractPart(zip,'word/document.xml');
  if(body.trim().isNotEmpty)sections.add(body);
  for(final name in [
    'word/footnotes.xml','word/endnotes.xml',
    'word/header1.xml','word/header2.xml','word/header3.xml',
    'word/footer1.xml','word/footer2.xml','word/footer3.xml',
  ]) {
    final value=_extractPart(zip,name);
    if(value.trim().isNotEmpty)sections.add(value);
  }
  final text=sections.join('\n\n');
  if(text.trim().isEmpty)throw const FormatException('Word 中未找到可导入的文字');
  return text;
}
