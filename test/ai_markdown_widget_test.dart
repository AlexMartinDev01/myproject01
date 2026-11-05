import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:zhixing_compass/ai_chat_v051.dart';

void main() {
  testWidgets('Markdown renders headings and bullet points', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: Scaffold(
      body: SingleChildScrollView(child: MessageMarkdown(data: '''
# 核心结论

回答需要易读。

## 依据

- 第一条
- 第二条
''')))));
    expect(find.text('核心结论'), findsWidgets);
    expect(find.text('依据'), findsWidgets);
    expect(find.textContaining('第一条'), findsWidgets);
    expect(tester.takeException(), isNull);
  });
}
