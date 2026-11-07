import 'dart:typed_data';
import 'package:archive/archive.dart';
import 'core/store.dart';
import 'diary_book_v085.dart';

/// Standalone, offline OOXML DOCX export. No AI or network requests.
/// Export files are readable, NOT encrypted private backups.
class DiaryBookDocxService {
  // XML 1.0 disallows most control codes even when escaped.
  static String escapeXml(String value) => String.fromCharCodes(value.runes.where(
    (r) => r == 0x9 || r == 0xA || r == 0xD ||
      (r >= 0x20 && r <= 0xD7FF) ||
      (r >= 0xE000 && r <= 0xFFFD) ||
      (r >= 0x10000 && r <= 0x10FFFF)))
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&apos;');

  static String paragraph(String text, {String? style, bool pageBreak = false}) {
    final ppr = style == null ? '' :
        '<w:pPr><w:pStyle w:val="$style"/></w:pPr>';
    final parts = text.split('\n').map((line) =>
      '<w:r><w:t xml:space="preserve">${escapeXml(line)}</w:t></w:r>')
      .join('<w:r><w:br/></w:r>');
    return '<w:p>$ppr${pageBreak ? '<w:r><w:br w:type="page"/></w:r>' : ''}$parts</w:p>';
  }

  static Future<Uint8List> build({
    required CompassStore store,
    required List<CompassEntry> entries,
    required String title,
    String subtitle = '',
    String preface = '',
  }) async {
    final ordered = DiaryBookService.sortEntries(entries);
    final name = title.trim();
    if (name.isEmpty || name.runes.length > 80) {
      throw const FormatException('书名应为 1–80 个字符');
    }
    if (subtitle.runes.length > 120 || preface.runes.length > 3000) {
      throw const FormatException('副标题或序言过长');
    }
    final body = StringBuffer();
    body.write(paragraph(name, style: 'Title'));
    if (subtitle.trim().isNotEmpty) {
      body.write(paragraph(subtitle.trim(), style: 'Subtitle'));
    }
    body.write(paragraph('我的私人日记集'));
    body.write(paragraph(
      '${DiaryBookService.formatDate(ordered.first.createdAt)} — '
      '${DiaryBookService.formatDate(ordered.last.createdAt)}'));
    body.write(paragraph('共 ${ordered.length} 篇日记'));
    body.write(paragraph('目录', style: 'Heading1', pageBreak: true));
    String? tocMonth;
    for (final entry in ordered) {
      final month = DiaryBookService.monthKey(entry.createdAt);
      if (tocMonth != month) {
        tocMonth = month;
        body.write(paragraph(month, style: 'Heading2'));
      }
      body.write(paragraph(
        '${entry.createdAt.day.toString().padLeft(2, '0')} 日 · ${entry.title}'));
    }
    if (preface.trim().isNotEmpty) {
      body.write(paragraph('序言', style: 'Heading1', pageBreak: true));
      for (final para in preface.split('\n')) {
        body.write(paragraph(para));
      }
    }
    String? currentMonth;
    var totalChars = 0;
    for (final entry in ordered) {
      final raw = await store.privateJournalBody(entry.id);
      totalChars += raw.runes.length;
      if (totalChars > DiaryBookService.maxCharacters) {
        throw const FormatException('内容过长，建议分册导出');
      }
      final month = DiaryBookService.monthKey(entry.createdAt);
      if (month != currentMonth) {
        currentMonth = month;
        body.write(paragraph(month, style: 'Heading1', pageBreak: true));
      }
      body.write(paragraph(entry.title, style: 'Heading2'));
      body.write(paragraph(DiaryBookService.formatDate(entry.createdAt)));
      for (final para in raw.split('\n')) {
        body.write(paragraph(para));
      }
    }
    const ns = 'http://schemas.openxmlformats.org/wordprocessingml/2006/main';
    final document = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
      '<w:document xmlns:w="$ns"><w:body>${body.toString()}'
      '<w:sectPr><w:pgSz w:w="8391" w:h="11906"/>'
      '<w:pgMar w:top="1080" w:right="900" w:bottom="1080" w:left="900"/>'
      '</w:sectPr></w:body></w:document>';
    final styles = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
      '<w:styles xmlns:w="$ns">'
      '<w:style w:type="paragraph" w:default="1" w:styleId="Normal">'
      '<w:name w:val="Normal"/><w:rPr><w:rFonts w:eastAsia="Microsoft YaHei"/>'
      '<w:sz w:val="22"/></w:rPr></w:style>'
      '<w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/>'
      '<w:rPr><w:b/><w:sz w:val="42"/></w:rPr></w:style>'
      '<w:style w:type="paragraph" w:styleId="Subtitle"><w:name w:val="Subtitle"/>'
      '<w:rPr><w:sz w:val="26"/></w:rPr></w:style>'
      '<w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/>'
      '<w:rPr><w:b/><w:sz w:val="32"/></w:rPr></w:style>'
      '<w:style w:type="paragraph" w:styleId="Heading2"><w:name w:val="heading 2"/>'
      '<w:rPr><w:b/><w:sz w:val="26"/></w:rPr></w:style>'
      '</w:styles>';
    final archive = Archive();
    void add(String path, String value) =>
      archive.addFile(ArchiveFile.string(path, value));
    add('[Content_Types].xml', '<?xml version="1.0" encoding="UTF-8"?>'
      '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
      '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
      '<Default Extension="xml" ContentType="application/xml"/>'
      '<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>'
      '<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>'
      '</Types>');
    add('_rels/.rels', '<?xml version="1.0" encoding="UTF-8"?>'
      '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
      '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>'
      '</Relationships>');
    add('word/_rels/document.xml.rels', '<?xml version="1.0" encoding="UTF-8"?>'
      '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
      '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>'
      '</Relationships>');
    add('word/document.xml', document);
    add('word/styles.xml', styles);
    return ZipEncoder().encodeBytes(archive);
  }
}
