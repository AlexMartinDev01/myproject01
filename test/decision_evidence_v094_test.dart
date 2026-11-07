import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sqflite/sqflite.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart' as ffi;
import 'package:path/path.dart';
import 'package:zhixing_compass/core/store.dart';
import 'package:zhixing_compass/core/decision_evidence_v094.dart';
import 'package:zhixing_compass/core/decision_support.dart';
import 'package:zhixing_compass/decision_evidence_picker_v094.dart';
import 'package:zhixing_compass/main.dart';

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

  test('decision links save support and opposition, survive restart and keep cards intact',
    () async {
    final store=await CompassStore.open();
    final yes=await store.add(kind:'card',title:'支持经验',body:'可逆行动成本低');
    final no=await store.add(kind:'card',title:'反对依据',body:'资源不足');
    final decision=await store.add(kind:'decision',title:'要不要开始',
      body:'预算有限',meta:{'goal':'先验证','options':['开始','延后']});
    await saveDecisionEvidence(store,decision.id,[
      DecisionCardEvidence(yes.id,stance:'支持',note:'适合低成本试验'),
      DecisionCardEvidence(no.id,stance:'反对',note:'可能超预算'),
    ]);
    await store.closeForTests();
    final reopened=await CompassStore.open();
    final current=reopened.byId(decision.id)!;
    final links=decisionCardEvidence(current);
    expect(links,hasLength(2));
    expect(links.first.stance,'支持');
    expect(links.last.stance,'反对');
    expect(decisionEvidenceBrief(current,reopened),contains('支持经验'));
    expect(decisionEvidenceBrief(current,reopened),contains('反对观点'));
    expect(reopened.byId(yes.id)?.body,'可逆行动成本低');
    expect(reopened.byId(no.id)?.body,'资源不足');
    await reopened.closeForTests();
  });

  test('journal and missing cards rejected before metadata is modified',() async {
    final store=await CompassStore.open();
    final decision=await store.add(kind:'decision',title:'我的选择');
    final privateNote=await store.add(kind:'journal',title:'隐私',
      body:'不可公开的秘密');
    await expectLater(saveDecisionEvidence(store,decision.id,[
      DecisionCardEvidence(privateNote.id,stance:'支持'),
    ]),throwsFormatException);
    await expectLater(saveDecisionEvidence(store,decision.id,[
      const DecisionCardEvidence('does-not-exist',stance:'反对'),
    ]),throwsFormatException);
    expect(decisionCardEvidence(store.byId(decision.id)!),isEmpty);
    await store.closeForTests();
  });

  test('deleted cards no longer count as available evidence',() async {
    final store=await CompassStore.open();
    final card=await store.add(kind:'card',title:'旧卡片',body:'有用内容');
    final decision=await store.add(kind:'decision',title:'择业');
    await saveDecisionEvidence(store,decision.id,[
      DecisionCardEvidence(card.id,stance:'待核实'),
    ]);
    expect(availableDecisionCards(store,store.byId(decision.id)!),hasLength(1));
    await store.remove(card);
    expect(availableDecisionCards(store,store.byId(decision.id)!),isEmpty);
    await store.closeForTests();
  });

  test('analysis records offered card IDs and legacy report remains readable',
    () async {
    final store=await CompassStore.open();
    final card=await store.add(kind:'card',title:'个人经验',body:'参考经验');
    final decision=await store.add(kind:'decision',title:'两种方案');
    final report=await saveDecisionReview(store,decision.id,'方案比较结果',
      'test-model',const [],offeredCardIds:[card.id]);
    expect(report.offeredCardIds,[card.id]);
    expect(readDecisionReviews(store.byId(decision.id)!).single.offeredCardIds,
      [card.id]);
    final legacy=DecisionReviewRecord.fromJson({
      'id':'legacy','created_at':'2026-01-01T00:00:00Z',
      'answer':'旧版分析','model':'legacy','offered_chunk_ids':[],
      'cited_chunk_ids':[],'unknown_chunk_ids':[],
    });
    expect(legacy.offeredCardIds,isEmpty);
    await store.closeForTests();
  });

  testWidgets('card evidence picker shows the three explicit evidence stances',
    (tester) async {
    late CompassStore store;
    late CompassEntry decision;
    await tester.runAsync(() async {
      store=await CompassStore.open();
      await store.add(kind:'card',title:'一条知识卡',body:'可以作参考');
      decision=await store.add(kind:'decision',title:'选哪个方案');
    });
    await tester.pumpWidget(CompassScope(store:store,child:
      MaterialApp(home:DecisionEvidencePickerPage(decisionId:decision.id))));
    expect(find.text('选择知识卡证据'),findsOneWidget);
    expect(find.text('一条知识卡'),findsOneWidget);
    expect(find.text('保存决策依据'),findsOneWidget);
    await tester.tap(find.byType(CheckboxListTile).first);
    await tester.pumpAndSettle();
    expect(find.text('这张卡与当前决策的关系'),findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.runAsync(()=>store.closeForTests());
  });
}
