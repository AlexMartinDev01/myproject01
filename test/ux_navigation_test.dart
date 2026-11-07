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
    expect(find.text('四个常用入口'), findsOneWidget);
    expect(find.text('随手记录'), findsOneWidget);
    expect(find.text('问问 AI'), findsOneWidget);
    expect(find.text('我的日记'), findsOneWidget);
    expect(find.text('知识网络'), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.runAsync(() => store.closeForTests());
  });

  testWidgets('main navigation exposes journal and workspace as first-class tabs',
      (tester) async {
    late CompassStore store;
    await tester.runAsync(() async { store = await CompassStore.open(); });
    await tester.pumpWidget(CompassScope(store: store,
      child: const MaterialApp(home: HomeShell())));
    expect(find.byType(NavigationBar), findsOneWidget);
    expect(find.byType(NavigationDestination), findsNWidgets(5));
    expect(find.text('日记'), findsOneWidget);
    expect(find.text('工作台'), findsOneWidget);
    await tester.tap(find.text('工作台'));
    await tester.pumpAndSettle();
    expect(find.text('我的工作台'), findsOneWidget);
    expect(find.text('决策实验室'), findsOneWidget);
    expect(find.text('AI 思考'), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.runAsync(() => store.closeForTests());
  });

  testWidgets('empty inbox has actionable next step and filter reset', (tester) async {
    late CompassStore store;
    await tester.runAsync(() async {store = await CompassStore.open();});
    await tester.pumpWidget(CompassScope(store: store,
      child: const MaterialApp(home: InboxPage())));
    expect(find.text('收集箱还是空的'), findsOneWidget);
    expect(find.text('添加第一条素材'), findsOneWidget);
    await tester.tap(find.text('待处理'));
    await tester.pumpAndSettle();
    expect(find.text('没有“待处理”的素材'), findsOneWidget);
    await tester.tap(find.text('查看全部素材'));
    await tester.pumpAndSettle();
    expect(find.text('收集箱还是空的'), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.runAsync(() => store.closeForTests());
  });

  testWidgets('knowledge empty state clears search without changing database',
      (tester) async {
    late CompassStore store;
    await tester.runAsync(() async {
      store = await CompassStore.open();
      await store.add(kind: 'card', title: '测试知识卡', body: '一条本地内容');
    });
    await tester.pumpWidget(CompassScope(store: store,
      child: const MaterialApp(home: KnowledgePage())));
    final search = find.byType(TextField).first;
    await tester.enterText(search, '不存在的词');
    await tester.pumpAndSettle();
    expect(find.text('没有找到匹配的知识卡'), findsOneWidget);
    await tester.tap(find.text('清空筛选'));
    await tester.pumpAndSettle();
    expect(find.text('测试知识卡'), findsOneWidget);
    expect(store.ofKind('card').length, 1);
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

  testWidgets('journal list offers direct file import', (tester) async {
    late CompassStore store;
    await tester.runAsync(() async { store = await CompassStore.open(); });
    await tester.pumpWidget(CompassScope(store: store,
      child: const MaterialApp(home: JournalPage())));
    expect(find.text('写一篇新日记'), findsOneWidget);
    expect(find.text('日记成书'), findsOneWidget);
    expect(find.text('导入文件'), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.runAsync(() => store.closeForTests());
  });

  testWidgets('journal editor has clear import action and persistent save',
      (tester) async {
    late CompassStore store;
    await tester.runAsync(() async { store = await CompassStore.open(); });
    await tester.pumpWidget(CompassScope(store: store,
      child: const MaterialApp(home: JournalEditPage())));
    expect(find.text('保存本次内容'), findsOneWidget);
    expect(find.text('导入文档'), findsOneWidget);
    expect(find.text('添加照片'), findsOneWidget);
    expect(find.byType(TextField), findsNWidgets(2));
    final scaffold = tester.widget<Scaffold>(find.byType(Scaffold).first);
    expect(scaffold.bottomNavigationBar, isNotNull);
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.runAsync(() => store.closeForTests());
  });
}
