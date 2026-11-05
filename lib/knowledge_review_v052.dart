import 'package:flutter/material.dart';

import 'main.dart';
import 'core/knowledge_candidates.dart';
import 'core/store.dart';

class KnowledgeReviewPage extends StatefulWidget {
  final List<KnowledgeCandidate> candidates;
  final String? conversationId;
  final String? messageId;
  final String? originalSourceId;
  final List<String> offeredSourceIds;
  final bool inputWasTruncated;

  const KnowledgeReviewPage({
    super.key,
    required this.candidates,
    this.conversationId,
    this.messageId,
    this.originalSourceId,
    this.offeredSourceIds = const [],
    this.inputWasTruncated = false,
  });

  @override
  State<KnowledgeReviewPage> createState() => _KnowledgeReviewPageState();
}

class _EditableCandidate {
  final TextEditingController title;
  final TextEditingController explanation;
  final TextEditingController conditions;
  final TextEditingController counterexample;
  final TextEditingController application;
  String topic;
  String? mergeTargetId;
  bool selected = true;

  _EditableCandidate(KnowledgeCandidate point)
      : title = TextEditingController(text: point.title),
        explanation = TextEditingController(text: point.explanation),
        conditions = TextEditingController(text: point.conditions),
        counterexample = TextEditingController(text: point.counterexample),
        application = TextEditingController(text: point.application),
        topic = point.topic;

  KnowledgeSaveDraft toDraft() => KnowledgeSaveDraft(
    title: title.text,
    explanation: explanation.text,
    conditions: conditions.text,
    counterexample: counterexample.text,
    application: application.text,
    topic: topic,
    mergeTargetId: mergeTargetId,
  );

  void dispose() {
    title.dispose();
    explanation.dispose();
    conditions.dispose();
    counterexample.dispose();
    application.dispose();
  }
}

class _PotentialDuplicate {
  final CompassEntry card;
  final double score;
  _PotentialDuplicate(this.card, this.score);
}

class _KnowledgeReviewPageState extends State<KnowledgeReviewPage> {
  late final List<_EditableCandidate> editable;
  bool saving = false;
  int get selectedCount => editable.where((e) => e.selected).length;

  @override
  void initState() {
    super.initState();
    editable = widget.candidates.map(_EditableCandidate.new).toList();
  }

  @override
  void dispose() {
    for (final item in editable) {
      item.dispose();
    }
    super.dispose();
  }

  List<_PotentialDuplicate> duplicatesFor(String title) {
    final cards = CompassScope.of(context).ofKind('card');
    final matches = cards.map((e) =>
      _PotentialDuplicate(e, knowledgeTitleSimilarity(title, e.title)))
        .where((e) => e.score >= 0.40).toList();
    matches.sort((a, b) => b.score.compareTo(a.score));
    return matches.take(4).toList();
  }

  Future<void> chooseMerge(int index) async {
    final cards = CompassScope.of(context).ofKind('card');
    if (cards.isEmpty) {
      notice(context, '知识库里还没有知识卡，可以先新建');
      return;
    }
    String search = '';
    final choice = await showModalBottomSheet<String>(
      context: context, isScrollControlled: true,
      showDragHandle: true,
      builder: (sheet) => StatefulBuilder(
        builder: (sheet, updateSheet) {
          final shown = cards.where((e) =>
            e.title.toLowerCase().contains(search.trim().toLowerCase()))
              .take(80).toList();
          return SafeArea(child: Padding(
            padding: EdgeInsets.fromLTRB(18, 4, 18,
              MediaQuery.of(sheet).viewInsets.bottom + 14),
            child: SizedBox(height: MediaQuery.of(sheet).size.height * .65,
              child: Column(children: [
                const Text('选择合并目标', style: TextStyle(
                  fontWeight: FontWeight.w700, fontSize: 19)),
                const SizedBox(height: 10),
                TextField(
                  onChanged: (v) => updateSheet(() => search = v),
                  decoration: const InputDecoration(
                    prefixIcon: Icon(Icons.search),
                    hintText: '查找已有知识卡标题')),
                TextButton.icon(
                  onPressed: () => Navigator.pop(sheet, '__new__'),
                  icon: const Icon(Icons.add), label: const Text('改为新建，不合并')),
                Expanded(child: ListView.builder(
                  itemCount: shown.length,
                  itemBuilder: (ctx, i) => ListTile(
                    leading: const Icon(Icons.menu_book_outlined, color: grape),
                    title: Text(shown[i].title, maxLines: 2,
                      overflow: TextOverflow.ellipsis),
                    subtitle: Text(shown[i].meta['topic']?.toString() ?? ''),
                    onTap: () => Navigator.pop(sheet, shown[i].id),
                  ),
                )),
              ])),
          ));
        },
      ),
    );
    if (!mounted || choice == null) return;
    setState(() => editable[index].mergeTargetId =
      choice == '__new__' ? null : choice);
  }

  Future<void> saveSelected() async {
    if (saving) return;
    final choices = editable.where((e) => e.selected).toList();
    if (choices.isEmpty) {
      notice(context, '至少勾选一个知识点');
      return;
    }
    final drafts = choices.map((e) => e.toDraft()).toList();
    try {
      for (final draft in drafts) {
        draft.validate();
      }
    } catch (e) {
      notice(context, '请先修改知识点：$e');
      return;
    }

    // Similar titles require an explicit review. Never merge automatically.
    final potentiallyRepeated = drafts.where((d) =>
      d.mergeTargetId == null && duplicatesFor(d.title).isNotEmpty)
        .map((d) => d.title).toList();
    if (potentiallyRepeated.isNotEmpty) {
      final approved = await showDialog<bool>(
        context: context, builder: (c) => AlertDialog(
          title: const Text('发现可能重复的知识点'),
          content: SingleChildScrollView(child: Text(
            '以下标题与已有知识卡相似：\n\n'
            '${potentiallyRepeated.join("\n")}\n\n'
            '建议选择合并或修改标题。确定仍然新建吗？')),
          actions: [
            TextButton(onPressed: () => Navigator.pop(c, false),
              child: const Text('返回检查')),
            FilledButton(onPressed: () => Navigator.pop(c, true),
              child: const Text('仍然新建')),
          ],
        ),
      );
      if (approved != true || !mounted) return;
    }

    setState(() => saving = true);
    try {
      final ids = await CompassScope.of(context).saveKnowledgeCandidates(
        drafts,
        conversationId: widget.conversationId,
        messageId: widget.messageId,
        originalSourceId: widget.originalSourceId,
        offeredSourceIds: widget.offeredSourceIds,
      );
      if (!mounted) return;
      notice(context, '已保存 ${ids.length} 个知识点（待验证），可在知识库中查看');
      Navigator.pop(context, ids);
    } catch (e) {
      if (mounted) notice(context, '保存失败，未写入部分内容：$e');
    } finally {
      if (mounted) setState(() => saving = false);
    }
  }

  Widget editorSection(String label, TextEditingController controller, {
    int maxLines = 4, int minLines = 1, int maxLength = 1200,
    bool updateDuplicates = false,
  }) => Padding(
    padding: const EdgeInsets.only(bottom: 12),
    child: TextField(
      controller: controller,
      minLines: minLines, maxLines: maxLines, maxLength: maxLength,
      onChanged: updateDuplicates ? (_) => setState(() {}) : null,
      decoration: InputDecoration(labelText: label,
        floatingLabelBehavior: FloatingLabelBehavior.always),
    ),
  );

  Widget candidateTile(int index) {
    final item = editable[index];
    final matches = duplicatesFor(item.title.text);
    final merged = item.mergeTargetId == null ? null :
      CompassScope.of(context).byId(item.mergeTargetId!);
    return Card(
      margin: const EdgeInsets.only(bottom: 14),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(18),
        side: const BorderSide(color: Color(0xFFECE3F0))),
      elevation: 0, color: Colors.white,
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        CheckboxListTile(
          value: item.selected,
          onChanged: saving ? null : (v) => setState(() => item.selected = v ?? false),
          title: Text(item.title.text.trim().isEmpty ?
            '未填写标题' : item.title.text, maxLines: 2,
            style: const TextStyle(fontWeight: FontWeight.w700)),
          subtitle: Text(item.selected ? '已选中 · 可以编辑或选择合并' :
            '已取消选择 · 保存时跳过'),
          activeColor: grape,
          controlAffinity: ListTileControlAffinity.leading,
        ),
        if (item.selected) Padding(
          padding: const EdgeInsets.fromLTRB(15, 2, 15, 15),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            editorSection('知识点标题', item.title, maxLength: 160,
              maxLines: 2, updateDuplicates: true),
            editorSection('核心解释', item.explanation,
              minLines: 3, maxLines: 9, maxLength: 2500),
            DropdownButtonFormField<String>(
              initialValue: item.topic,
              items: knowledgeTopics.map((s) =>
                DropdownMenuItem(value: s, child: Text(s))).toList(),
              decoration: const InputDecoration(labelText: '知识专题'),
              onChanged: (value) => setState(() =>
                item.topic = value ?? item.topic),
            ),
            const SizedBox(height: 15),
            editorSection('适用条件', item.conditions, maxLines: 4),
            editorSection('反例与边界', item.counterexample, maxLines: 4),
            editorSection('应用或验证方式', item.application, maxLines: 4),
            if (matches.isNotEmpty) ...[
              const Text('可能已有相关知识卡 · 仅提醒，不会自动合并',
                style: TextStyle(color: grape, fontWeight: FontWeight.w600)),
              const SizedBox(height: 5),
              Wrap(spacing: 7, runSpacing: 5, children: matches.map((m) =>
                ActionChip(
                  label: Text('${m.card.title} · ${(m.score * 100).round()}%'),
                  onPressed: saving ? null : () => setState(() =>
                    item.mergeTargetId = m.card.id),
                )).toList()),
              const SizedBox(height: 8),
            ],
            Row(children: [
              const Icon(Icons.merge_type, color: grape),
              const SizedBox(width: 7),
              Expanded(child: Text(merged == null ? '保存方式：新建知识卡' :
                '保存方式：合并至《${merged.title}》',
                style: const TextStyle(fontWeight: FontWeight.w600))),
            ]),
            const SizedBox(height: 6),
            OutlinedButton.icon(
              onPressed: saving ? null : () => chooseMerge(index),
              icon: const Icon(Icons.swap_horiz),
              label: const Text('选择已有卡合并 / 改为新建'),
            ),
            const Text('合并时会保留旧正文并追加新知识点，同时记录提炼来源；不会覆盖原卡。',
              style: TextStyle(fontSize: 12, color: Colors.grey, height: 1.5)),
          ]),
        ),
      ]),
    );
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('提炼知识点 · 人工筛选')),
    body: ListView(padding: const EdgeInsets.fromLTRB(16, 10, 16, 16),
      children: [
        Container(
          padding: const EdgeInsets.all(15),
          decoration: BoxDecoration(
            color: const Color(0xFFF2EBF8),
            borderRadius: BorderRadius.circular(17)),
          child: const Text(
            'AI 生成的是“候选知识点”，不是已核验的事实。你可以取消勾选、修改每条解释，'
            '也可以合并到已有知识卡。只有点击底部确认保存时才会写入知识库。',
            style: TextStyle(height: 1.6, color: ink)),
        ),
        if (widget.inputWasTruncated) Padding(
          padding: const EdgeInsets.symmetric(vertical: 10),
          child: Text('本次只提炼了所选文本的前 6,500 字符；原文未删减。',
            style: TextStyle(color: Colors.deepOrange.shade800)),
        ),
        if (widget.offeredSourceIds.isNotEmpty || widget.originalSourceId != null)
          const Padding(
            padding: EdgeInsets.symmetric(vertical: 12),
            child: Text('本次 AI 曾接收来源资料，但这不代表每一个观点都已获得逐条证据验证。',
              style: TextStyle(fontSize: 12, color: Colors.grey))),
        const SizedBox(height: 14),
        Row(children: [
          Expanded(child: Text('候选知识点 · ${editable.length}',
            style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold))),
          TextButton(onPressed: saving ? null : () => setState(() {
            for (final item in editable) { item.selected = true; }
          }), child: const Text('全选')),
          TextButton(onPressed: saving ? null : () => setState(() {
            for (final item in editable) { item.selected = false; }
          }), child: const Text('全不选')),
        ]),
        const SizedBox(height: 9),
        for (var i = 0; i < editable.length; i++) candidateTile(i),
      ]),
    bottomNavigationBar: SafeArea(top: false, child: Container(
      padding: const EdgeInsets.fromLTRB(16, 10, 16, 12),
      decoration: const BoxDecoration(color: Color(0xFFF9F5FA),
        border: Border(top: BorderSide(color: Color(0xFFECE3F0)))),
      child: Row(children: [
        Expanded(child: Text('已选 ${selectedCount} / ${editable.length} 条',
          style: const TextStyle(fontWeight: FontWeight.w600))),
        FilledButton.icon(
          onPressed: saving || selectedCount == 0 ? null : saveSelected,
          icon: saving ? const SizedBox(width: 17, height: 17,
            child: CircularProgressIndicator(strokeWidth: 2)) :
              const Icon(Icons.library_add_outlined),
          label: Text(saving ? '正在保存…' : '确认保存所选知识点'),
        ),
      ]),
    )),
  );
}
