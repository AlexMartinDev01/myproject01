import 'knowledge_candidates.dart';

/// Plain local draft metadata for *ordinary* source materials only.
/// Private journals are never allowed as a source of review drafts.
class KnowledgeDraftPoint {
  final KnowledgeCandidate candidate;
  final bool selected;
  final String? mergeTargetId;
  const KnowledgeDraftPoint({
    required this.candidate,
    this.selected = true,
    this.mergeTargetId,
  });

  Map<String, dynamic> toJson() => {
    'title': candidate.title,
    'explanation': candidate.explanation,
    'conditions': candidate.conditions,
    'counterexample': candidate.counterexample,
    'application': candidate.application,
    'topic': candidate.topic,
    'selected': selected,
    if (mergeTargetId != null) 'merge_target_id': mergeTargetId,
  };

  factory KnowledgeDraftPoint.fromJson(Map<String, dynamic> json) {
    String field(String name) => json[name] is String
        ? (json[name] as String) : '';
    final topic = field('topic');
    return KnowledgeDraftPoint(
      candidate: KnowledgeCandidate(
        title: field('title'), explanation: field('explanation'),
        conditions: field('conditions'),
        counterexample: field('counterexample'),
        application: field('application'),
        topic: knowledgeTopics.contains(topic) ? topic : '自我成长',
      ),
      selected: json['selected'] != false,
      mergeTargetId: json['merge_target_id'] is String &&
          (json['merge_target_id'] as String).isNotEmpty
          ? json['merge_target_id'] as String : null,
    );
  }
}

class KnowledgeReviewDraft {
  final String sourceId;
  final List<KnowledgeDraftPoint> points;
  final bool inputWasTruncated;
  const KnowledgeReviewDraft({
    required this.sourceId, required this.points,
    this.inputWasTruncated = false,
  });

  factory KnowledgeReviewDraft.fromCandidates(
    String sourceId, List<KnowledgeCandidate> points, {
    bool inputWasTruncated = false,
  }) => KnowledgeReviewDraft(
    sourceId: sourceId,
    points: points.map((p) => KnowledgeDraftPoint(candidate: p)).toList(),
    inputWasTruncated: inputWasTruncated,
  );

  Map<String, dynamic> toJson() => {
    'version': 1,
    'source_id': sourceId,
    'truncated': inputWasTruncated,
    'items': points.map((p) => p.toJson()).toList(),
  };

  factory KnowledgeReviewDraft.fromJson(Map<String, dynamic> json) {
    if (json['version'] != 1 || json['source_id'] is! String ||
        json['items'] is! List) {
      throw const FormatException('知识整理草稿格式不兼容');
    }
    final entries = json['items'] as List;
    if (entries.isEmpty || entries.length > 8) {
      throw const FormatException('知识整理草稿须包含 1–8 条候选知识');
    }
    return KnowledgeReviewDraft(
      sourceId: json['source_id'] as String,
      inputWasTruncated: json['truncated'] == true,
      points: entries.map((e) {
        if (e is! Map<String, dynamic>) {
          throw const FormatException('知识整理草稿内容损坏');
        }
        return KnowledgeDraftPoint.fromJson(e);
      }).toList(),
    );
  }
}
