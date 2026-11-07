import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sqflite/sqflite.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart' as ffi;
import 'package:path/path.dart';
import 'package:zhixing_compass/core/store.dart';
import 'package:zhixing_compass/ai_chat_v051.dart';
import 'package:zhixing_compass/main.dart';
import 'package:zhixing_compass/screens.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll((){
    ffi.sqfliteFfiInit();
    databaseFactory=ffi.databaseFactoryFfi;
    FlutterSecureStorage.setMockInitialValues({});
  });
  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    await deleteDatabase(join(await getDatabasesPath(),'zhixing_compass_v3.db'));
  });
  tearDown(() async {
    await Future<void>.delayed(const Duration(milliseconds:35));
    await deleteDatabase(join(await getDatabasesPath(),'zhixing_compass_v3.db'));
  });

  test('AI task IDs persist after restart; deleted references are not restored as body',
    () async {
    final store=await CompassStore.open();
    final source=await store.add(kind:'source',title:'我的课程材料',
      body:'一段普通公开知识内容');
    final thread=await store.chats.createThread(title:'资料讨论');
    await store.chats.setContext(thread.id,[source.id,source.id]);
    expect(await store.chats.getContext(thread.id),[source.id]);
    await store.closeForTests();
    final reopened=await CompassStore.open();
    expect(await reopened.chats.getContext(thread.id),[source.id]);
    expect(reopened.byId(source.id)?.body,'一段普通公开知识内容');
    await reopened.remove(reopened.byId(source.id)!);
    expect(await reopened.chats.getContext(thread.id),[source.id]);
    expect(reopened.byId(source.id),isNull);
    await reopened.chats.deleteThread(thread.id);
    expect(await reopened.chats.getContext(thread.id),isEmpty);
    await reopened.closeForTests();
  });

  testWidgets('source-specific chat clearly displays its selected ordinary context',
    (tester) async {
    late CompassStore store;
    late CompassEntry source;
    await tester.runAsync(() async {
      store=await CompassStore.open();
      source=await store.add(kind:'source',title:'论证方法原文',
        body:'区分事实和意见的材料');
    });
    await tester.pumpWidget(CompassScope(store:store,child:MaterialApp(
      home:AiChatPage(mode:'ask',selectedEvidence:[source],
        restrictEvidenceToSelection:true,
        initialPrompt:'有哪些反例？'))));
    await tester.pumpAndSettle();
    expect(find.text('本次 AI 任务 · 指定资料范围'),findsOneWidget);
    expect(find.text('论证方法原文'),findsOneWidget);
    expect(find.text('有哪些反例？'),findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.runAsync(()=>store.closeForTests());
  });

  testWidgets('private journal never appears in selected AI context',
    (tester) async {
    late CompassStore store;
    late CompassEntry secret;
    await tester.runAsync(() async {
      store=await CompassStore.open();
      secret=await store.add(kind:'journal',title:'私密日记标题',
        body:'秘密不能被发送');
    });
    await tester.pumpWidget(CompassScope(store:store,child:MaterialApp(
      home:AiChatPage(mode:'ask',selectedEvidence:[secret],
        restrictEvidenceToSelection:true))));
    await tester.pumpAndSettle();
    expect(find.text('本次 AI 任务 · 指定资料范围'),findsOneWidget);
    expect(find.text('原资料已删除或没有正文，将只发送你输入的问题。'),findsOneWidget);
    expect(find.text('私密日记标题'),findsNothing);
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.runAsync(()=>store.closeForTests());
  });
}
