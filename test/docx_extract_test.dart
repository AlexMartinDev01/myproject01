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
  test('invalid files are not accepted as Word documents', () {
    expect(() => parseDocxBytes(Uint8List.fromList(utf8.encode('not a ZIP'))),
      throwsA(anything));
  });
}
