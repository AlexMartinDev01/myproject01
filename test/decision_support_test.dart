import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sqflite/sqflite.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart' as ffi;
import 'package:path/path.dart';
import 'package:zhixing_compass/core/store.dart';
import 'package:zhixing_compass/core/decision_support.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() {
    ffi.sqfliteFfiInit();
    databaseFactory = ffi.databaseFactoryFfi;
    FlutterSecureStorage.setMockInitialValues({});
  });
  late String dbPath;
  late CompassStore store;

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    dbPath = join(await getDatabasesPath(), 'zhixing_compass_v3.db');
    await deleteDatabase(dbPath);
    store = await CompassStore.open();
  });

  tearDown(() async {
    await store.closeForTests();
    await Future<void>.delayed(const Duration(milliseconds: 40));
    await deleteDatabase(dbPath);
  });

  test('decision report persists selected source and verified location only', () async {
    final source = await store.add(kind: 'source', title: '职业规划笔记',
      body: '不同职业方案需要比较现金流与机会成本。');
    final decision = await store.add(kind: 'decision', title: '毕业方向',
      body: '预算有限，先做小实验', meta: {
        'goal': '控制风险',
        'options': ['自由职业', '就业'],
        'criteria': {'经济稳定': 5},
      });
    final chunks = await store.findKnowledge('现金流与机会成本');
    expect(chunks, isNotEmpty);
    final id = chunks.first.chunkId;
    final record = await saveDecisionReview(store, decision.id,
      '## 结论\n小成本验证。[$id]', 'deepseek-flash', [id]);
    expect(record.offeredChunkIds, [id]);
    expect(record.citedChunkIds, [id]);
    expect(record.unknownChunkIds, isEmpty);
    final saved = readDecisionReviews(store.byId(decision.id)!);
    expect(saved, hasLength(1));
    expect(saved.single.answer, contains('小成本验证'));
    expect(decisionPrompt(decision), contains('最强反方'));
    expect(decisionFollowUpPrompt(decision, record), contains('小成本验证'));
    await store.update(store.byId(decision.id)!.copyWith(
      meta: {...store.byId(decision.id)!.meta, 'prediction': '下周验证'}));
    expect(readDecisionReviews(store.byId(decision.id)!), hasLength(1));
    expect(store.byId(decision.id)!.meta['prediction'], '下周验证');
    expect(source.kind, 'source');
  });

  test('unrecognized or fabricated model citation is NOT accepted as evidence', () async {
    final actual = 'k_1234567890abcdef_1';
    final unknown = 'k_ffffffffffffffff_2';
    final audit = DecisionCitationAudit.inspect(
      '有一条原文 [$actual]，但这条引用是杜撰 [$unknown]。',
      [actual]);
    expect(audit.citedIds, [actual]);
    expect(audit.unknownIds, [unknown]);
    expect(audit.hasVerifiableLocations, true);
    expect(audit.containsUnrecognizedIds, true);
  });

  test('invalid chunk ID blocks save and never creates half-saved analysis', () async {
    final decision = await store.add(kind:'decision',title:'选择方向');
    await expectLater(saveDecisionReview(store,decision.id,
      '模型给出了一个没有来源的结论', 'deepseek-flash',
      ['k_ffffffffffffffff_7']), throwsStateError);
    expect(readDecisionReviews(store.byId(decision.id)!), isEmpty);
    await expectLater(saveDecisionReview(store,decision.id,
      '', 'deepseek-flash', []), throwsFormatException);
  });

  test('private diary never enters ordinary decision evidence', () async {
    await store.add(kind:'journal',title:'秘密',
      body:'夜间记下的个人隐私信息');
    final decision = await store.add(kind:'decision',title:'选择职业',
      meta: {'goal':'职业计划'});
    final matches = await store.findKnowledge('夜间记下的个人隐私信息');
    expect(matches, isEmpty);
    final record = await saveDecisionReview(store,decision.id,
      '个人知识库目前没有足够材料，无法下结论。', 'deepseek-flash', []);
    expect(record.offeredChunkIds, isEmpty);
  });

  test('history keeps up to 12 analysis revisions, not just last answer', () async {
    final decision = await store.add(kind:'decision',title:'生活计划');
    for (var n=0; n<13; n++) {
      await saveDecisionReview(store,decision.id,
        '第 $n 次分析', 'test-model', []);
    }
    final history = readDecisionReviews(store.byId(decision.id)!);
    expect(history.length, 12);
    expect(history.first.answer, '第 1 次分析');
    expect(history.last.answer, '第 12 次分析');
  });
}
