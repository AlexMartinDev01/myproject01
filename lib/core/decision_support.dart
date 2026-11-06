import 'store.dart';

/// Returning source IDs is never proof that individual factual claims are true.
class DecisionCitationAudit {
  final List<String> citedIds;
  final List<String> unknownIds;
  final List<String> offeredIds;
  const DecisionCitationAudit(this.citedIds, this.unknownIds, this.offeredIds);
  bool get hasVerifiableLocations => citedIds.isNotEmpty;
  bool get containsUnrecognizedIds => unknownIds.isNotEmpty;

  static DecisionCitationAudit inspect(String answer, Iterable<String> offered) {
    final allowed = offered.toSet();
    final extracted = RegExp(r'\[(k_[0-9a-f]{16}_[0-9]+)\]')
        .allMatches(answer).map((m) => m.group(1)!).toSet();
    final valid = extracted.where(allowed.contains).toList();
    final invalid = extracted.where((id) => !allowed.contains(id)).toList();
    return DecisionCitationAudit(valid, invalid, allowed.toList());
  }
}

class DecisionReviewRecord {
  final String id;
  final DateTime createdAt;
  final String answer;
  final String model;
  final List<String> offeredChunkIds;
  final List<String> citedChunkIds;
  final List<String> unknownChunkIds;
  const DecisionReviewRecord({
    required this.id, required this.createdAt, required this.answer,
    required this.model, required this.offeredChunkIds,
    required this.citedChunkIds, required this.unknownChunkIds,
  });
  Map<String, dynamic> toJson() => {
    'id': id, 'created_at': createdAt.toUtc().toIso8601String(),
    'answer': answer, 'model': model,
    'offered_chunk_ids': offeredChunkIds,
    'cited_chunk_ids': citedChunkIds,
    'unknown_chunk_ids': unknownChunkIds,
  };
  factory DecisionReviewRecord.fromJson(Map<String, dynamic> json) {
    List<String> strings(String key) => (json[key] is List)
        ? (json[key] as List).whereType<String>().toList() : [];
    return DecisionReviewRecord(
      id: json['id']?.toString() ?? '',
      createdAt: DateTime.tryParse(json['created_at']?.toString() ?? '') ??
          DateTime.fromMillisecondsSinceEpoch(0, isUtc: true),
      answer: json['answer']?.toString() ?? '',
      model: json['model']?.toString() ?? '',
      offeredChunkIds: strings('offered_chunk_ids'),
      citedChunkIds: strings('cited_chunk_ids'),
      unknownChunkIds: strings('unknown_chunk_ids'),
    );
  }
}

List<DecisionReviewRecord> readDecisionReviews(CompassEntry entry) {
  final list = entry.meta['decision_analyses'];
  if (list is! List) return [];
  return list.whereType<Map>().map((e) =>
      DecisionReviewRecord.fromJson(Map<String, dynamic>.from(e))).toList();
}

String decisionQuestion(CompassEntry entry) {
  final goal = entry.meta['goal']?.toString() ?? '';
  final options = (entry.meta['options'] as List? ?? [])
      .map((e) => e.toString()).join(' ');
  return [entry.title, goal, options, entry.body].join(' ');
}

String decisionPrompt(CompassEntry entry) {
  if (entry.kind != 'decision') {
    throw const FormatException('只有决策记录可以进行决策分析');
  }
  final options = (entry.meta['options'] as List? ?? [])
      .map((e) => e.toString()).toList();
  String clip(Object? value, int maxChars) {
    final text = value?.toString() ?? '';
    return text.length > maxChars ? text.substring(0, maxChars) : text;
  }
  return '''
请根据用户的明确目标、备选方案、约束和本次所提供的证据，帮助用户分析而不是替用户拍板。
每一个来自个人资料的事实，尽量在原判断后使用本次提供的片段 ID 引用，格式如 [k_xxxxxxxxxxxxxxxx_1]。
禁止编造引用 ID；如果资料不足，请明确写“个人知识库暂缺证据”。
区分“个人材料直接支持的事实”“模型推断”“尚待验证的假设”。
请按以下 Markdown 结构简短而具体地回答：
## 核心判断（不超过 3 句话）
## 方案比较（每个方案的收益、成本、可逆性）
## 现有证据（仅引用本次提供的片段）
## 最强反方观点（至少一个反例）
## 证据缺口与不确定性
## 14 天最小验证计划（含可测量指标）
## 何时重新评估
如果没有提供任何个人知识片段，禁止声称已检索到了用户知识库中的证据。
【决策标题】
${clip(entry.title, 200)}
【目标】
${clip(entry.meta['goal'], 700)}
【备选方案】
${clip(options.join(' / '), 1100)}
【限制及背景】
${clip(entry.body, 2100)}
【评分权重】
${clip(entry.meta['criteria'], 700)}
''';
}

Future<DecisionReviewRecord> saveDecisionReview(
  CompassStore store, String decisionId, String answer, String model,
  List<String> offeredChunkIds,
) async {
  if (answer.trim().isEmpty || answer.length > 50000) {
    throw const FormatException('AI 决策报告为空或长度超出限制');
  }
  if (offeredChunkIds.length > 9 ||
      offeredChunkIds.toSet().length != offeredChunkIds.length) {
    throw const FormatException('来源片段数量异常');
  }
  final entry = store.byId(decisionId);
  if (entry == null || entry.kind != 'decision') throw StateError('决策记录不存在');
  for (final id in offeredChunkIds) {
    final source = await store.knowledge.lookup(id);
    if (source == null || !['source', 'card', 'note'].contains(source.kind)) {
      throw StateError('引用源已失效或没有授权：$id');
    }
  }
  final audit = DecisionCitationAudit.inspect(answer, offeredChunkIds);
  final record = DecisionReviewRecord(
    id: CompassStore.newId(),
    createdAt: DateTime.now().toUtc(),
    answer: answer.trim(),
    model: model,
    offeredChunkIds: List.unmodifiable(offeredChunkIds),
    citedChunkIds: audit.citedIds,
    unknownChunkIds: audit.unknownIds,
  );
  final current = readDecisionReviews(entry);
  final all = [...current, record];
  final recent = all.length > 12 ? all.sublist(all.length - 12) : all;
  await store.update(entry.copyWith(meta: {
    ...entry.meta,
    'decision_analyses': recent.map((e) => e.toJson()).toList(),
  }));
  return record;
}

String decisionFollowUpPrompt(CompassEntry entry, DecisionReviewRecord? last) {
  final base = decisionPrompt(entry);
  final report = last?.answer ?? '';
  const prefix = '\n【之前的分析（供继续讨论）】\n';
  const ending = '\n请从上次分析继续，重点帮助我澄清尚未解决的假设。';
  final remaining = (7500 - base.length - prefix.length - ending.length)
      .clamp(0, 3400).toInt();
  final excerpt = report.length > remaining ? report.substring(0, remaining) : report;
  return base + prefix + excerpt + ending;
}
