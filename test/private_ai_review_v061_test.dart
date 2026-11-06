import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sqflite/sqflite.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart' as ffi;
import 'package:path/path.dart';
import 'package:zhixing_compass/main.dart';
import 'package:zhixing_compass/core/store.dart';
import 'package:zhixing_compass/private_ai_review_v061.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() {
    ffi.sqfliteFfiInit();
    databaseFactory = ffi.databaseFactoryFfi;
    FlutterSecureStorage.setMockInitialValues({});
  });
  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    await deleteDatabase(join(await getDatabasesPath(), 'zhixing_compass_v3.db'));
  });
  tearDown(() async {
    await deleteDatabase(join(await getDatabasesPath(), 'zhixing_compass_v3.db'));
  });

  testWidgets('private review never transmits or asks for consent on initial render',
      (tester) async {
    late CompassStore store;
    await tester.runAsync(() async {store = await CompassStore.open();});
    final start = DateTime(2026, 11, 1);
    final end = DateTime(2026, 12, 1);
    await tester.pumpWidget(CompassScope(store: store,
      child: MaterialApp(home: PrivateAiReviewPage(
        start: start, end: end, periodLabel: '2026年11月'))));
    expect(find.text('第一步 · 选择日记'), findsOneWidget);
    expect(find.text('生成待发送内容预览'), findsOneWidget);
    expect(find.text('确认后生成 AI 回顾'), findsNothing);
    expect(find.byType(CheckboxListTile), findsNothing);
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.runAsync(() => store.closeForTests());
  });

  testWidgets('selected diary only reveals preview after explicit button',
      (tester) async {
    late CompassStore store;
    await tester.runAsync(() async {
      store = await CompassStore.open();
      await store.add(kind: 'journal', title: '隐私记录测试',
        body: '这段内容默认不显示', status: '私密');
    });
    final now = DateTime.now();
    await tester.pumpWidget(CompassScope(store: store,
      child: MaterialApp(home: PrivateAiReviewPage(
        start: DateTime(now.year, now.month, 1),
        end: DateTime(now.year, now.month + 1, 1), periodLabel: '本月'))));
    expect(find.text('隐私记录测试'), findsOneWidget);
    expect(find.textContaining('这段内容默认不显示'), findsNothing);
    expect(find.text('确认后生成 AI 回顾'), findsNothing);
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.runAsync(() => store.closeForTests());
  });
}
