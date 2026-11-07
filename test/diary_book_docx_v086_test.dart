import 'package:flutter_test/flutter_test.dart';
import 'package:zhixing_compass/diary_book_docx_v086.dart';

void main() {
  test('Word export escapes XML metacharacters and preserves Unicode', () {
    expect(DiaryBookDocxService.escapeXml('日记 & <春天> "你好"'),
      '日记 &amp; &lt;春天&gt; &quot;你好&quot;');
  });
  test('Word paragraphs preserve line breaks and styles', () {
    final text = DiaryBookDocxService.paragraph('第一行\n第二行', style: 'Heading2');
    expect(text, contains('w:pStyle w:val="Heading2"'));
    expect(text, contains('<w:br/>'));
    expect(text, contains('第二行'));
  });
}
