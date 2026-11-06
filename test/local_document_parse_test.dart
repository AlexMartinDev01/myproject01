import 'dart:convert';
import 'package:archive/archive.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:zhixing_compass/core/local_document_parse.dart';

void main() {
  test('EPUB extracts actual XHTML body and ignores scripts', () {
    final archive=Archive();
    archive.addFile(ArchiveFile.string('OEBPS/chapter01.xhtml',
      '<html><body><h1>第一章</h1><p>真实段落</p><script>恶意脚本</script></body></html>'));
    final bytes=ZipEncoder().encode(archive);
    final text=LocalDocumentParser.parseEpubBytes(bytes);
    expect(text,contains('真实段落'));
    expect(text,contains('第一章'));
    expect(text,isNot(contains('恶意脚本')));
  });
  test('garbled EPUB is rejected rather than generating fictional content', () {
    expect(() => LocalDocumentParser.parseEpubBytes(utf8.encode('not a zip')),
      throwsA(anything));
  });
}
