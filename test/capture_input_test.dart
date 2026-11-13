import 'package:flutter_test/flutter_test.dart';
import 'package:zhixing_compass/core/capture_input.dart';

void main() {
  test('keeps a plain note intact', () {
    final value = CaptureInput.fromText('今天想到一件事\n下一行');
    expect(value.title, '今天想到一件事');
    expect(value.body, '今天想到一件事\n下一行');
    expect(value.origin, isEmpty);
    expect(value.status, '待处理');
  });

  test('recognizes a shared link without inventing extracted content', () {
    final value = CaptureInput.fromText('https://example.org/watch?v=12');
    expect(value.origin, 'https://example.org/watch?v=12');
    expect(value.body, isEmpty);
    expect(value.status, '需补内容');
  });

  test('preserves shared explanatory text alongside source link', () {
    final value = CaptureInput.fromText('这个视频很有启发 https://example.org/watch?v=12');
    expect(value.origin, 'https://example.org/watch?v=12');
    expect(value.body, '这个视频很有启发');
    expect(value.title, '这个视频很有启发');
  });

  test('ignores punctuation after shared link', () {
    final value = CaptureInput.fromText('看看这个：https://example.org/abc。');
    expect(value.origin, 'https://example.org/abc');
  });

  test('rejects empty text', () {
    expect(() => CaptureInput.fromText('  '), throwsFormatException);
  });
}
