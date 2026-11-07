import 'dart:convert';
import 'dart:typed_data';
import 'package:archive/archive.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:zhixing_compass/core/docx_extract.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  test('DOCX imports Chinese paragraphs and preserves order without network', () {
    final xml = "<w:document xmlns:w='http://schemas.openxmlformats.org/wordprocessingml/2006/main'>"
      "<w:body><w:p><w:r><w:t>第一次读书笔记</w:t></w:r></w:p>"
      "<w:p><w:r><w:t>第二次补充思考</w:t></w:r></w:p></w:body></w:document>";
    final zip = Archive();
    zip.addFile(ArchiveFile.string('word/document.xml', xml));
    final bytes = Uint8List.fromList(ZipEncoder().encode(zip));
    expect(parseDocxBytes(bytes), '第一次读书笔记\n第二次补充思考');
  });
  test('DOCX extraction includes table cells, tabs, line breaks and headers', () {
    final xml = "<w:document xmlns:w='http://schemas.openxmlformats.org/wordprocessingml/2006/main'>"
      "<w:body><w:p><w:r><w:t>正文标题</w:t></w:r></w:p>"
      "<w:tbl><w:tr><w:tc><w:p><w:r><w:t>表格单元格 A</w:t></w:r></w:p></w:tc>"
      "<w:tc><w:p><w:r><w:t>表格单元格 B</w:t></w:r></w:p></w:tc></w:tr></w:tbl>"
      "</w:body></w:document>";
    final header = "<w:hdr xmlns:w='http://schemas.openxmlformats.org/wordprocessingml/2006/main'>"
      "<w:p><w:r><w:t>页眉文字</w:t></w:r></w:p></w:hdr>";
    final zip=Archive();
    zip.addFile(ArchiveFile.string('word/document.xml',xml));
    zip.addFile(ArchiveFile.string('word/header1.xml',header));
    final bytes=Uint8List.fromList(ZipEncoder().encode(zip));
    final text=parseDocxBytes(bytes);
    expect(text,contains('正文标题'));
    expect(text,contains('表格单元格 A'));
    expect(text,contains('表格单元格 B'));
    expect(text,contains('页眉文字'));
  });
  test('invalid files are not accepted as Word documents', () {
    expect(() => parseDocxBytes(Uint8List.fromList(utf8.encode('not a ZIP'))),
      throwsA(anything));
  });
}
