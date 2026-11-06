import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sqflite/sqflite.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart' as ffi;
import 'package:path/path.dart';
import 'package:zhixing_compass/core/store.dart';
import 'package:zhixing_compass/main.dart';
import 'package:zhixing_compass/knowledge_graph_v07.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() {
    ffi.sqfliteFfiInit();
    databaseFactory=ffi.databaseFactoryFfi;
    FlutterSecureStorage.setMockInitialValues({});
  });
  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    await deleteDatabase(join(await getDatabasesPath(), 'zhixing_compass_v3.db'));
  });
  tearDown(() async {
    await deleteDatabase(join(await getDatabasesPath(), 'zhixing_compass_v3.db'));
  });

  test('graph draws only authored links and verified source references', () async {
    final store=await CompassStore.open();
    final source=await store.add(kind:'source',title:'原始资料',
      body:'公开资料',status:'待处理');
    final cardA=await store.add(kind:'card',title:'关键概念A',
      body:'解释A',meta:{'topic':'科技','source_id':source.id});
    final cardB=await store.add(kind:'card',title:'关键概念B',
      body:'解释B',meta:{'topic':'科技'});
    final journal=await store.add(kind:'journal',title:'秘密',
      body:'只存本机的私密内容',status:'私密');
    await store.update(cardA.copyWith(meta:{
      ...cardA.meta,'related_card_ids':[cardB.id,cardB.id,'nonexistent',journal.id],
    }));
    final graph=KnowledgeGraphSnapshot.build(store.items);
    expect(graph.nodes.where((n)=>!n.isSource).length,2);
    expect(graph.nodes.where((n)=>n.isSource).length,1);
    expect(graph.nodes.any((n)=>n.id==journal.id),isFalse);
    expect(graph.nodes.any((n)=>n.id=='nonexistent'),isFalse);
    expect(graph.edges.where((e)=>!e.isSource).length,1);
    expect(graph.edges.where((e)=>e.isSource).length,1);
    expect(graph.edges.any((e)=>e.to==journal.id),isFalse);
    await store.closeForTests();
  });

  test('stored graph relations survive store reload and respect topic filters', () async {
    final store=await CompassStore.open();
    final a=await store.add(kind:'card',title:'过滤甲',body:'正文甲',
      meta:{'topic':'科技'});
    final b=await store.add(kind:'card',title:'过滤乙',body:'正文乙',
      meta:{'topic':'成长'});
    await store.update(a.copyWith(meta:{...a.meta,'related_card_ids':[b.id]}));
    await store.reload();
    expect(KnowledgeGraphSnapshot.relations(store.byId(a.id)!),contains(b.id));
    final subset=KnowledgeGraphSnapshot.build(store.items,topic:'科技');
    expect(subset.nodes.length,1);
    expect(subset.edges,isEmpty);
    await store.closeForTests();
  });

  test('typed relations remain explicit and survive database reload', () async {
    final store=await CompassStore.open();
    final a=await store.add(kind:'card',title:'方法',body:'一种方法',
      meta:{'topic':'科技'});
    final b=await store.add(kind:'card',title:'反例',body:'明确的反例',
      meta:{'topic':'科技'});
    await store.update(a.copyWith(meta:{
      ...a.meta,
      'related_card_ids':[b.id],
      'related_card_types':{b.id:'反例'},
    }));
    await store.reload();
    final updated=store.byId(a.id)!;
    expect(KnowledgeGraphSnapshot.relationType(updated,b.id),'反例');
    final graph=KnowledgeGraphSnapshot.build(store.items);
    expect(graph.edges.where((e)=>!e.isSource).single.relation,'反例');
    expect(KnowledgeGraphSnapshot.relationType(updated,'not-a-card'),'相关');
    expect(KnowledgeGraphSnapshot.relationTypes,containsAll(['相关','支持','反例','延伸','对比']));
    await store.closeForTests();
  });

  testWidgets('empty knowledge graph shows onboarding text without cloud dependency',
      (tester) async {
    late CompassStore store;
    await tester.runAsync(() async {store=await CompassStore.open();});
    await tester.pumpWidget(CompassScope(store:store,
      child:const MaterialApp(home:KnowledgeGraphPage())));
    expect(find.text('知识网络'),findsOneWidget);
    expect(find.textContaining('当前没有匹配的知识卡'),findsOneWidget);
    expect(find.byType(InteractiveViewer),findsNothing);
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.runAsync(() => store.closeForTests());
  });
}
