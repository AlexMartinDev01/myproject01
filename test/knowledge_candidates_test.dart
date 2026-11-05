import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
import 'package:zhixing_compass/core/knowledge_candidates.dart';

void main() {
  test('parses multiple independent candidates including optional fields', () {
    final payload = jsonEncode({'points': [
      {'title': '先做可逆决策', 'explanation': '通过小规模实践评估方案。',
       'conditions': '成本可控时', 'counterexample': '不可逆的紧急选择',
       'application': '做一周试验', 'topic': '职业发展'},
      {'title': '记录机会成本', 'explanation': '同时比较未选择方案的价值。',
       'conditions': '', 'counterexample': '', 'application': '',
       'topic': '自我成长'},
    ]});
    final items = parseKnowledgeCandidates(payload);
    expect(items, hasLength(2));
    expect(items[0].title, '先做可逆决策');
    expect(items[0].conditions, '成本可控时');
    expect(items[1].application, isEmpty);
  });

  test('accepts fenced JSON but never converts raw prose into one giant card', () {
    final fence = String.fromCharCodes([96, 96, 96]);
    final value = '$fence' + 'json\n' + jsonEncode({'points': [
      {'title': '原则一', 'explanation': '用自己的话总结。'},
    ]}) + '\n$fence';
    expect(parseKnowledgeCandidates(value), hasLength(1));
    expect(() => parseKnowledgeCandidates('## 核心结论\n全文应当直接保存'),
      throwsFormatException);
  });

  test('rejects invalid, missing, empty, too many or oversized candidates', () {
    expect(() => parseKnowledgeCandidates('{}'), throwsFormatException);
    expect(() => parseKnowledgeCandidates('{"points":[]}'), throwsFormatException);
    expect(() => parseKnowledgeCandidates(jsonEncode({'points': [
      {'title': '', 'explanation': '内容'}]})), throwsFormatException);
    expect(() => parseKnowledgeCandidates(jsonEncode({'points': [
      for (var i=0; i<9; i++) {'title': 'title$i', 'explanation':'有意义'}]})),
      throwsFormatException);
    expect(() => parseKnowledgeCandidates(jsonEncode({'points': [
      {'title': '标题', 'explanation': '重复' * 2500}]})), throwsFormatException);
  });

  test('deduplicates model repeat suggestions by title', () {
    final points = parseKnowledgeCandidates(jsonEncode({'points': [
      {'title': '先验证，再决策', 'explanation': 'A'},
      {'title': '先验证再决策', 'explanation': 'B'},
      {'title': '使用最小实验', 'explanation': 'C'},
    ]}));
    expect(points, hasLength(2));
  });

  test('local similarity identifies likely duplicate and ignores unrelated cards', () {
    expect(knowledgeTitleSimilarity('先验证，再做决策', '先验证再做决策'), 1.0);
    expect(knowledgeTitleSimilarity('最小化实验验证', '最小实验验证'), greaterThan(0.4));
    expect(knowledgeTitleSimilarity('预算管理', '天文观测'), lessThan(0.2));
  });

  test('invalid drafts cannot be saved', () {
    expect(() => const KnowledgeSaveDraft(title:'', explanation:'内容').validate(),
      throwsFormatException);
    expect(() => const KnowledgeSaveDraft(title:'标题', explanation:'').validate(),
      throwsFormatException);
  });

  test('prompt has hard input boundary and requires JSON not long-form duplication', () {
    final text = knowledgeExtractionPrompt('书' * 28000);
    expect(text.length, lessThan(8000));
    expect(text, contains('"points"'));
    expect(text, contains('【原始内容开始】'));
  });
}
