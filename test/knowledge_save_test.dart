import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sqflite/sqflite.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart' as ffi;
import 'package:path/path.dart';
import 'package:zhixing_compass/core/store.dart';
import 'package:zhixing_compass/core/knowledge_candidates.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUpAll(() {
    ffi.sqfliteFfiInit();
    databaseFactory = ffi.databaseFactoryFfi;
    FlutterSecureStorage.setMockInitialValues({});
  });
  late String path;
  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    path = join(await getDatabasesPath(), 'zhixing_compass_v3.db');
    await deleteDatabase(path);
  });
  tearDown(() async {
    await Future<void>.delayed(const Duration(milliseconds: 35));
    await deleteDatabase(path);
  });

  test('only explicitly selected drafts become cards with traceable provenance', () async {
    final s = await CompassStore.open();
    final source = await s.add(kind:'source', title:'我的阅读材料',
      body:'关于最小成本实验的章节');
    final ids = await s.saveKnowledgeCandidates([
      const KnowledgeSaveDraft(title:'用小实验验证选择',
        explanation:'先投入有限时间测试重要假设。',
        conditions:'可撤回时', counterexample:'时间敏感且不可逆时',
        application:'先验证一周', topic:'职业发展'),
      // Deliberately only a selected subset supplied to saveKnowledgeCandidates.
    ], conversationId:'chat-one', messageId:'message-one',
      offeredSourceIds:[source.id,'unknown-id'], originalSourceId:source.id);
    expect(ids, hasLength(1));
    final cards = s.ofKind('card');
    expect(cards, hasLength(1));
    expect(cards.single.body, '先投入有限时间测试重要假设。');
    expect(cards.single.body, isNot(contains('最小成本实验的章节')));
    expect(cards.single.meta['conditions'], '可撤回时');
    expect(cards.single.meta['source_id'], source.id);
    final reviews = cards.single.meta['ai_candidate_reviews'] as List;
    expect((reviews.single as Map)['message_id'], 'message-one');
    expect((reviews.single as Map)['source_claim'], 'not_verified');
    expect((reviews.single as Map)['offered_source_ids'], [source.id]);
    await s.closeForTests();
  });

  test('merging preserves old body and appends only user-confirmed material', () async {
    final s = await CompassStore.open();
    final old = await s.add(kind:'card', title:'预算与实验',
      body:'原始用户自己写的想法', status:'已掌握',
      meta:{'topic':'自我成长','counter':'旧反例'});
    final ids = await s.saveKnowledgeCandidates([
      KnowledgeSaveDraft(title:'低风险验证新方向',
        explanation:'先进行七天的小规模实验',
        counterexample:'需要立刻处理的紧急问题',
        mergeTargetId:old.id),
    ]);
    expect(ids, [old.id]);
    final merged = s.byId(old.id)!;
    expect(merged.body, contains('原始用户自己写的想法'));
    expect(merged.body, contains('低风险验证新方向'));
    expect(merged.meta['counter'], contains('旧反例'));
    expect(merged.meta['counter'], contains('紧急问题'));
    expect(merged.status, '待验证');
    expect(s.ofKind('card'), hasLength(1));
    await s.closeForTests();
  });

  test('atomic rollback prevents partial saving and duplicate exact titles', () async {
    final s = await CompassStore.open();
    await s.add(kind:'card', title:'已存在的标题', body:'原有正文');
    await expectLater(s.saveKnowledgeCandidates([
      const KnowledgeSaveDraft(title:'可以保存', explanation:'这条也应一起回滚'),
      const KnowledgeSaveDraft(title:'已存在的标题', explanation:'AI 重复生成'),
    ]), throwsStateError);
    expect(s.ofKind('card').map((e)=>e.title), ['已存在的标题']);
    await expectLater(s.saveKnowledgeCandidates([
      const KnowledgeSaveDraft(title:'标题', explanation:'新增观点',
        mergeTargetId:'nonexistent-card'),
    ]), throwsStateError);
    expect(s.ofKind('card'), hasLength(1));
    await s.closeForTests();
  });
}
