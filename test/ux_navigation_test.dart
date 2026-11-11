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
import 'package:zhixing_compass/core/knowledge_candidates.dart';
import 'package:zhixing_compass/core/review_draft_v091.dart';

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

  testWidgets('fused home makes recording, AI and reflection discoverable',
      (tester) async {
    late CompassStore store;
    await tester.runAsync(() async { store = await CompassStore.open(); });
    await tester.pumpWidget(CompassScope(store: store,
      child: const MaterialApp(home: TodayPage())));
    expect(find.text('今天有什么值得留下？'), findsOneWidget);
    expect(find.text('随手记录'), findsOneWidget);
    await tester.scrollUntilVisible(find.text('AI 对话'), 250,
      scrollable: find.byType(Scrollable).first);
    expect(find.text('AI 对话'), findsOneWidget);
    expect(find.text('生活复盘'), findsOneWidget);
    expect(find.text('辅助决策'), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.runAsync(() => store.closeForTests());
  });

  testWidgets('new user reads one clear promise and a single start action',
      (tester) async {
    late CompassStore store;
    await tester.runAsync(() async {store=await CompassStore.open();});
    await tester.pumpWidget(CompassScope(store:store,
      child:const MaterialApp(home:WelcomePage())));
    expect(find.text('把看到的内容，\\n变成真正属于自己的知识。'),
      findsOneWidget);
    expect(find.text('进入知行罗盘'),findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.runAsync(()=>store.closeForTests());
  });

  testWidgets('first-time start opens the fused home, not a forced capture form',
      (tester) async {
    late CompassStore store;
    await tester.runAsync(() async {store=await CompassStore.open();});
    await tester.pumpWidget(CompassApp(store:store));
    expect(find.text('进入知行罗盘'),findsOneWidget);
    await tester.ensureVisible(find.text('进入知行罗盘'));
    await tester.tap(find.text('进入知行罗盘'));
    await tester.pumpAndSettle();
    expect(store.onboarded, isTrue);
    expect(find.text('今天有什么值得留下？'), findsOneWidget);
    expect(find.text('随手记录'), findsOneWidget);
    expect(find.text('快速收集'), findsNothing);
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.runAsync(()=>store.closeForTests());
  });

  testWidgets('source detail offers AI and offline manual knowledge paths',
      (tester) async {
    late CompassStore store;
    late CompassEntry source;
    await tester.runAsync(() async {
      store=await CompassStore.open();
      source=await store.add(kind:'source',title:'测试来源',
        body:'一段可整理的材料',status:'待处理');
    });
    await tester.pumpWidget(CompassScope(store:store,
      child:MaterialApp(home:SourceDetailPage(id:source.id))));
    expect(find.text('下一步：把这条内容变成知识'),findsOneWidget);
    expect(find.text('提炼知识点 · 由我决定保存'),findsOneWidget);
    expect(find.text('不用 AI，自己写知识卡'),findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.runAsync(()=>store.closeForTests());
  });

  testWidgets('source page resumes a persisted human-selection draft',
      (tester) async {
    late CompassStore store;
    late CompassEntry source;
    await tester.runAsync(() async {
      store=await CompassStore.open();
      source=await store.add(kind:'source',
        title:'可继续的资料',body:'可提炼的正文');
      await store.saveKnowledgeReviewDraft(
        KnowledgeReviewDraft.fromCandidates(source.id,[
          const KnowledgeCandidate(title:'可复用的经验',
            explanation:'从经验中总结规则'),
        ]));
    });
    await tester.pumpWidget(CompassScope(store:store,
      child:MaterialApp(home:SourceDetailPage(id:source.id))));
    // SQLite futures run outside the widget fake clock.
    await tester.runAsync(() async {
      await Future<void>.delayed(const Duration(milliseconds: 120));
    });
    await tester.pumpAndSettle();
    expect(find.text('继续人工选择 · 1 条候选'),findsOneWidget);
    await tester.tap(find.text('继续人工选择 · 1 条候选'));
    await tester.pumpAndSettle();
    expect(find.text('提炼知识点 · 人工筛选'),findsOneWidget);
    expect(find.text('可复用的经验'),findsWidgets);
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.runAsync(()=>store.closeForTests());
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
    expect(find.text('更多'), findsOneWidget);
    await tester.tap(find.text('更多'));
    await tester.pumpAndSettle();
    expect(find.text('我的工作台'), findsOneWidget);
    expect(find.text('决策实验室'), findsOneWidget);
    expect(find.text('开始 AI 对话'), findsOneWidget);
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
    expect(find.text('继续之前的对话'), findsOneWidget);
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
  testWidgets('inbox search offers explicit reset without deleting entries',
      (tester) async {
    late CompassStore store;
    await tester.runAsync(() async {
      store = await CompassStore.open();
      await store.add(kind:'source',title:'在学校学编程',body:'算法笔记');
    });
    await tester.pumpWidget(CompassScope(store:store,
      child:const MaterialApp(home:InboxPage())));
    await tester.enterText(find.byType(TextField).first,'不存在的素材');
    await tester.pumpAndSettle();
    expect(find.text('没有找到相关素材'),findsOneWidget);
    await tester.tap(find.text('清空筛选'));
    await tester.pumpAndSettle();
    expect(find.text('在学校学编程'),findsOneWidget);
    expect(store.ofKind('source').length,1);
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.runAsync(()=>store.closeForTests());
  });

  testWidgets('knowledge topics are based on existing cards', (tester) async {
    late CompassStore store;
    await tester.runAsync(() async {
      store=await CompassStore.open();
      await store.add(kind:'card',title:'原创标签卡',body:'卡片内容',
        meta:{'topic':'交互设计'});
    });
    await tester.pumpWidget(CompassScope(store:store,
      child:const MaterialApp(home:KnowledgePage())));
    expect(find.text('交互设计'),findsOneWidget);
    expect(find.text('原创标签卡'),findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.runAsync(()=>store.closeForTests());
  });


}
