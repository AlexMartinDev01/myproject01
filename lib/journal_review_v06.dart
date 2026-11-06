import 'package:flutter/material.dart';
import 'main.dart';

/// V0.6 local-only review. No private journal plaintext is sent to an API.
enum ReviewPeriod { week, month }

class JournalReviewPage extends StatefulWidget {
  const JournalReviewPage({super.key});
  @override
  State<JournalReviewPage> createState() => _JournalReviewPageState();
}

class _JournalReviewPageState extends State<JournalReviewPage> {
  ReviewPeriod period = ReviewPeriod.week;
  DateTime anchor = DateTime.now();
  bool includeExcerpts = false;
  bool saving = false;
  final highlight = TextEditingController();
  final continueDoing = TextEditingController();
  final nextStep = TextEditingController();

  @override
  void dispose() {
    highlight.dispose();
    continueDoing.dispose();
    nextStep.dispose();
    super.dispose();
  }

  void clearDraft() {
    highlight.clear();
    continueDoing.clear();
    nextStep.clear();
  }

  Future<void> saveReflection(String periodLabel) async {
    if (saving) return;
    final answers = [highlight.text.trim(), continueDoing.text.trim(), nextStep.text.trim()];
    if (answers.every((t) => t.isEmpty)) {
      notice(context, '请至少填写一项回顾内容');
      return;
    }
    setState(() => saving = true);
    try {
      final title = '${period == ReviewPeriod.week ? '每周小结' : '每月回顾'} · $periodLabel';
      final content = ['最重要的一件事：${answers[0]}',
        '值得继续保持：${answers[1]}', '下一步想尝试：${answers[2]}'].join('\\n\\n');
      await CompassScope.of(context).add(kind: 'journal', title: title,
        body: content, status: '私密', meta: {'local_review': true});
      if (!mounted) return;
      clearDraft();
      setState(() => includeExcerpts = false);
      notice(context, '回顾已加密保存到本机日记');
    } catch (e) {
      if (mounted) notice(context, '回顾未保存，请重试：$e');
    } finally {
      if (mounted) setState(() => saving = false);
    }
  }

  DateTime get beginning {
    final d = DateTime(anchor.year, anchor.month, anchor.day);
    return period == ReviewPeriod.week
        ? d.subtract(Duration(days: d.weekday - DateTime.monday))
        : DateTime(d.year, d.month);
  }
  DateTime get ending => period == ReviewPeriod.week
      ? beginning.add(const Duration(days: 7))
      : DateTime(beginning.year, beginning.month + 1);
  void shift(int direction) {
    setState(() {
      anchor = period == ReviewPeriod.week
          ? anchor.add(Duration(days: 7 * direction))
          : DateTime(anchor.year, anchor.month + direction, 15);
      includeExcerpts = false;
      clearDraft();
    });
  }

  @override
  Widget build(BuildContext context) {
    final store = CompassScope.of(context);
    final start = beginning, end = ending;
    final entries = store.ofKind('journal').where((e) =>
      !e.createdAt.isBefore(start) && e.createdAt.isBefore(end)).toList()
        ..sort((a,b) => a.createdAt.compareTo(b.createdAt));
    final label = period == ReviewPeriod.week
        ? '${start.month}月${start.day}日 — ${end.subtract(const Duration(days: 1)).month}月${end.subtract(const Duration(days: 1)).day}日'
        : '${start.year}年${start.month}月';
    return PageFrame(title: '生活回顾',
      subtitle: '仅在本机整理日记，不上传私密正文',
      children: [
        SegmentedButton<ReviewPeriod>(
          segments: const [
            ButtonSegment(value: ReviewPeriod.week, label: Text('每周小结')),
            ButtonSegment(value: ReviewPeriod.month, label: Text('每月回顾')),
          ],
          selected: {period}, onSelectionChanged: (values) =>
            setState(() {period = values.first; anchor = DateTime.now(); includeExcerpts = false; clearDraft();})),
        const SizedBox(height: 12),
        Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
          IconButton(tooltip: '上一期', onPressed: () => shift(-1),
            icon: const Icon(Icons.chevron_left)),
          Text(label, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
          IconButton(tooltip: '下一期', onPressed: () => shift(1),
            icon: const Icon(Icons.chevron_right)),
        ]),
        SoftCard(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text('本期记录 ${entries.length} 篇',
            style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold)),
          const SizedBox(height: 8),
          Text(entries.isEmpty
            ? '这一期还没有日记。可以从一件值得记住的小事开始。'
            : '以下仅依据本机真实记录整理，不推断你的情绪或心理状态。',
            style: const TextStyle(height: 1.5)),
        ])),
        const SectionHeading('本期记录'),
        if (entries.isEmpty)
          const Text('暂时没有可回顾的内容。'),
        ...entries.map((e) => ListTile(
          leading: const Icon(Icons.book_outlined, color: grape),
          title: Text(e.title, maxLines: 2, overflow: TextOverflow.ellipsis),
          subtitle: Text('${e.createdAt.month}月${e.createdAt.day}日'),
          onTap: () => openPage(context, JournalReadPageBridge(id: e.id)),
          trailing: const Icon(Icons.chevron_right))),
        const SectionHeading('写下自己的回顾'),
        SoftCard(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          TextField(controller: highlight, minLines: 1, maxLines: 4,
            decoration: InputDecoration(labelText: period == ReviewPeriod.week
              ? '这周最想记住的一件事' : '这个月最重要的变化')),
          const SizedBox(height: 12),
          TextField(controller: continueDoing, minLines: 1, maxLines: 4,
            decoration: const InputDecoration(labelText: '有什么值得继续保持？')),
          const SizedBox(height: 12),
          TextField(controller: nextStep, minLines: 1, maxLines: 4,
            decoration: const InputDecoration(labelText: '下一期想尝试的一件小事')),
          const SizedBox(height: 12),
          SizedBox(width: double.infinity, child: FilledButton.icon(
            onPressed: saving ? null : () => saveReflection(label),
            icon: const Icon(Icons.lock_outline),
            label: Text(saving ? '加密保存中…' : '加密保存本期回顾'))),
          const SizedBox(height: 8),
          const Text('手动填写的回顾将保存为一篇私密日记，可再次打开阅读。'
            '不会上传到云端，也不会自动替你生成观点。',
            style: TextStyle(fontSize: 12, color: Colors.grey)),
        ])),
        SwitchListTile.adaptive(
          contentPadding: EdgeInsets.zero,
          title: const Text('在本机查看正文摘录'),
          subtitle: const Text('关闭时仅显示标题；正文不会发送到云端'),
          value: includeExcerpts,
          onChanged: entries.isEmpty ? null : (v) => setState(() => includeExcerpts = v)),
        if (includeExcerpts)
          ...entries.map((e) => _PrivateExcerpt(id: e.id, title: e.title)),
        const Padding(padding: EdgeInsets.symmetric(vertical: 12),
          child: Text('说明：这是本地回顾预览，不是 AI 心理分析。'
            '未来如开放云端日记分析，将单独说明服务方并在每次发送前请求授权。',
            style: TextStyle(fontSize: 12, color: Colors.grey))),
      ]);
  }
}

class _PrivateExcerpt extends StatelessWidget {
  const _PrivateExcerpt({required this.id, required this.title});
  final String id, title;
  @override
  Widget build(BuildContext context) {
    return FutureBuilder<String>(
      future: CompassScope.of(context).privateJournalBody(id),
      builder: (context, snapshot) {
        if (snapshot.hasError) {
          return SoftCard(child: Text('无法读取「$title」的本地加密正文'));
        }
        if (!snapshot.hasData) {
          return const Padding(padding: EdgeInsets.all(12),
            child: CircularProgressIndicator());
        }
        final text = snapshot.data!.trim();
        return SoftCard(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(title, style: const TextStyle(fontWeight: FontWeight.bold)),
          const SizedBox(height: 8),
          SelectableText(text.length > 400 ? '${text.substring(0,400)}…' : text),
        ]));
      },
    );
  }
}

/// Small navigation boundary to avoid circular imports through the main shell.
class JournalReadPageBridge extends StatelessWidget {
  const JournalReadPageBridge({super.key, required this.id});
  final String id;
  @override
  Widget build(BuildContext context) {
    final e = CompassScope.of(context).byId(id);
    return PageFrame(title: e?.title ?? '日记',
      children: [_PrivateExcerpt(id: id, title: e?.title ?? '日记')]);
  }
}
