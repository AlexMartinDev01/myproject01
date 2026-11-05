import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:zhixing_compass/main.dart';

void main() {
  testWidgets('branding and reusable cards render', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: Scaffold(body: Column(children: [
      SectionHeading('知识库'), SoftCard(child: Text('可追溯的知识卡')),
    ]))));
    expect(find.text('知识库'), findsOneWidget);
    expect(find.text('可追溯的知识卡'), findsOneWidget);
  });
  testWidgets('navigation card responds to tap', (tester) async {
    var taps = 0;
    await tester.pumpWidget(MaterialApp(home: Scaffold(body:
      NavCard(icon: Icons.book, title: '开始学习', onTap: () => taps++))));
    await tester.tap(find.text('开始学习'));
    expect(taps, 1);
  });
}
