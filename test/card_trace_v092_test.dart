import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sqflite/sqflite.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart' as ffi;
import 'package:path/path.dart';
import 'package:zhixing_compass/core/store.dart';
import 'package:zhixing_compass/core/card_trace_v092.dart';
import 'package:zhixing_compass/main.dart';
import 'package:zhixing_compass/screens.dart';
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
    await deleteDatabase(join(await getDatabasesPath(),
      'zhixing_compass_v3.db'));
  });
  tearDown(() async {
    await Future<void>.delayed(const Duration(milliseconds:35));
    await deleteDatabase(join(await getDatabasesPath(),
      'zhixing_compass_v3.db'));
  });

  test('reviewed card traces distinct real sources and never private journals',
    () async {
    final store=await CompassStore.open();
    final a=await store.add(kind:'source',title:'文章原文',
      body:'公开材料');
    final b=await store.add(kind:'source',title:'访谈原文',
      body:'公开访谈');
    final secret=await store.add(kind:'journal',title:'私密日记',
      body:'隐私文本');
    final card=await store.add(kind:'card',title:'知识',
      body:'自己的解释',meta:{
        'source_id':a.id,'ai_assisted':true,
        'ai_candidate_reviews':[{
          'review':'human_selected',
          'original_source_id':a.id,
          'offered_source_ids':[a.id,b.id,secret.id],
        }],
      });
    final trace=CardTrace.forCard(card,store.items);
    expect(trace.sources.map((e)=>e.id).toSet(),{a.id,b.id});
    expect(trace.aiAssisted,true);
    expect(trace.humanSelected,true);
    expect(trace.hasMissingSources,false);
    final graph=KnowledgeGraphSnapshot.build(store.items);
    expect(graph.edges.where((e)=>e.isSource).length,2);
    expect(graph.nodes.any((n)=>n.id==secret.id),false);
    await store.closeForTests();
  });

  test('incoming and outgoing links visible with explicit relation labels',
    () async {
    final store=await CompassStore.open();
    final a=await store.add(kind:'card',title:'核心知识',body:'内容');
    final b=await store.add(kind:'card',title:'支持证据',body:'内容');
    final c=await store.add(kind:'card',title:'反对观点',body:'内容');
    await store.update(b.copyWith(meta:{
      'related_card_ids':[a.id],
      'related_card_types':{a.id:'支持'},
    }));
    await store.update(a.copyWith(meta:{
      'related_card_ids':[c.id],
      'related_card_types':{c.id:'反例'},
    }));
    final trace=CardTrace.forCard(store.byId(a.id)!,store.items);
    expect(trace.relatedCards.length,2);
    expect(trace.relatedCards.any((r)=>r.card.id==b.id &&
      r.type=='支持' && r.direction=='被其他卡片关联'),true);
    expect(trace.relatedCards.any((r)=>r.card.id==c.id &&
      r.type=='反例' && r.direction=='主动关联'),true);
    await store.closeForTests();
  });

  test('deleted sources are reported without showing fake evidence',
    () async {
    final store=await CompassStore.open();
    final source=await store.add(kind:'source',title:'已删除材料',body:'正文');
    final card=await store.add(kind:'card',title:'保留下来的知识',
      body:'理解',meta:{'source_id':source.id});
    await store.remove(source);
    final trace=CardTrace.forCard(card,store.items);
    expect(trace.sources,isEmpty);
    expect(trace.hasMissingSources,true);
    await store.closeForTests();
  });

  testWidgets('knowledge detail displays provenance and authored relationships',
    (tester) async {
    late CompassStore store;
    late CompassEntry card;
    await tester.runAsync(() async {
      store=await CompassStore.open();
      final source=await store.add(kind:'source',title:'可回查的原文',
        body:'原始段落');
      final another=await store.add(kind:'card',title:'对照知识卡',
        body:'不同角度');
      card=await store.add(kind:'card',title:'知识摘要',body:'提炼结果',
        meta:{
          'source_id':source.id,
          'related_card_ids':[another.id],
          'related_card_types':{another.id:'对比'},
        });
    });
    await tester.pumpWidget(CompassScope(store:store,
      child:MaterialApp(home:KnowledgeDetailPage(id:card.id))));
    expect(find.text('知识来源 · 可以追溯'),findsOneWidget);
    await tester.scrollUntilVisible(find.text('可回查的原文'),200,
      scrollable:find.byType(Scrollable).first);
    expect(find.text('可回查的原文'),findsOneWidget);
    await tester.scrollUntilVisible(find.text('对照知识卡'),200,
      scrollable:find.byType(Scrollable).first);
    expect(find.text('对照知识卡'),findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.runAsync(()=>store.closeForTests());
  });
}
