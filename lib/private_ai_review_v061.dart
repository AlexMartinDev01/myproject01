import 'package:flutter/material.dart';
import 'main.dart';
import 'core/ai.dart';

/// Opt-in cloud analysis of explicitly selected, previewed excerpts.
/// This page never sends journal text from initState/build or checkbox changes.
class PrivateAiReviewPage extends StatefulWidget {
  const PrivateAiReviewPage({super.key, required this.start, required this.end,
    required this.periodLabel});
  final DateTime start;
  final DateTime end;
  final String periodLabel;
  @override
  State<PrivateAiReviewPage> createState() => _PrivateAiReviewPageState();
}

class _PrivateAiReviewPageState extends State<PrivateAiReviewPage> {
  final selectedIds = <String>{};
  final preview = TextEditingController();
  final result = TextEditingController();
  bool loading = false;
  bool prepared = false;
  bool agreed = false;
  bool sending = false;
  bool saving = false;
  String? errorMessage;

  String get reviewQuestion =>
      '请依据用户明确选择的日记，为${widget.periodLabel}生成回顾。'
      '分别写主要经历、值得肯定的进展、存在的困难、下一阶段可尝试的行动。'
      '区分事实和推断，不做健康或心理疾病诊断。';

  @override
  void dispose() {
    preview.dispose();
    result.dispose();
    super.dispose();
  }

  void invalidatePreview() {
    setState(() {
      preview.clear();
      result.clear();
      prepared = false;
      agreed = false;
      errorMessage = null;
    });
  }

  Future<void> prepareReview() async {
    if (selectedIds.isEmpty || loading) return;
    setState(() {loading = true; errorMessage = null;});
    try {
      final store = CompassScope.of(context);
      final entries = store.ofKind('journal').where((e) => selectedIds.contains(e.id)).toList()
        ..sort((a,b) => a.createdAt.compareTo(b.createdAt));
      final blocks = <String>[];
      for (final entry in entries) {
        final text = (await store.privateJournalBody(entry.id)).trim();
        if (text.isNotEmpty) {
          blocks.add('【${entry.createdAt.year}-${entry.createdAt.month}-${entry.createdAt.day} · ${entry.title}】\n$text');
        }
      }
      if (!mounted) return;
      final joined = blocks.join('\n\n');
      if (joined.isEmpty) {
        throw StateError('所选日记没有可分析的正文');
      }
      if (joined.length > 18000) {
        throw StateError('所选内容超过 18000 字，请减少日记数量；不会自动截断');
      }
      preview.text = joined;
      setState(() {prepared = true; agreed = false; result.clear();});
    } catch (e) {
      if (mounted) setState(() {errorMessage = e.toString(); prepared = false;});
    } finally {
      if (mounted) setState(() => loading = false);
    }
  }

  Future<void> submitReview() async {
    final exactText = preview.text;
    if (sending || !prepared || !agreed) return;
    if (exactText.trim().isEmpty || exactText.length > 18000) {
      setState(() => errorMessage = '请输入 18000 字以内的回顾材料');
      return;
    }
    final store = CompassScope.of(context);
    setState(() {sending = true; errorMessage = null;});
    try {
      final client = AiClient(store);
      if (!await client.privateReviewEnabled()) {
        throw StateError('当前 Render 服务端尚未开启私密回顾；普通 AI 问答不受影响');
      }
      if (!mounted) return;
      final approved = await showDialog<bool>(context: context, builder: (ctx) => AlertDialog(
        title: const Text('确认本次发送到云端？'),
        content: Text('即将通过你的 Render AI 网关向 DeepSeek 发送刚才预览的 '
          '${exactText.length} 个字符，用于生成「${widget.periodLabel}」回顾。'
          '不包含未选择的日记，也不会自动保存结果。'
          '内容离开手机后将按相关云服务的数据处理政策处理。'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false),
            child: const Text('取消')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true),
            child: const Text('仅授权本次发送')),
        ],
      ));
      if (approved != true || !mounted) return;
      // The digest and upload are bound to this immutable snapshot.
      final response = await client.reviewPrivate(exactText, reviewQuestion);
      if (!mounted) return;
      result.text = response.answer;
      setState(() => agreed = false);
      notice(context, 'AI 回顾已生成，请检查并编辑后再保存');
    } catch (e) {
      if (mounted) setState(() => errorMessage = e.toString());
    } finally {
      if (mounted) setState(() => sending = false);
    }
  }

  Future<void> saveResult() async {
    if (saving || result.text.trim().isEmpty) return;
    setState(() => saving = true);
    try {
      await CompassScope.of(context).add(kind: 'journal',
        title: 'AI 回顾 · ${widget.periodLabel}',
        body: result.text.trim(), status: '私密',
        meta: {'ai_review': true, 'review_period': widget.periodLabel});
      if (!mounted) return;
      notice(context, '已加密保存为私密日记');
      result.clear();
      setState(() => prepared = false);
    } catch (e) {
      if (mounted) setState(() => errorMessage = '保存失败：$e');
    } finally {
      if (mounted) setState(() => saving = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final entries = CompassScope.of(context).ofKind('journal').where((e) =>
        !e.createdAt.isBefore(widget.start) && e.createdAt.isBefore(widget.end)).toList()
      ..sort((a,b) => a.createdAt.compareTo(b.createdAt));
    return PageFrame(title: 'AI 智能回顾',
      subtitle: '${widget.periodLabel} · 自主选择、先预览、单次授权',
      children: [
        const SoftCard(child: Text('私密日记默认只保存在手机。'
          '只有你选择具体日记、核对待发送文字、勾选同意，并在确认弹窗中批准后，'
          '才会通过 Render 网关向 DeepSeek 发送本次内容。')),
        const SectionHeading('第一步 · 选择日记'),
        if (entries.isEmpty) const SoftCard(child: Text('当前周期没有日记，请先记录内容。')),
        ...entries.map((e) => CheckboxListTile(
          value: selectedIds.contains(e.id),
          title: Text(e.title), subtitle: Text('${e.createdAt.month}月${e.createdAt.day}日'),
          onChanged: sending || loading ? null : (checked) {
            if (checked == true) {selectedIds.add(e.id);} else {selectedIds.remove(e.id);}
            invalidatePreview();
          })),
        SizedBox(width: double.infinity, child: OutlinedButton.icon(
          onPressed: selectedIds.isEmpty || sending || loading ? null : prepareReview,
          icon: const Icon(Icons.visibility_outlined),
          label: Text(loading ? '正在本机读取…' : '生成待发送内容预览'))),
        if (prepared) ...[
          const SectionHeading('第二步 · 检查并编辑发送内容'),
          TextField(controller: preview, minLines: 5, maxLines: 12,
            onChanged: (_) => setState(() {agreed = false; result.clear();}),
            decoration: const InputDecoration(labelText: '本次将发送的文字（可删减）')),
          const SizedBox(height: 8),
          CheckboxListTile(
            contentPadding: EdgeInsets.zero,
            value: agreed,
            onChanged: sending ? null : (v) => setState(() => agreed = v == true),
            title: const Text('我已检查内容，同意仅本次发送至云端 AI'),
            subtitle: const Text('未经授权不发送；可删除敏感段落；默认不保存授权')),
          SizedBox(width: double.infinity, child: FilledButton.icon(
            onPressed: agreed && !sending ? submitReview : null,
            icon: const Icon(Icons.auto_awesome),
            label: Text(sending ? 'AI 正在生成…' : '确认后生成 AI 回顾'))),
        ],
        if (errorMessage != null) Padding(
          padding: const EdgeInsets.symmetric(vertical: 12),
          child: Text(errorMessage!, style: const TextStyle(color: Colors.deepOrange))),
        if (result.text.isNotEmpty) ...[
          const SectionHeading('第三步 · 编辑并保存回顾'),
          TextField(controller: result, minLines: 6, maxLines: 16,
            decoration: const InputDecoration(labelText: 'AI 回顾草稿，可修改再保存')),
          const SizedBox(height: 10),
          SizedBox(width: double.infinity, child: FilledButton.icon(
            onPressed: saving ? null : saveResult,
            icon: const Icon(Icons.lock_outline),
            label: Text(saving ? '保存中…' : '加密保存回顾到日记'))),
        ],
        const Padding(padding: EdgeInsets.symmetric(vertical: 10),
          child: Text('AI 内容可能遗漏或误解经历，请自己核对。'
            '若网关未启用私密回顾，此页面只允许本机预览，不会绕过服务端限制。',
            style: TextStyle(fontSize: 12, color: Colors.grey))),
      ]);
  }
}
