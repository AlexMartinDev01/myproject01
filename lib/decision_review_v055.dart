import 'package:flutter/material.dart';
import 'main.dart';
import 'ai_chat_v051.dart';
import 'knowledge_ref_v053.dart';
import 'core/ai.dart';
import 'core/decision_support.dart';
import 'core/knowledge_index.dart';
import 'core/store.dart';

/// The decision room sends only knowledge fragments explicitly checked by the user.
class DecisionReviewPage extends StatefulWidget {
  final String id;
  const DecisionReviewPage({super.key, required this.id});
  @override
  State<DecisionReviewPage> createState() => _DecisionReviewPageState();
}

class _DecisionReviewPageState extends State<DecisionReviewPage> {
  final forecast = TextEditingController();
  final reality = TextEditingController();
  final nextAction = TextEditingController();
  List<KnowledgeHit> candidates = [];
  final selectedIds = <String>{};
  String? activeReportId, error;
  bool analyzing = false, searching = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final e = CompassScope.of(context).byId(widget.id);
      if (e != null) {
        forecast.text = e.meta['prediction']?.toString() ?? '';
        nextAction.text = e.meta['next_action']?.toString() ?? '';
        reality.text = e.meta['observed']?.toString() ?? '';
      }
      findSources();
    });
  }

  @override
  void dispose() {
    forecast.dispose(); reality.dispose(); nextAction.dispose();
    super.dispose();
  }

  List<KnowledgeHit> get checked =>
      candidates.where((h) => selectedIds.contains(h.chunkId)).toList();

  Future<void> findSources() async {
    if (!mounted || searching) return;
    final store = CompassScope.of(context);
    final entry = store.byId(widget.id);
    if (entry == null) return;
    setState(() { searching = true; error = null; });
    try {
      final hits = await store.findKnowledge(decisionQuestion(entry), limit: 9);
      if (mounted) setState(() {
        candidates = hits;
        selectedIds.clear();
        selectedIds.addAll(hits.take(6).map((h) => h.chunkId));
      });
    } catch (e) {
      if (mounted) setState(() => error = '知识检索失败：' + e.toString());
    } finally {
      if (mounted) setState(() => searching = false);
    }
  }

  Future<void> analyze(CompassEntry decision) async {
    if (analyzing) return;
    final store = CompassScope.of(context);
    final sources = checked;
    setState(() { analyzing = true; error = null; });
    try {
      final result = await AiClient(store).ask(decisionPrompt(decision),
        mode: 'decision', evidence: sources.map((e) => e.toEvidence()).toList());
      final report = await saveDecisionReview(store, decision.id, result.answer,
        result.model, sources.map((h) => h.chunkId).toList());
      if (mounted) {
        setState(() => activeReportId = report.id);
        notice(context, 'AI 报告已保存为一个历史版本');
      }
    } catch (e) {
      if (mounted) setState(() => error = '分析失败，未保存报告：' + e.toString());
    } finally {
      if (mounted) setState(() => analyzing = false);
    }
  }

  Future<void> saveRetrospective() async {
    final store = CompassScope.of(context);
    final entry = store.byId(widget.id);
    if (entry == null) return;
    try {
      await store.update(entry.copyWith(
        status: reality.text.trim().isEmpty ? '执行中' : '已复盘',
        meta: {...entry.meta, 'prediction': forecast.text.trim(),
          'next_action': nextAction.text.trim(), 'observed': reality.text.trim()}));
      if (mounted) notice(context, '预测与复盘已保存');
    } catch (e) {
      if (mounted) notice(context, '保存失败：' + e.toString());
    }
  }

  Future<void> deleteDecision(CompassEntry entry) async {
    final yes = await showDialog<bool>(context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('确定删除这条决策？'),
        content: const Text('分析历史和复盘将一起删除，不会删除原始知识资料。'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false),
            child: const Text('取消')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true),
            child: const Text('删除')),
        ]));
    if (yes == true && mounted) {
      await CompassScope.of(context).remove(entry);
      if (mounted) Navigator.pop(context);
    }
  }

  Widget sourceCard(KnowledgeHit hit) => SoftCard(
    padding: const EdgeInsets.all(10),
    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      CheckboxListTile(
        value: selectedIds.contains(hit.chunkId), activeColor: grape,
        onChanged: analyzing ? null : (value) => setState(() {
          if (value == true) { selectedIds.add(hit.chunkId); }
          else { selectedIds.remove(hit.chunkId); }
        }),
        title: Text(hit.title, maxLines: 2, overflow: TextOverflow.ellipsis),
        subtitle: Text('来源片段 ' + hit.part.toString() +
          ' · 匹配分数 ' + hit.score.toStringAsFixed(1))),
      Padding(padding: const EdgeInsets.symmetric(horizontal: 12),
        child: Text(hit.body.length > 170 ?
          hit.body.substring(0,170) + '…' : hit.body,
          maxLines: 5, overflow: TextOverflow.ellipsis,
          style: const TextStyle(fontSize: 12, height: 1.5))),
      Align(alignment: Alignment.centerRight, child: TextButton.icon(
        icon: const Icon(Icons.open_in_new, size: 16),
        label: const Text('查看原始段落'),
        onPressed: () => openPage(context,
          KnowledgeReferencePage(chunkId: hit.chunkId)))),
    ]));

  Widget reportView(DecisionReviewRecord record) => SoftCard(
    color: const Color(0xFFF3EDF8),
    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      const Text('AI 决策分析', style: TextStyle(
        fontWeight: FontWeight.bold, fontSize: 18)),
      const SizedBox(height: 8),
      const Text('来源编号只说明可以查到对应资料，不代表模型每个判断都已核实。',
        style: TextStyle(fontSize: 12, height: 1.5, color: Colors.black54)),
      if (record.offeredChunkIds.isEmpty)
        const Text('本次没有提供个人知识资料，结论仅供验证。',
          style: TextStyle(color: Colors.deepOrange)),
      if (record.offeredChunkIds.isNotEmpty && record.citedChunkIds.isEmpty)
        const Text('模型没有实际引用本次提供的原文段落。',
          style: TextStyle(color: Colors.deepOrange)),
      if (record.unknownChunkIds.isNotEmpty)
        Text('提示：存在 ' + record.unknownChunkIds.length.toString() +
          ' 个未提供的引用编号，不能视为有效证据。',
          style: const TextStyle(color: Colors.red)),
      const Divider(),
      MessageMarkdown(data: record.answer),
      if (record.citedChunkIds.isNotEmpty) ...[
        const SizedBox(height: 12),
        const Text('可核对的引用位置', style:
          TextStyle(fontWeight: FontWeight.bold)),
        Wrap(spacing: 6, children: record.citedChunkIds.map((id) =>
          ActionChip(label: const Text('查看原文'),
            onPressed: () => openPage(context,
              KnowledgeReferencePage(chunkId: id)))).toList()),
      ],
      const SizedBox(height: 9),
      Text('模型：' + record.model + ' · 提供 ' +
        record.offeredChunkIds.length.toString() + ' 段知识',
        style: const TextStyle(fontSize: 11, color: Colors.grey)),
    ]));

  @override
  Widget build(BuildContext context) {
    final store = CompassScope.of(context);
    final entry = store.byId(widget.id);
    if (entry == null || entry.kind != 'decision') {
      return const PageFrame(title: '决策不存在',
        children: [Text('记录可能已被删除。')]);
    }
    final options = (entry.meta['options'] as List? ?? [])
      .map((e) => e.toString()).join(' / ');
    final history = readDecisionReviews(entry);
    DecisionReviewRecord? current;
    if (history.isNotEmpty) {
      current = history.firstWhere((h) => h.id == activeReportId,
        orElse: () => history.last);
    }
    return PageFrame(title: '知识驱动决策', subtitle: entry.title, children: [
      SoftCard(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text('目标：' + (entry.meta['goal']?.toString() ?? '-'),
          style: const TextStyle(fontWeight: FontWeight.bold)),
        const SizedBox(height: 8),
        Text('方案：' + (options.isEmpty ? '未填写' : options)),
        const SizedBox(height: 8),
        Text('约束：' + (entry.body.isEmpty ? '未填写' : entry.body)),
        const SizedBox(height: 8),
        Text('权重：' + (entry.meta['criteria']?.toString() ?? '{}')),
      ])),
      const SectionHeading('① 选择参与分析的资料'),
      const SoftCard(color: Color(0xFFF4EDF9),
        child: Text('先在本机检索知识，只会将勾选的段落发送给 AI。'
          '私密日记不会参加自动检索。没有资料时只能生成待验证的思路。')),
      if (searching) const Center(child: CircularProgressIndicator()),
      if (!searching && candidates.isEmpty)
        const SoftCard(child: Text('暂时没有找到相关资料，可以不使用知识库继续分析。')),
      if (candidates.isNotEmpty) ...[
        Row(children: [
          Expanded(child: Text('找到 ' + candidates.length.toString() +
            ' 段，选择了 ' + selectedIds.length.toString() + ' 段')),
          TextButton(onPressed: analyzing ? null : () => setState(() {
            selectedIds.clear();
            selectedIds.addAll(candidates.map((h) => h.chunkId));
          }), child: const Text('全选')),
          TextButton(onPressed: analyzing ? null : () =>
            setState(selectedIds.clear), child: const Text('不引用')),
        ]),
        for (final item in candidates) sourceCard(item),
      ],
      OutlinedButton.icon(onPressed: searching || analyzing ? null : findSources,
        icon: const Icon(Icons.refresh), label: const Text('重新检索')),
      const SectionHeading('② AI 方案比较与反方审查'),
      FilledButton.icon(
        icon: const Icon(Icons.psychology_outlined),
        onPressed: analyzing || searching ? null : () => analyze(entry),
        label: Text(analyzing ? '分析中…' : checked.isEmpty
          ? '不使用个人资料进行分析'
          : '使用所选 ' + checked.length.toString() + ' 段资料分析'),
      ),
      if (error != null) Text(error!, style: const TextStyle(color: Colors.red)),
      if (history.isNotEmpty) ...[
        SectionHeading('分析历史 · ' + history.length.toString() + ' 版'),
        Wrap(spacing: 7, runSpacing: 5, children: history.reversed.map((h) =>
          ChoiceChip(
            selected: current?.id == h.id,
            label: Text(h.createdAt.month.toString() + '/' +
              h.createdAt.day.toString() + ' ' +
              h.createdAt.hour.toString() + ':' +
              h.createdAt.minute.toString().padLeft(2,'0')),
            onSelected: (_) => setState(() => activeReportId = h.id),
          )).toList()),
        if (current != null) reportView(current),
        OutlinedButton.icon(
          onPressed: () => openPage(context, AiChatPage(
            mode: 'decision', restrictEvidenceToSelection: true,
            selectedEvidence: checked.map((h) => h.toEvidence()).toList(),
            initialPrompt: decisionFollowUpPrompt(entry, current))),
          icon: const Icon(Icons.forum_outlined),
          label: const Text('基于当前分析继续追问')),
      ],
      const SectionHeading('③ 预测与现实复盘'),
      TextField(controller: forecast, minLines: 2, maxLines: 5,
        decoration: const InputDecoration(labelText: '预测、选择与成功指标')),
      const SizedBox(height: 10),
      TextField(controller: nextAction, minLines: 2, maxLines: 5,
        decoration: const InputDecoration(labelText: '14 天最小实验')),
      const SizedBox(height: 10),
      TextField(controller: reality, minLines: 2, maxLines: 6,
        decoration: const InputDecoration(labelText: '真实观察结果')),
      const SizedBox(height: 14),
      FilledButton(onPressed: saveRetrospective,
        child: const Text('保存预测与复盘')),
      TextButton(onPressed: () => deleteDecision(entry),
        child: const Text('删除这条决策', style: TextStyle(color: Colors.red))),
    ]);
  }
}
