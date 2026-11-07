import 'store.dart';

/// Read-only projection of authored/source-backed knowledge relations.
/// Never treats AI suggestions as verified factual citations.
class CardTrace {
  final List<CompassEntry> sources;
  final List<TraceRelation> relatedCards;
  final bool aiAssisted;
  final bool humanSelected;
  final bool hasMissingSources;
  const CardTrace({
    required this.sources,
    required this.relatedCards,
    required this.aiAssisted,
    required this.humanSelected,
    required this.hasMissingSources,
  });

  static CardTrace forCard(CompassEntry card, List<CompassEntry> all) {
    if (card.kind != 'card') {
      throw const FormatException('来源追溯仅适用于知识卡');
    }
    final sourcesById = {
      for (final entry in all.where((e) => e.kind == 'source')) entry.id: entry,
    };
    final cardsById = {
      for (final entry in all.where((e) => e.kind == 'card')) entry.id: entry,
    };
    final sourceIds = <String>{};
    final rawSource = card.meta['source_id'];
    if (rawSource is String && rawSource.isNotEmpty) sourceIds.add(rawSource);
    final rawReviews = card.meta['ai_candidate_reviews'];
    bool humanSelected = false;
    if (rawReviews is List) {
      for (final value in rawReviews) {
        if (value is! Map) continue;
        if (value['review'] == 'human_selected') humanSelected = true;
        final sourceId = value['original_source_id'];
        if (sourceId is String && sourceId.isNotEmpty) sourceIds.add(sourceId);
        final offered = value['offered_source_ids'];
        if (offered is List) {
          for (final id in offered.whereType<String>()) {
            // Offered knowledge cards are evidence, not original sources.
            if (sourcesById.containsKey(id)) sourceIds.add(id);
          }
        }
      }
    }
    final related = <String, TraceRelation>{};
    final rawLinks = card.meta['related_card_ids'];
    final rawTypes = card.meta['related_card_types'];
    if (rawLinks is List) {
      for (final id in rawLinks.whereType<String>()) {
        if (id == card.id || !cardsById.containsKey(id)) continue;
        final type = rawTypes is Map ? rawTypes[id] : null;
        related[id] = TraceRelation(cardsById[id]!,
          _safeType(type), direction: '主动关联');
      }
    }
    // Also show links authored on other cards (backlinks).
    for (final other in cardsById.values) {
      if (other.id == card.id) continue;
      final links = other.meta['related_card_ids'];
      if (links is! List || !links.contains(card.id)) continue;
      final raw = other.meta['related_card_types'];
      related.putIfAbsent(other.id, () => TraceRelation(other,
        _safeType(raw is Map ? raw[card.id] : null), direction: '被其他卡片关联'));
    }
    final found = sourceIds.where(sourcesById.containsKey)
        .map((id) => sourcesById[id]!).toList()
      ..sort((a, b) => a.title.compareTo(b.title));
    final relations = related.values.toList()
      ..sort((a,b) => a.card.title.compareTo(b.card.title));
    return CardTrace(
      sources: found,
      relatedCards: relations,
      aiAssisted: card.meta['ai_assisted'] == true,
      humanSelected: humanSelected,
      hasMissingSources: sourceIds.length > found.length,
    );
  }

  static String _safeType(Object? value) {
    const allowed = {'相关', '支持', '反例', '延伸', '对比'};
    return value is String && allowed.contains(value) ? value : '相关';
  }
}

class TraceRelation {
  final CompassEntry card;
  final String type;
  final String direction;
  const TraceRelation(this.card, this.type, {required this.direction});
}
