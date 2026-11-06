import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sqflite/sqflite.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart' as ffi;
import 'package:path/path.dart';
import 'package:zhixing_compass/main.dart';
import 'package:zhixing_compass/screens.dart';
import 'package:zhixing_compass/core/store.dart';

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

  testWidgets('home makes journaling and ordinary AI conversation discoverable',
      (tester) async {
    late CompassStore store;
    await tester.runAsync(() async { store = await CompassStore.open(); });
    await tester.pumpWidget(CompassScope(store: store,
      child: const MaterialApp(home: TodayPage())));
    expect(find.text('今天想做什么？'), findsOneWidget);
    expect(find.text('随手记录'), findsOneWidget);
    expect(find.text('问问 AI'), findsOneWidget);
    expect(find.text('写日记 / 导入已有日记'), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.runAsync(() => store.closeForTests());
  });

  testWidgets('AI hub prioritizes everyday chat before advanced modes',
      (tester) async {
    await tester.pumpWidget(const MaterialApp(home: AiHubPage()));
    expect(find.text('开始普通 AI 对话'), findsOneWidget);
    expect(find.text('其他思考方式'), findsOneWidget);
    expect(find.text('历史对话'), findsOneWidget);
  });

  testWidgets('journal editor has clear import action and persistent save',
      (tester) async {
    late CompassStore store;
    await tester.runAsync(() async { store = await CompassStore.open(); });
    await tester.pumpWidget(CompassScope(store: store,
      child: const MaterialApp(home: JournalEditPage())));
    expect(find.text('保存本次内容'), findsOneWidget);
    expect(find.text('导入 Word / PDF 日记'), findsOneWidget);
    final scaffold = tester.widget<Scaffold>(find.byType(Scaffold).first);
    expect(scaffold.bottomNavigationBar, isNotNull);
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.runAsync(() => store.closeForTests());
  });
}
