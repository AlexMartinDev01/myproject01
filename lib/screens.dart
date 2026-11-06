import 'dart:io';
import 'dart:async';
import 'dart:convert';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:path/path.dart' as path;
import 'package:path_provider/path_provider.dart';
import 'package:url_launcher/url_launcher.dart';
import 'main.dart';
import 'core/store.dart';
import 'core/ai.dart';
import 'ai_chat_v051.dart';
import 'decision_review_v055.dart';
import 'core/knowledge_candidates.dart';
import 'knowledge_review_v052.dart';
import 'journal_view_v053.dart';
import 'core/local_document_parse.dart';

class WelcomePage extends StatelessWidget {
  const WelcomePage({super.key});
  @override
  Widget build(BuildContext context) => Scaffold(
    body: SafeArea(child: Padding(padding: const EdgeInsets.all(26),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        const Spacer(),
        const Icon(Icons.explore, color: grape, size: 68),
        const SizedBox(height: 28),
        const Text('知行罗盘', style: TextStyle(fontSize: 42, fontWeight: FontWeight.w800, color: ink)),
        const SizedBox(height: 8),
        const Text('把信息变成知识，把知识变成判断。',
          style: TextStyle(fontSize: 18, color: Color(0xFF736576))),
        const SizedBox(height: 32),
        const SoftCard(color: Color(0xFFF4EDF9), child: Column(
          crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text('你的知识，由你掌控', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18)),
            SizedBox(height: 8),
            Text('手机离线保存内容；可选连接自己的 云端 AI 网关。'),
            SizedBox(height: 10),
            Text('私密日记只有每次明确授权，才会发送给 AI 分析。'),
          ])),
        const Spacer(),
        SizedBox(width: double.infinity, child: FilledButton(
          onPressed: () async => CompassScope.of(context).setOnboarded(),
          child: const Text('开始我的知识之旅'))),
        const SizedBox(height: 12),
        const Center(child: Text('无须账号 · 无须付费 AI API · 默认离线可用',
          style: TextStyle(color: Colors.grey, fontSize: 12))),
      ]))),
  );
}

class TodayPage extends StatelessWidget {
  const TodayPage({super.key});
  @override
  Widget build(BuildContext context) {
    final store = CompassScope.of(context);
    final inboxCount = store.ofKind('source').where((e) => e.status == '待处理').length;
    final cards = store.ofKind('card').length;
    final decisions = store.ofKind('decision').length;
    final tasks = store.ofKind('task');
    return PageFrame(title: '知行罗盘', subtitle: '今天的收获，终会成为自己的判断力。',
      actions: [IconButton(onPressed: () => openPage(context, const CapturePage()),
        icon: const Icon(Icons.add_circle_outline))],
      children: [
        SoftCard(color: const Color(0xFFF4EAF6), child: Column(
          crossAxisAlignment: CrossAxisAlignment.start, children: [
            const Row(children: [Icon(Icons.auto_awesome, color: grape), SizedBox(width: 8),
              Text('今日思考', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold))]),
            const SizedBox(height: 14),
            const Text('一个观点让你信服，是因为证据充分，还是因为它恰好符合你的期待？',
              style: TextStyle(fontSize: 19, height: 1.55, color: ink)),
            const SizedBox(height: 12),
            Align(alignment: Alignment.centerRight, child: FilledButton.tonal(
              onPressed: () => openPage(context, const AiChatPage(mode: 'challenge')),
              child: const Text('展开思考 →'))),
          ])),
        const SectionHeading('今天想做什么？'),
        Row(children: [
          Expanded(child: _Shortcut(Icons.edit_note_outlined, '随手记录', '想法与资料',
            () => openPage(context, const CapturePage()))),
          const SizedBox(width: 8),
          Expanded(child: _Shortcut(Icons.auto_awesome_outlined, '问问 AI', '直接开始对话',
            () => openPage(context, const AiChatPage(mode: 'ask')))),
        ]),
        const SizedBox(height: 10),
        SizedBox(width: double.infinity, child: OutlinedButton.icon(
          onPressed: () => openPage(context, const JournalPage()),
          icon: const Icon(Icons.menu_book_outlined), label: const Text('写日记 / 导入已有日记'))),
        SectionHeading('继续上次的事'),
        Row(children: [
          Expanded(child: _Shortcut(Icons.inbox_outlined, '处理收集', '$inboxCount 条',
            () => openPage(context, const InboxPage()))),
          const SizedBox(width: 8),
          Expanded(child: _Shortcut(Icons.auto_stories_outlined, '我的知识', '$cards 张',
            () => openPage(context, const KnowledgePage()))),
          const SizedBox(width: 8),
          Expanded(child: _Shortcut(Icons.balance_outlined, '继续决策', '$decisions 个',
            () => openPage(context, const DecisionPage()))),
        ]),
        SectionHeading('今日待办', action: Text('${tasks.where((e) => e.status == '已完成').length}/${tasks.length}',
          style: const TextStyle(color: grape))),
        if (tasks.isEmpty) SoftCard(child: Column(children: [
          const Text('从一件小事开始，把知识真正用起来。'),
          TextButton(onPressed: () => openPage(context, const LearningPage()),
            child: const Text('创建学习任务'))]))
        else SoftCard(child: Column(children: tasks.take(4).map((task) =>
          CheckboxListTile(contentPadding: EdgeInsets.zero,
            value: task.status == '已完成', title: Text(task.title),
            onChanged: (v) => store.update(task.copyWith(status: v == true ? '已完成' : '进行中')),
          )).toList())),
        SectionHeading('最近收集'),
        ...store.ofKind('source').take(3).map((item) => _EntryTile(entry: item,
          onTap: () => openPage(context, SourceDetailPage(id: item.id)))),
        if (store.ofKind('source').isEmpty) const SoftCard(child: Text('还没有素材。试试收藏一个视频链接或一段读书笔记。')),
        const SoftCard(color: Color(0xFFF8EBF0), child: Text('提醒：收藏只是开始，复述、反驳和真实应用才会让知识留下来。',
          style: TextStyle(color: ink, height: 1.6))),
      ]);
  }
}

class _Shortcut extends StatelessWidget {
  final IconData icon;
  final String title;
  final String subtitle;
  final VoidCallback onTap;
  const _Shortcut(this.icon, this.title, this.subtitle, this.onTap);
  @override
  Widget build(BuildContext context) => InkWell(onTap: onTap, borderRadius: BorderRadius.circular(18),
    child: Container(padding: const EdgeInsets.symmetric(vertical: 17, horizontal: 5),
      decoration: BoxDecoration(color: Colors.white,
        borderRadius: BorderRadius.circular(18), border: Border.all(color: const Color(0xFFECE3F0))),
      child: Column(children: [Icon(icon, size: 29, color: grape), const SizedBox(height: 8),
        Text(title, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
        Text(subtitle, style: const TextStyle(fontSize: 11, color: Colors.grey))])));
}

class _EntryTile extends StatelessWidget {
  final CompassEntry entry;
  final VoidCallback onTap;
  const _EntryTile({required this.entry, required this.onTap});
  @override
  Widget build(BuildContext context) => SoftCard(padding: EdgeInsets.zero,
    child: ListTile(onTap: onTap,
      leading: Icon(entry.kind == 'source' ? Icons.bookmark_border :
        entry.kind == 'card' ? Icons.menu_book_outlined : Icons.article_outlined, color: grape),
      title: Text(entry.title, maxLines: 2, overflow: TextOverflow.ellipsis),
      subtitle: Text('${entry.origin.isEmpty ? entry.kind : entry.origin} · ${entry.status}',
        maxLines: 1, overflow: TextOverflow.ellipsis),
      trailing: const Icon(Icons.chevron_right)));
}

class InboxPage extends StatefulWidget {
  const InboxPage({super.key});
  @override
  State<InboxPage> createState() => _InboxPageState();
}

class _InboxPageState extends State<InboxPage> {
  String filter = '全部';
  @override
  Widget build(BuildContext context) {
    final store = CompassScope.of(context);
    final items = store.ofKind('source').where((e) => filter == '全部' || e.status == filter).toList();
    return PageFrame(title: '收集箱', subtitle: '先收进来，再慢慢把信息变成自己的理解。', children: [
      SoftCard(color: const Color(0xFFF2EAF8), child: Column(children: [
        const Row(children: [Icon(Icons.add_circle_outline, color: grape), SizedBox(width: 8),
          Expanded(child: Text('随手收集', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18)))]),
        const SizedBox(height: 12),
        const Text('抖音 / 小红书 / B站 / YouTube 链接、书名、PDF、文字都可以保存。',
          style: TextStyle(color: Color(0xFF6B5A72))),
        const SizedBox(height: 12),
        SizedBox(width: double.infinity, child: FilledButton.icon(
          onPressed: () => openPage(context, const CapturePage()),
          icon: const Icon(Icons.add), label: const Text('添加一条素材'))),
      ])),
      const SectionHeading('处理状态'),
      SingleChildScrollView(scrollDirection: Axis.horizontal, child: Row(
        children: ['全部', '待处理', '已整理', '需补内容', '失败'].map((f) =>
          Padding(padding: const EdgeInsets.only(right: 8), child: ChoiceChip(
            label: Text(f), selected: filter == f, onSelected: (_) => setState(() => filter = f)))).toList())),
      const SizedBox(height: 15),
      if (items.isEmpty) const SoftCard(child: Text('当前筛选下没有内容。可以先添加一条链接或笔记。')),
      ...items.map((item) => Dismissible(
        key: ValueKey(item.id), direction: DismissDirection.endToStart,
        background: Container(color: const Color(0xFFE8A8B5), alignment: Alignment.centerRight,
          padding: const EdgeInsets.all(16), child: const Icon(Icons.delete, color: Colors.white)),
        confirmDismiss: (_) async => await showDialog<bool>(context: context,
          builder: (c) => AlertDialog(title: const Text('删除这条收集？'),
            content: const Text('删除来源也会删除该来源下的持续笔记和版本历史，操作无法撤销，请先备份。'), actions: [
              TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('取消')),
              TextButton(onPressed: () => Navigator.pop(c, true), child: const Text('删除'))])) ?? false,
        onDismissed: (_) => store.remove(item),
        child: _EntryTile(entry: item, onTap: () => openPage(context, SourceDetailPage(id: item.id))))),
    ]);
  }
}

class CapturePage extends StatefulWidget {
  const CapturePage({super.key});
  @override
  State<CapturePage> createState() => _CapturePageState();
}
class _CapturePageState extends State<CapturePage> {
  final title = TextEditingController();
  final body = TextEditingController();
  final url = TextEditingController();
  String type = '网页/视频链接';
  bool saving = false;
  bool parsing = false;
  String? parseNotice;
  int? parsedPages;
  String? parsedLevel;
  @override
  void dispose() { title.dispose(); body.dispose(); url.dispose(); super.dispose(); }

  Future<void> attachFile() async {
    final result = await FilePicker.platform.pickFiles(type: FileType.custom,
      allowedExtensions: ['pdf', 'txt', 'md', 'docx', 'epub']);
    if (result == null || result.files.isEmpty || !mounted) return;
    final file = result.files.first;
    if (file.path == null) { notice(context, '无法访问选中文件'); return; }
    const maxBytes = 40 * 1024 * 1024;
    if (file.size > maxBytes) { notice(context, '当前仅支持 40 MB 以内的文件'); return; }
    final name = path.basename(file.name);
    final dir = Directory(path.join((await getApplicationDocumentsDirectory()).path, 'imports'));
    await dir.create(recursive: true);
    final dest = File(path.join(dir.path, '${CompassStore.newId()}_${name.replaceAll(RegExp(r'[^a-zA-Z0-9._\-\u4e00-\u9fa5]'), '_')}'));
    await File(file.path!).copy(dest.path);
    if (!mounted) return;
    setState(() {type = '书籍/PDF'; title.text = name;
      url.text = dest.path; parsing = true; parseNotice = '正在手机本机解析文件…';});
    try {
      final extracted = await LocalDocumentParser().parseFile(dest);
      if (!mounted) return;
      setState(() {
        parsedPages = extracted.pageCount;
        parsedLevel = 'locally_extracted';
        parseNotice = extracted.partial ? extracted.warning : '正文已在本机提取并准备保存';
        body.text = body.text.trim().isEmpty
          ? extracted.text : body.text + '\n\n' + extracted.text;
      });
    } catch (e) {
      if (mounted) setState(() {
        parsedLevel = 'metadata_only';
        parseNotice = '正文提取未完成：$e。文件仍已复制保存，可稍后补录或重试。';
      });
    } finally {
      if (mounted) setState(() => parsing = false);
    }
  }

  Future<void> save() async {
    if (saving) return;
    if (title.text.trim().isEmpty && url.text.trim().isEmpty && body.text.trim().isEmpty) {
      notice(context, '至少填写标题、链接或正文中的一项'); return;
    }
    setState(() => saving = true);
    try {
      final store = CompassScope.of(context);
      final raw = url.text.trim();
      final isWeb = raw.startsWith('http://') || raw.startsWith('https://');
      final matches = isWeb ? store.ofKind('source').where((e) => e.origin == raw).toList() : <CompassEntry>[];
      if (matches.isNotEmpty) {
        if (body.text.trim().isNotEmpty) {
          await store.addNote(sourceId: matches.first.id,
            title: title.text.trim().isEmpty ? '新增摘录 / 笔记' : title.text.trim(),
            body: body.text.trim());
          if (mounted) {notice(context, '内容已追加到原来源下，不会覆盖以前的笔记'); Navigator.pop(context);}
        } else if (mounted) {
          notice(context, '这个链接已经收藏；可以在原来源下继续追加笔记');
          Navigator.pop(context);
        }
        return;
      }
      await store.add(kind: 'source', title: title.text.trim().isEmpty ?
          (raw.isEmpty ? body.text.trim().split('\n').first : raw) : title.text.trim(),
        body: body.text.trim(), origin: raw,
        status: body.text.trim().isEmpty ? '需补内容' : '待处理',
        meta: {'source_type': type,
          'content_level': parsedLevel ??
            (body.text.trim().isEmpty ? 'metadata_only' : 'user_supplied'),
          if (parsedPages != null) 'page_count': parsedPages,
          if (parseNotice != null) 'parse_message': parseNotice});
      if (mounted) { notice(context, '已保存到收集箱'); Navigator.pop(context); }
    } catch (e) {
      if (mounted) notice(context, '保存失败：$e');
    } finally {if (mounted) setState(() => saving = false);}
  }

  @override
  Widget build(BuildContext context) => PageFrame(title: '快速收集', subtitle: '只保存真实获取的内容，不依据链接标题编造全文。', children: [
    SoftCard(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      const Text('内容类型', style: TextStyle(fontWeight: FontWeight.bold)),
      DropdownButtonFormField<String>(initialValue: type,
        items: ['网页/视频链接', '书籍/PDF', '读书笔记', '个人想法', '字幕/转录稿']
          .map((x) => DropdownMenuItem(value: x, child: Text(x))).toList(),
        onChanged: (value) => setState(() => type = value ?? type)),
      const SizedBox(height: 12),
      TextField(controller: title, maxLength: 160,
        decoration: const InputDecoration(labelText: '标题（可留空）')),
      TextField(controller: url,
        decoration: const InputDecoration(labelText: '链接、ISBN 或本机文件路径')),
      const SizedBox(height: 12),
      TextField(controller: body, minLines: 5, maxLines: 12,
        decoration: const InputDecoration(labelText: '原文、书摘、字幕或自己的笔记',
          hintText: '仅有链接时先保存，后续可补录真实正文。')),
      const SizedBox(height: 15),
      OutlinedButton.icon(onPressed: saving || parsing ? null : attachFile, icon: const Icon(Icons.upload_file),
        label: const Text('选择 PDF / 文本 / EPUB 文件')),
      if (parseNotice != null) Text(parseNotice!, style: const TextStyle(color: grape)),
      const SizedBox(height: 12),
      SizedBox(width: double.infinity, child: FilledButton(
        onPressed: saving || parsing ? null : save,
        child: Text(saving ? '正在保存…' : '保存到收集箱'))),
    ])),
    const SoftCard(color: Color(0xFFF8ECF1), child: Text('权限提示：平台链接通常只能保存元信息；要分析完整视频，请补充有权使用的字幕或文字。')),
  ]);
}

class SourceDetailPage extends StatefulWidget {
  final String id;
  const SourceDetailPage({super.key, required this.id});
  @override
  State<SourceDetailPage> createState() => _SourceDetailPageState();
}
class _SourceDetailPageState extends State<SourceDetailPage> {
  bool thinking = false;
  String? aiBrief;
  Future<void> summarize(CompassEntry source) async {
    final notes = CompassScope.of(context).notesForSource(source.id);
    if (source.body.trim().isEmpty && notes.isEmpty) {
      openPage(context, SourceMissingPage(sourceId: source.id)); return;
    }
    final evidence = <CompassEntry>[
      if (source.body.trim().isNotEmpty) source,
      ...notes.where((n) => n.body.trim().isNotEmpty).take(10).map((n) => CompassEntry(
        id: 'note-${n.id}', kind: 'note', title: n.title.isEmpty ? source.title : n.title,
        body: n.body, createdAt: n.createdAt, updatedAt: n.updatedAt)),
    ];
    if (evidence.isEmpty) {notice(context, '当前还没有可以整理的笔记正文'); return;}
    setState(() => thinking = true);
    try {
      final result = await AiClient(CompassScope.of(context)).ask(
        '请忠实提炼当前已经提供的来源原文和阅读笔记，明确说明资料可能不完整。',
        mode: 'brief', evidence: evidence);
      if (!mounted) return;
      setState(() => aiBrief = result.answer);
    } catch (e) {
      if (mounted) notice(context, 'AI 整理未完成：$e');
    } finally {if (mounted) setState(() => thinking = false);}
  }
  Future<void> proposeKnowledgePoints(CompassEntry source) async {
    if (thinking) return;
    final store = CompassScope.of(context);
    final notes = store.notesForSource(source.id);
    final material = aiBrief?.trim().isNotEmpty == true ? aiBrief! :
      [source.body,
        ...notes.map((n) => '【${n.chapter.isEmpty ? n.title : n.chapter}】\n${n.body}')]
          .where((text) => text.trim().isNotEmpty).join('\n\n');
    if (material.trim().isEmpty) {
      notice(context, '请先补充来源原文或阅读笔记，不能仅凭标题生成知识点');
      return;
    }
    final approved = await showDialog<bool>(context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('提炼这份材料中的知识点？'),
        content: const Text('会将素材前 6,500 个字符发送到你配置的 AI 网关及模型服务。'
          'AI 只会生成候选知识点，不会自动保存；保存前可以逐条筛选和编辑。'
          '私密日记不属于此操作范围。'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false),
            child: const Text('取消')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true),
            child: const Text('开始提炼')),
        ],
      ),
    );
    if (approved != true || !mounted) return;
    setState(() => thinking = true);
    try {
      final result = await AiClient(store).ask(
        knowledgeExtractionPrompt(material), mode: 'ask');
      final points = parseKnowledgeCandidates(result.answer);
      if (!mounted) return;
      await openPage(context, KnowledgeReviewPage(
        candidates: points, originalSourceId: source.id,
        offeredSourceIds: [source.id], inputWasTruncated: material.length > 6500,
      ));
    } catch (e) {
      if (mounted) notice(context, '知识提炼失败，尚未保存：$e');
    } finally {
      if (mounted) setState(() => thinking = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final store = CompassScope.of(context);
    final source = store.byId(widget.id);
    if (source == null) return const PageFrame(title: '来源不存在', children: [Text('这条记录可能已经删除。')]);
    final full = source.body.trim().isNotEmpty;
    return PageFrame(title: '来源详情', subtitle: source.title, children: [
      SoftCard(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        const Text('来源状态', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
        const SizedBox(height: 8),
        Wrap(spacing: 8, children: [SmallChip(source.status),
          SmallChip(source.meta['content_level'] == 'user_supplied' ? '用户提供正文' : '仅有元信息')]),
        const SizedBox(height: 12),
        Text('来源：${source.origin.isEmpty ? '手动录入' : source.origin}',
          style: const TextStyle(fontSize: 12, color: Colors.grey)),
        if (source.origin.startsWith('https://') || source.origin.startsWith('http://'))
          TextButton.icon(onPressed: () async {
            final uri = Uri.tryParse(source.origin);
            if (uri != null && !await launchUrl(uri, mode: LaunchMode.externalApplication) && context.mounted) {
              notice(context, '无法打开原链接');
            }
          }, icon: const Icon(Icons.open_in_new), label: const Text('打开原始链接')),
      ])),
      NavCard(icon: Icons.edit_note_outlined, title: '编辑来源正文和标题',
        subtitle: '修改会自动记录历史版本，不会覆盖旧版本',
        onTap: () => openPage(context, SourceEditorPage(sourceId: source.id))),
      NavCard(icon: Icons.history, title: '来源版本历史',
        subtitle: '查看原文旧版本，支持恢复',
        onTap: () => openPage(context, SourceHistoryPage(sourceId: source.id))),
      SectionHeading('原文 / 用户提供的材料'),
      SoftCard(child: SelectableText(full ? source.body : '当前仅保存了链接或书目信息，尚无可分析的完整正文。',
        style: const TextStyle(height: 1.65))),
      if (LocalDocumentParser.extension(source.origin).isNotEmpty && !full)
        NavCard(icon: Icons.text_snippet_outlined, title: '在手机本机重新解析文件',
          subtitle: 'PDF / DOCX / TXT / MD / EPUB · 不会上传私人文件',
          onTap: () async {
            try {
              final extracted = await LocalDocumentParser().parseFile(File(source.origin));
              await store.update(source.copyWith(body: extracted.text,
                status: '待处理',
                meta: {...source.meta, 'content_level': 'locally_extracted',
                  'page_count': extracted.pageCount,
                  'partial': extracted.partial}));
              if (context.mounted) notice(context,
                extracted.partial ? extracted.warning : '文件正文已提取并建立本地检索索引');
            } catch (e) {
              if (context.mounted) notice(context, '本机解析失败：$e');
            }
          }),
      if (!full) NavCard(icon: Icons.edit_note, title: '补录字幕或原文',
        subtitle: '补全材料后才可做基于正文的 AI 摘要',
        onTap: () => openPage(context, SourceMissingPage(sourceId: source.id))),
      const SectionHeading('我的持续笔记'),
      NavCard(icon: Icons.note_add_outlined, title: '追加一条读书 / 视频笔记',
        subtitle: '可按章节、日期不断添加，不覆盖原来的记录',
        onTap: () => openPage(context, SourceNoteEditorPage(sourceId: source.id))),
      if (store.notesForSource(source.id).isEmpty)
        const SoftCard(child: Text('还没有追加笔记，可以边读边记录，所有内容都会保存在这个来源下。')),
      ...store.notesForSource(source.id).map((n) => NavCard(
        icon: Icons.sticky_note_2_outlined,
        title: n.title.isEmpty ? '阅读笔记 · ${n.createdAt.month}/${n.createdAt.day}' : n.title,
        subtitle: '${n.chapter.isEmpty ? '随手记录' : n.chapter} · 第 ${n.revision} 版 · ${n.body.length > 40 ? '${n.body.substring(0, 40)}…' : n.body}',
        onTap: () => openPage(context, SourceNoteEditorPage(sourceId: source.id, noteId: n.id)))),
      const SectionHeading('AI 提炼'),
      if (aiBrief != null) SoftCard(color: const Color(0xFFF1E9F6), child: MessageMarkdown(data: aiBrief!)),
      SoftCard(child: Column(children: [
        SizedBox(width: double.infinity, child: FilledButton.icon(
          onPressed: thinking ? null : () => summarize(source),
          icon: const Icon(Icons.auto_awesome),
          label: Text(thinking ? 'AI 正在整理…' : 'AI 提炼关键内容'))),
        const SizedBox(height: 8),
        SizedBox(width: double.infinity, child: OutlinedButton.icon(
          onPressed: thinking ? null : () => proposeKnowledgePoints(source),
          icon: const Icon(Icons.fact_check_outlined),
          label: const Text('提炼多个知识点 · 勾选后保存'))),
        SizedBox(width: double.infinity, child: TextButton.icon(
          onPressed: () => openPage(context, CardEditPage(
            originalSourceId: source.id)),
          icon: const Icon(Icons.edit_note),
          label: const Text('我自己新建一张空白知识卡'))),
        SizedBox(width: double.infinity, child: TextButton(
          onPressed: () async {
            await store.update(source.copyWith(status: '已整理'));
            if (context.mounted) notice(context, '已标记为整理完成');
          }, child: const Text('标记为已整理'))),
      ])),
    ]);
  }
}

/// Edit the original material without conflating it with personal reading notes.
class SourceEditorPage extends StatefulWidget {
  final String sourceId;
  const SourceEditorPage({super.key, required this.sourceId});
  @override
  State<SourceEditorPage> createState() => _SourceEditorPageState();
}
class _SourceEditorPageState extends State<SourceEditorPage> {
  final title = TextEditingController();
  final body = TextEditingController();
  bool busy = false;
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final e = CompassScope.of(context).byId(widget.sourceId);
      if (e != null) { title.text = e.title; body.text = e.body; }
    });
  }
  @override
  void dispose() { title.dispose(); body.dispose(); super.dispose(); }
  Future<void> save() async {
    if (busy) return;
    if (title.text.trim().isEmpty) { notice(context, '请输入标题'); return; }
    final store = CompassScope.of(context);
    final entry = store.byId(widget.sourceId);
    if (entry == null || entry.kind != 'source') { notice(context, '来源不存在'); return; }
    setState(() => busy = true);
    try {
      await store.update(entry.copyWith(title: title.text.trim(), body: body.text,
        status: body.text.trim().isEmpty ? '需补内容' : entry.status,
        meta: {...entry.meta, if (body.text != entry.body)
          'content_level': body.text.trim().isEmpty ? 'metadata_only' : 'user_supplied'}));
      if (mounted) { notice(context, '保存成功，已保留原文版本历史'); Navigator.pop(context); }
    } catch (e) { if (mounted) notice(context, '保存失败：$e'); }
    finally { if (mounted) setState(() => busy = false); }
  }
  @override
  Widget build(BuildContext context) => PageFrame(title: '编辑来源材料',
    subtitle: '来源原文和个人笔记分开管理。每次修改都会保留旧版本。', children: [
    TextField(controller: title, decoration: const InputDecoration(labelText: '标题')),
    const SizedBox(height: 12),
    TextField(controller: body, minLines: 12, maxLines: 24,
      decoration: const InputDecoration(labelText: '已授权的原文、摘录、字幕')),
    const SizedBox(height: 12),
    FilledButton.icon(onPressed: busy ? null : save,
      icon: const Icon(Icons.save_outlined), label: Text(busy ? '保存中…' : '保存并记录版本')),
  ]);
}

class SourceNoteEditorPage extends StatefulWidget {
  final String sourceId;
  final String? noteId;
  const SourceNoteEditorPage({super.key, required this.sourceId, this.noteId});
  @override
  State<SourceNoteEditorPage> createState() => _SourceNoteEditorPageState();
}
class _SourceNoteEditorPageState extends State<SourceNoteEditorPage> {
  final title = TextEditingController();
  final chapter = TextEditingController();
  final body = TextEditingController();
  bool busy = false;
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || widget.noteId == null) return;
      final found = CompassScope.of(context).notes.where((n) => n.id == widget.noteId).toList();
      if (found.isNotEmpty) {
        title.text = found.first.title;
        chapter.text = found.first.chapter;
        body.text = found.first.body;
      }
    });
  }
  @override
  void dispose() { title.dispose(); chapter.dispose(); body.dispose(); super.dispose(); }
  Future<void> save() async {
    if (busy) return;
    if (body.text.trim().isEmpty && title.text.trim().isEmpty) {
      notice(context, '至少写一条标题或笔记内容'); return;
    }
    final store = CompassScope.of(context);
    setState(() => busy = true);
    try {
      if (widget.noteId == null) {
        await store.addNote(sourceId: widget.sourceId, title: title.text,
          body: body.text, chapter: chapter.text);
      } else {
        final current = store.notes.where((n) => n.id == widget.noteId).toList();
        if (current.isEmpty) throw StateError('笔记已删除');
        await store.updateNote(current.first, title: title.text,
          body: body.text, chapter: chapter.text);
      }
      if (mounted) { notice(context, '已保存笔记和版本历史'); Navigator.pop(context); }
    } catch (e) { if (mounted) notice(context, '保存失败：$e'); }
    finally { if (mounted) setState(() => busy = false); }
  }
  @override
  Widget build(BuildContext context) {
    final note = CompassScope.of(context).notes.where((n) => n.id == widget.noteId).toList();
    return PageFrame(title: widget.noteId == null ? '追加笔记' : '编辑阅读笔记',
      subtitle: '同一来源可无限次追加。历史版本不会因编辑而消失。', children: [
      TextField(controller: title, maxLength: 160,
        decoration: const InputDecoration(labelText: '本条笔记标题（可选）')),
      const SizedBox(height: 10),
      TextField(controller: chapter,
        decoration: const InputDecoration(labelText: '章节 / 页码 / 时间码（可选）')),
      const SizedBox(height: 10),
      TextField(controller: body, minLines: 10, maxLines: 22,
        decoration: const InputDecoration(labelText: '我的摘录、思考与质疑')),
      const SizedBox(height: 12),
      FilledButton.icon(onPressed: busy ? null : save,
        icon: const Icon(Icons.save_outlined), label: Text(busy ? '正在保存…' : '保存笔记')),
      if (note.isNotEmpty) ...[
        NavCard(icon: Icons.history, title: '查看与恢复编辑历史',
          subtitle: '当前第 ${note.first.revision} 版',
          onTap: () async {
            await openPage(context, NoteHistoryPage(noteId: note.first.id));
            if (!mounted) return;
            final fresh = CompassScope.of(context).notes.where((n) => n.id == note.first.id).toList();
            if (fresh.isNotEmpty) {
              setState(() { title.text = fresh.first.title; chapter.text = fresh.first.chapter;
                body.text = fresh.first.body; });
            }
          }),
        OutlinedButton.icon(onPressed: () async {
          final yes = await showDialog<bool>(context: context,
            builder: (ctx) => AlertDialog(title: const Text('删除这条笔记？'),
              content: const Text('删除后不会再出现在来源笔记列表里。'),
              actions: [TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('取消')),
                FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('删除'))]));
          if (yes != true || !context.mounted) return;
          await CompassScope.of(context).removeNote(note.first);
          if (context.mounted) Navigator.pop(context);
        }, icon: const Icon(Icons.delete_outline), label: const Text('删除笔记')),
      ],
    ]);
  }
}

class NoteHistoryPage extends StatelessWidget {
  final String noteId;
  const NoteHistoryPage({super.key, required this.noteId});
  @override
  Widget build(BuildContext context) {
    final store = CompassScope.of(context);
    return PageFrame(title: '笔记版本历史', subtitle: '点击历史版本可查看并恢复，不覆盖当前版本。', children: [
      FutureBuilder<List<Map<String, Object?>>>(
        future: store.noteHistory(noteId),
        builder: (context, snapshot) {
          if (!snapshot.hasData) return const Center(child: CircularProgressIndicator());
          return Column(children: snapshot.data!.map((row) => NavCard(
            icon: Icons.history,
            title: '第 ${row['revision']} 版 · ${row['title']}',
            subtitle: '${row['saved_at']} · ${(row['body'] as String).length > 50 ? (row['body'] as String).substring(0, 50) : row['body']}',
            onTap: () async {
              final ok = await showDialog<bool>(context: context,
                builder: (ctx) => AlertDialog(title: const Text('恢复此历史版本？'),
                  content: SelectableText((row['body'] as String).length > 1200 ?
                    '${(row['body'] as String).substring(0, 1200)}…' : row['body'] as String),
                  actions: [TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('取消')),
                    FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('恢复为新版'))]));
              if (ok != true || !context.mounted) return;
              final notes = store.notes.where((n) => n.id == noteId).toList();
              if (notes.isEmpty) { notice(context, '笔记已不存在'); return; }
              try {
                await store.restoreNote(notes.first, row['revision'] as int);
                if (context.mounted) { notice(context, '已恢复，旧版本仍保留'); Navigator.pop(context); }
              } catch (e) { if (context.mounted) notice(context, '恢复失败：$e'); }
            })).toList());
        }),
    ]);
  }
}

class SourceHistoryPage extends StatelessWidget {
  final String sourceId;
  const SourceHistoryPage({super.key, required this.sourceId});
  @override
  Widget build(BuildContext context) {
    final store = CompassScope.of(context);
    return PageFrame(title: '原始来源版本历史', children: [
      FutureBuilder<List<Map<String, Object?>>>(future: store.sourceVersions(sourceId),
        builder: (context, snapshot) {
          if (!snapshot.hasData) return const Center(child: CircularProgressIndicator());
          return Column(children: snapshot.data!.map((row) => NavCard(
            icon: Icons.history,
            title: '${row['title']}', subtitle: '保存于 ${row['created_at']}',
            onTap: () async {
              final ok = await showDialog<bool>(context: context,
                builder: (ctx) => AlertDialog(title: const Text('恢复此来源版本？'),
                  content: SelectableText((row['body'] as String).length > 1500 ?
                    '${(row['body'] as String).substring(0, 1500)}…' : row['body'] as String),
                  actions: [TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('取消')),
                    FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('恢复'))]));
              if (ok != true || !context.mounted) return;
              final source = store.byId(sourceId);
              if (source == null) return;
              try {
                await store.restoreSourceVersion(source, row['id'] as String);
                if (context.mounted) { notice(context, '来源已恢复到指定版本'); Navigator.pop(context); }
              } catch (e) { if (context.mounted) notice(context, '恢复失败：$e'); }
            })).toList());
        }),
    ]);
  }
}

class SourceMissingPage extends StatefulWidget {
  final String sourceId;
  const SourceMissingPage({super.key, required this.sourceId});
  @override
  State<SourceMissingPage> createState() => _SourceMissingPageState();
}
class _SourceMissingPageState extends State<SourceMissingPage> {
  final controller = TextEditingController();
  @override
  void dispose() {controller.dispose(); super.dispose();}
  @override
  Widget build(BuildContext context) {
    final store = CompassScope.of(context);
    final item = store.byId(widget.sourceId);
    return PageFrame(title: '补充真实内容', subtitle: '内容暂时不足', children: [
      const SoftCard(color: Color(0xFFFFF1E5), child: Column(children: [
        Icon(Icons.info_outline, color: Color(0xFFB16D45), size: 35),
        SizedBox(height: 8),
        Text('当前仅获取到链接或元信息，不能据此生成完整视频或书籍摘要。',
          textAlign: TextAlign.center),
      ])),
      SoftCard(child: Text('来源：${item?.title ?? '记录已删除'}')),
      TextField(controller: controller, minLines: 8, maxLines: 15,
        decoration: const InputDecoration(labelText: '粘贴你有权使用的字幕、正文或笔记')),
      const SizedBox(height: 16),
      FilledButton(onPressed: item == null ? null : () async {
        if (controller.text.trim().isEmpty) {notice(context, '请先填写内容'); return;}
        await store.update(item.copyWith(body: controller.text.trim(), status: '待处理',
          meta: {...item.meta, 'content_level': 'user_supplied'}));
        if (context.mounted) {notice(context, '正文已补充'); Navigator.pop(context);}
      }, child: const Text('保存补录内容')),
    ]);
  }
}

class KnowledgePage extends StatefulWidget {
  const KnowledgePage({super.key});
  @override
  State<KnowledgePage> createState() => _KnowledgePageState();
}
class _KnowledgePageState extends State<KnowledgePage> {
  final query = TextEditingController();
  String theme = '全部';
  @override
  void dispose() {query.dispose(); super.dispose();}
  @override
  Widget build(BuildContext context) {
    final store = CompassScope.of(context);
    final cards = store.ofKind('card').where((e) =>
      (theme == '全部' || e.meta['topic'] == theme) &&
      ('${e.title}${e.body}'.toLowerCase().contains(query.text.trim().toLowerCase()))).toList();
    return PageFrame(title: '知识库', subtitle: '我的知识体系 · 累积、联结、重新理解',
      actions: [IconButton(onPressed: () => openPage(context, const CardEditPage()),
        icon: const Icon(Icons.add))], children: [
        TextField(controller: query, onChanged: (_) => setState(() {}),
          decoration: const InputDecoration(prefixIcon: Icon(Icons.search),
            hintText: '检索概念、观点、知识卡…')),
        Row(children: [
          Expanded(child: OutlinedButton.icon(
            onPressed: () => openPage(context, const CapturePage()),
            icon: const Icon(Icons.add_link), label: const Text('收集资料'))),
          const SizedBox(width: 8),
          Expanded(child: OutlinedButton.icon(
            onPressed: () => openPage(context, const CardEditPage()),
            icon: const Icon(Icons.note_add_outlined), label: const Text('新建知识卡'))),
        ]),
        const SectionHeading('按主题筛选'),
        Wrap(spacing: 8, runSpacing: 8,
          children: ['全部', '心理学', '哲学', '历史', '社会学', '科技', '职业发展', '自我成长']
            .map((s) => ChoiceChip(label: Text(s), selected: theme == s,
              onSelected: (_) => setState(() => theme = s))).toList()),
        SectionHeading('我的知识卡 · ${cards.length}'),
        if (cards.isEmpty) const SoftCard(child: Text('这里还没有符合条件的知识卡。整理一条材料并加入知识库吧。')),
        ...cards.map((item) => _EntryTile(entry: item,
          onTap: () => openPage(context, KnowledgeDetailPage(id: item.id)))),
        const SizedBox(height: 8),
        OutlinedButton.icon(onPressed: () => openPage(context, const CardEditPage()),
          icon: const Icon(Icons.add), label: const Text('新增知识卡')),
      ]);
  }
}

class CardEditPage extends StatefulWidget {
  final String? id;
  final String? originalSourceId;
  final String initialTitle;
  final String initialBody;
  const CardEditPage({super.key, this.id, this.originalSourceId,
    this.initialTitle = '', this.initialBody = ''});
  @override
  State<CardEditPage> createState() => _CardEditPageState();
}
class _CardEditPageState extends State<CardEditPage> {
  late final TextEditingController title;
  late final TextEditingController explanation;
  late final TextEditingController conditions;
  late final TextEditingController counter;
  late final TextEditingController application;
  String topic = '自我成长';
  @override
  void initState() {
    super.initState();
    title = TextEditingController(text: widget.initialTitle);
    explanation = TextEditingController(text: widget.initialBody);
    conditions = TextEditingController();
    counter = TextEditingController(); application = TextEditingController();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || widget.id == null) return;
      final e = CompassScope.of(context).byId(widget.id!);
      if (e != null) setState(() {
        title.text = e.title;
        explanation.text = e.body;
        conditions.text = e.meta['conditions']?.toString() ?? '';
        counter.text = e.meta['counter']?.toString() ?? '';
        application.text = e.meta['application']?.toString() ?? '';
        topic = e.meta['topic']?.toString() ?? topic;
      });
    });
  }
  @override
  void dispose() {
    title.dispose(); explanation.dispose(); conditions.dispose();
    counter.dispose(); application.dispose();
    super.dispose();
  }
  @override
  Widget build(BuildContext context) => PageFrame(title: widget.id == null ? '创建知识卡' : '编辑知识卡',
    subtitle: '写下自己的理解，而不是仅保存 AI 的结论。', children: [
      TextField(controller: title, maxLength: 160,
        decoration: const InputDecoration(labelText: '知识卡标题 *')),
      const SizedBox(height: 10),
      DropdownButtonFormField<String>(initialValue: topic,
        items: ['心理学', '哲学', '历史', '社会学', '科技', '职业发展', '自我成长']
          .map((e) => DropdownMenuItem(value: e, child: Text(e))).toList(),
        onChanged: (v) => setState(() => topic = v ?? topic),
        decoration: const InputDecoration(labelText: '归入专题')),
      const SizedBox(height: 10),
      TextField(controller: explanation, minLines: 5, maxLines: 12,
        decoration: const InputDecoration(labelText: '核心概念与我的解释 *')),
      const SizedBox(height: 10),
      TextField(controller: conditions, minLines: 2, maxLines: 5,
        decoration: const InputDecoration(labelText: '适用条件与局限')),
      const SizedBox(height: 10),
      TextField(controller: counter, minLines: 2, maxLines: 5,
        decoration: const InputDecoration(labelText: '反例与适用边界')),
      const SizedBox(height: 10),
      TextField(controller: application, minLines: 2, maxLines: 5,
        decoration: const InputDecoration(labelText: '我准备怎样应用')),
      const SizedBox(height: 15),
      FilledButton(onPressed: () async {
        if (title.text.trim().isEmpty || explanation.text.trim().isEmpty) {
          notice(context, '标题和自己的解释不能为空'); return;
        }
        final store = CompassScope.of(context);
        final meta = {'topic': topic, 'conditions': conditions.text.trim(),
          'counter': counter.text.trim(),
          'application': application.text.trim(),
          if (widget.originalSourceId != null) 'source_id': widget.originalSourceId};
        if (widget.id == null) {
          await store.add(kind: 'card', title: title.text.trim(),
            body: explanation.text.trim(), status: '待验证', meta: meta);
        } else {
          final existing = store.byId(widget.id!);
          if (existing != null) await store.update(existing.copyWith(title: title.text.trim(),
            body: explanation.text.trim(), meta: {...existing.meta, ...meta}));
        }
        if (context.mounted) {notice(context, '知识卡已保存'); Navigator.pop(context);}
      }, child: const Text('保存知识卡')),
    ]);
}

class KnowledgeDetailPage extends StatelessWidget {
  final String id;
  const KnowledgeDetailPage({super.key, required this.id});
  @override
  Widget build(BuildContext context) {
    final store = CompassScope.of(context);
    final entry = store.byId(id);
    if (entry == null) return const PageFrame(title: '内容不存在', children: [Text('知识卡已删除。')]);
    return PageFrame(title: '知识卡', subtitle: entry.title,
      actions: [IconButton(onPressed: () => openPage(context, CardEditPage(id: id)),
        icon: const Icon(Icons.edit_outlined))], children: [
      SoftCard(color: const Color(0xFFF2ECF8), child: Column(
        crossAxisAlignment: CrossAxisAlignment.start, children: [
        Wrap(spacing: 8, children: [SmallChip('${entry.meta['topic'] ?? '未分类'}'), SmallChip(entry.status)]),
        const SizedBox(height: 14),
        Text(entry.body, style: const TextStyle(fontSize: 16, height: 1.65)),
      ])),
      if (entry.meta['conditions']?.toString().isNotEmpty == true) ...[
         const SectionHeading('适用条件'),
         SoftCard(child: Text(entry.meta['conditions'].toString())),
       ],
       const SectionHeading('反例与边界'),
      SoftCard(child: Text(entry.meta['counter']?.toString().isNotEmpty == true ?
        entry.meta['counter'].toString() : '还没有补充反例。')),
      const SectionHeading('应用到现实'),
      SoftCard(child: Text(entry.meta['application']?.toString().isNotEmpty == true ?
        entry.meta['application'].toString() : '还没有留下真实应用。')),
      if (entry.meta['source_id'] is String)
        NavCard(icon: Icons.source_outlined, title: '查看原始来源',
          onTap: () => openPage(context, SourceDetailPage(id: entry.meta['source_id'] as String))),
      SoftCard(child: Column(children: [
        SizedBox(width: double.infinity, child: FilledButton.tonal(
          onPressed: () => openPage(context, AiChatPage(mode: 'learn', selectedEvidence: [entry])),
          child: const Text('用 AI 考考我'))),
        TextButton(onPressed: () => store.update(entry.copyWith(status: '已掌握')),
          child: const Text('标记为已掌握')),
        TextButton(onPressed: () async {
          final yes = await showDialog<bool>(context: context,
            builder: (c) => AlertDialog(title: const Text('删除知识卡？'), actions: [
              TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('取消')),
              TextButton(onPressed: () => Navigator.pop(c, true), child: const Text('删除'))]));
          if (yes == true) {await store.remove(entry); if (context.mounted) Navigator.pop(context);}
        }, child: const Text('删除知识卡', style: TextStyle(color: Colors.red))),
      ])),
    ]);
  }
}

class AiHubPage extends StatelessWidget {
  const AiHubPage({super.key});
  @override
  Widget build(BuildContext context) => PageFrame(title: 'AI 思考',
    subtitle: '有证据地学习，必要时挑战自己的观点。', children: [
      const SoftCard(color: Color(0xFFF2E8F8), child: Column(
        crossAxisAlignment: CrossAxisAlignment.start, children: [
        Icon(Icons.auto_awesome, color: grape, size: 34),
        SizedBox(height: 12),
        Text('你的 AI 知识伙伴', style: TextStyle(fontSize: 23, fontWeight: FontWeight.w700, color: ink)),
        SizedBox(height: 8),
        Text('基于你允许使用的知识材料回答，也会提出不同的观点。没有依据时会说明不知道。',
          style: TextStyle(height: 1.6)),
      ])),
      SizedBox(width: double.infinity, child: FilledButton.icon(
        onPressed: () => openPage(context, const AiChatPage(mode: 'ask')),
        icon: const Icon(Icons.chat_bubble_outline), label: const Text('开始普通 AI 对话'))),
      const SizedBox(height: 12),
      const SectionHeading('其他思考方式'),
      NavCard(icon: Icons.history, title: '历史对话',
        subtitle: '本机加密保存 · 重命名、搜索和继续追问',
        onTap: () => openPage(context, const ChatHistoryPage())),
      NavCard(icon: Icons.school_outlined, title: '费曼学习教练',
        subtitle: '解释、复述、反例、主动回忆', onTap: () => openPage(context, const AiChatPage(mode: 'learn'))),
      NavCard(icon: Icons.forum_outlined, title: '反方思辨',
        subtitle: '识别论证漏洞与忽略的前提', onTap: () => openPage(context, const AiChatPage(mode: 'challenge'))),
      NavCard(icon: Icons.balance, title: '决策实验室',
        subtitle: '选项、证据、权衡与复盘', onTap: () => openPage(context, const DecisionPage())),
      NavCard(icon: Icons.settings_outlined, title: '连接 AI 工作站',
        subtitle: '设置 DeepSeek 私有代理或 Windows 节点',
        onTap: () => openPage(context, const DeviceSettingsPage())),
    ]);
}

class DecisionPage extends StatelessWidget {
  const DecisionPage({super.key});
  @override
  Widget build(BuildContext context) {
    final decisions = CompassScope.of(context).ofKind('decision');
    return PageFrame(title: '决策室', subtitle: '看清事实、价值和机会成本，最后由自己决定。', children: [
      const SoftCard(color: Color(0xFFF4EDF9), child: Column(
        crossAxisAlignment: CrossAxisAlignment.start, children: [
        Icon(Icons.balance, color: grape, size: 34), SizedBox(height: 10),
        Text('做选择，不只靠感觉', style: TextStyle(fontSize: 19, fontWeight: FontWeight.bold)),
        SizedBox(height: 6),
        Text('目标澄清 → 方案比较 → 反方观点 → 小实验 → 复盘',
          style: TextStyle(height: 1.6)),
      ])),
      SizedBox(width: double.infinity, child: FilledButton.icon(
        onPressed: () => openPage(context, const DecisionEditPage()),
        icon: const Icon(Icons.add), label: const Text('发起一个新决策'))),
      const SectionHeading('我的决策记录'),
      if (decisions.isEmpty) const SoftCard(child: Text('还没有决策记录。可以从一个最近反复犹豫的问题开始。')),
      ...decisions.map((e) => NavCard(icon: Icons.tune_outlined, title: e.title,
        subtitle: '${e.status} · ${e.meta['goal'] ?? ''}',
        onTap: () => openPage(context, DecisionReviewPage(id: e.id)))),
    ]);
  }
}

class DecisionEditPage extends StatefulWidget {
  const DecisionEditPage({super.key});
  @override
  State<DecisionEditPage> createState() => _DecisionEditPageState();
}
class _DecisionEditPageState extends State<DecisionEditPage> {
  final question = TextEditingController();
  final goal = TextEditingController();
  final optionA = TextEditingController();
  final optionB = TextEditingController();
  final constraints = TextEditingController();
  double growth = 35;
  double time = 35;
  double risk = 30;
  @override
  void dispose() {
    for (final c in [question, goal, optionA, optionB, constraints]) {c.dispose();}
    super.dispose();
  }
  @override
  Widget build(BuildContext context) => PageFrame(title: '新建决策',
    subtitle: '先列出目标与不同选择，不急于让 AI 给唯一答案。', children: [
      TextField(controller: question, maxLength: 200,
        decoration: const InputDecoration(labelText: '我面临的问题 *')),
      const SizedBox(height: 12),
      TextField(controller: goal, maxLines: 3,
        decoration: const InputDecoration(labelText: '这次决定最重要的目标 *')),
      const SizedBox(height: 12),
      TextField(controller: constraints, maxLines: 3,
        decoration: const InputDecoration(labelText: '现实约束：时间 / 金钱 / 精力等')),
      const SectionHeading('备选方案'),
      TextField(controller: optionA, decoration: const InputDecoration(labelText: '方案 A *')),
      const SizedBox(height: 10),
      TextField(controller: optionB, decoration: const InputDecoration(labelText: '方案 B *')),
      const SectionHeading('比较维度（总权重 100%）'),
      _WeightSlider(label: '成长价值', value: growth,
        onChanged: (v) => setState(() => growth = v)),
      _WeightSlider(label: '时间投入', value: time,
        onChanged: (v) => setState(() => time = v)),
      _WeightSlider(label: '风险控制', value: risk,
        onChanged: (v) => setState(() => risk = v)),
      SoftCard(color: (growth + time + risk) == 100 ? const Color(0xFFF1ECF7) : const Color(0xFFFFEEE8),
        child: Text('当前权重：${(growth + time + risk).toInt()}%（必须等于 100%）')),
      FilledButton(onPressed: () async {
        if ([question.text, goal.text, optionA.text, optionB.text].any((x) => x.trim().isEmpty)) {
          notice(context, '请填写问题、目标和两个方案'); return;
        }
        if ((growth + time + risk).toInt() != 100) {
          notice(context, '维度权重总和必须是 100%'); return;
        }
        final entry = await CompassScope.of(context).add(kind: 'decision',
          title: question.text.trim(), body: constraints.text.trim(), status: '待审议', meta: {
            'goal': goal.text.trim(), 'options': [optionA.text.trim(), optionB.text.trim()],
            'criteria': {'成长价值': growth, '时间投入': time, '风险控制': risk},
            'prediction': '', 'observed': '', 'next_action': '',
          });
        if (context.mounted) {
          Navigator.pop(context);
          openPage(context, DecisionReviewPage(id: entry.id));
        }
      }, child: const Text('保存决策草稿')),
    ]);
}

class _WeightSlider extends StatelessWidget {
  final String label;
  final double value;
  final ValueChanged<double> onChanged;
  const _WeightSlider({required this.label, required this.value, required this.onChanged});
  @override
  Widget build(BuildContext context) => SoftCard(child: Column(children: [
    Row(children: [Expanded(child: Text(label)), Text('${value.toInt()}%')]),
    Slider(value: value, min: 0, max: 100, divisions: 20, onChanged: onChanged),
  ]));
}

class LearningPage extends StatefulWidget {
  const LearningPage({super.key});
  @override
  State<LearningPage> createState() => _LearningPageState();
}
class _LearningPageState extends State<LearningPage> {
  Future<void> createTopic() async {
    final c = TextEditingController();
    final title = await showDialog<String>(context: context, builder: (ctx) => AlertDialog(
      title: const Text('创建一个学习主题'),
      content: TextField(controller: c, autofocus: true, maxLength: 120,
        decoration: const InputDecoration(hintText: '如：哲学的认识论入门')),
      actions: [TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('取消')),
        FilledButton(onPressed: () => Navigator.pop(ctx, c.text.trim()), child: const Text('创建'))]));
    c.dispose();
    if (!mounted || title == null || title.isEmpty) return;
    await CompassScope.of(context).add(kind: 'topic', title: title, status: '学习中');
  }
  Future<void> createTask() async {
    final c = TextEditingController();
    final label = await showDialog<String>(context: context, builder: (ctx) => AlertDialog(
      title: const Text('新增一项具体任务'),
      content: TextField(controller: c, autofocus: true, maxLength: 160,
        decoration: const InputDecoration(hintText: '如：阅读一章并写下自己的反例')),
      actions: [TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('取消')),
        FilledButton(onPressed: () => Navigator.pop(ctx, c.text.trim()), child: const Text('创建'))]));
    c.dispose();
    if (!mounted || label == null || label.isEmpty) return;
    await CompassScope.of(context).add(kind: 'task', title: label, status: '进行中');
  }
  @override
  Widget build(BuildContext context) {
    final store = CompassScope.of(context);
    final topics = store.ofKind('topic'); final tasks = store.ofKind('task');
    final complete = tasks.where((e) => e.status == '已完成').length;
    return PageFrame(title: '成长与学习', subtitle: '一次认真理解，比十次匆忙收藏更重要。', children: [
      SoftCard(color: const Color(0xFFF1EAF8), child: Column(
        crossAxisAlignment: CrossAxisAlignment.start, children: [
          const Text('本周的学习进度', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
          const SizedBox(height: 12),
          LinearProgressIndicator(value: tasks.isEmpty ? 0 : complete / tasks.length,
            minHeight: 10, borderRadius: BorderRadius.circular(10)),
          const SizedBox(height: 10),
          Text('已完成 $complete / ${tasks.length} 项任务'),
        ])),
      SectionHeading('学习专题（${topics.length}）'),
      ...topics.map((topic) => _EntryTile(entry: topic,
        onTap: () async {
          final ok = await showDialog<bool>(context: context,
            builder: (c) => AlertDialog(title: Text(topic.title),
              content: const Text('要将这个学习主题标记为已完成吗？'), actions: [
                TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('返回')),
                FilledButton(onPressed: () => Navigator.pop(c, true), child: const Text('完成主题'))]));
          if (ok == true) store.update(topic.copyWith(status: '已完成'));
        })),
      OutlinedButton.icon(onPressed: createTopic, icon: const Icon(Icons.add),
        label: const Text('添加学习主题')),
      SectionHeading('具体行动（${tasks.length}）'),
      if (tasks.isEmpty) const SoftCard(child: Text('你可以为今天添加一项 15 分钟内能完成的小任务。')),
      ...tasks.map((task) => SoftCard(padding: EdgeInsets.zero,
        child: CheckboxListTile(title: Text(task.title),
          subtitle: Text(task.status), value: task.status == '已完成',
          secondary: IconButton(icon: const Icon(Icons.delete_outline, color: Colors.grey),
            onPressed: () async {
              final yes = await showDialog<bool>(context: context,
                builder: (c) => AlertDialog(title: const Text('删除任务？'), actions: [
                  TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('取消')),
                  TextButton(onPressed: () => Navigator.pop(c, true), child: const Text('删除'))]));
              if (yes == true) store.remove(task);
            }),
          onChanged: (value) => store.update(task.copyWith(status: value == true ? '已完成' : '进行中'))))),
      OutlinedButton.icon(onPressed: createTask, icon: const Icon(Icons.add_task),
        label: const Text('添加行动任务')),
      const SectionHeading('主动复述'),
      NavCard(icon: Icons.record_voice_over_outlined, title: '请 AI 考考我',
        subtitle: '解释一个概念，再自己说出来',
        onTap: () => openPage(context, const AiChatPage(mode: 'learn'))),
    ]);
  }
}

/// Journal browser: titles and dates only; private bodies are not indexed.
class JournalPage extends StatefulWidget {
  const JournalPage({super.key});
  @override
  State<JournalPage> createState() => _JournalPageState();
}
class _JournalPageState extends State<JournalPage> {
  DateTime month = DateTime(DateTime.now().year, DateTime.now().month);
  final search = TextEditingController();
  @override
  void dispose() { search.dispose(); super.dispose(); }
  @override
  Widget build(BuildContext context) {
    final entries = CompassScope.of(context).ofKind('journal').where((e) =>
      e.createdAt.year == month.year && e.createdAt.month == month.month &&
      e.title.toLowerCase().contains(search.text.toLowerCase())).toList();
    return PageFrame(title: '我的日记', subtitle: '写下来，留给未来的自己。内容仅保存在本机。',
      actions: [IconButton(onPressed: () => openPage(context, const JournalEditPage()),
        icon: const Icon(Icons.edit_note))], children: [
        SizedBox(width: double.infinity, child: FilledButton.icon(
          onPressed: () => openPage(context, const JournalEditPage()),
          icon: const Icon(Icons.edit_outlined), label: const Text('写一篇日记'))),
        const SizedBox(height: 8),
        Row(children: [
          IconButton(onPressed: () => setState(() => month = DateTime(month.year, month.month - 1)),
            icon: const Icon(Icons.chevron_left)),
          Expanded(child: Center(child: Text('${month.year} 年 ${month.month} 月',
            style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700)))),
          IconButton(onPressed: () => setState(() => month = DateTime(month.year, month.month + 1)),
            icon: const Icon(Icons.chevron_right)),
        ]),
        TextField(controller: search, onChanged: (_) => setState(() {}),
          decoration: const InputDecoration(prefixIcon: Icon(Icons.search),
            hintText: '按标题搜索当月日记（不检索私密正文）')),
        const SizedBox(height: 10),
        SectionHeading('日记记录 · ${entries.length} 篇'),
        if (entries.isEmpty) const SoftCard(child: Text('本月暂无记录。你可以写一篇日记，或导入 Word / PDF。')),
        ...entries.map((e) => NavCard(icon: Icons.book_outlined,
          title: e.title,
          subtitle: '私密 · ${e.createdAt.year}-${e.createdAt.month}-${e.createdAt.day} · 最近编辑 ${e.updatedAt.month}/${e.updatedAt.day}',
          onTap: () => openPage(context, JournalReadPage(id: e.id)))),
        OutlinedButton.icon(onPressed: () => openPage(context, const JournalEditPage(autoImport: true)),
          icon: const Icon(Icons.upload_file_outlined), label: const Text('导入 Word / PDF 到日记')),
        const Padding(padding: EdgeInsets.symmetric(vertical: 8), child: Text(
          '隐私说明：日记和附件在本机加密，默认不参与 AI 问答。普通数据备份不包含日记。',
          style: TextStyle(fontSize: 12, color: Colors.grey))),
      ]);
  }
}


/// Read-first private journal page. Tapping a saved diary does not enter
/// append mode, and decrypted content is kept in memory only for the viewer.
class JournalReadPage extends StatelessWidget {
  final String id;
  const JournalReadPage({super.key, required this.id});

  @override
  Widget build(BuildContext context) {
    final store = CompassScope.of(context);
    final entry = store.byId(id);
    if (entry == null || entry.kind != 'journal') {
      return const PageFrame(title: '日记不存在',
        children: [Text('这篇日记可能已被删除。')]);
    }
    return PageFrame(title: '阅读私密日记',
      subtitle: entry.title,
      actions: [IconButton(
        tooltip: '继续写这篇日记',
        icon: const Icon(Icons.edit_note_outlined),
        onPressed: () => openPage(context, JournalEditPage(id: id)))],
      bottomBar: SafeArea(top: false, child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 9, 16, 12),
        child: FilledButton.icon(
          onPressed: () => openPage(context, JournalEditPage(id: id)),
          icon: const Icon(Icons.edit_note_outlined),
          label: const Text('继续写 · 追加到这篇日记')),
      )),
      children: [
        const SoftCard(color: Color(0xFFF4EDF9), child: Text(
          '正文和附件仅在本机解密阅读，不会自动上传云端。')),
        const SectionHeading('已保存的正文'),
        FutureBuilder<String>(
          future: store.privateJournalBody(id),
          builder: (ctx, snapshot) {
            if (snapshot.hasError) {
              return SoftCard(child: Text('日记解密失败：${snapshot.error}'));
            }
            if (!snapshot.hasData) return const Center(
              child: CircularProgressIndicator());
            return SoftCard(child: SelectableText(
              snapshot.data!.isEmpty ? '这篇日记暂时只有附件，还没有正文。' :
                snapshot.data!,
              style: const TextStyle(fontSize: 15, height: 1.8)));
          },
        ),
        const SectionHeading('已保存的私密附件'),
        FutureBuilder<List<Map<String, Object?>>>(
          future: store.journalAttachments(id),
          builder: (ctx, snapshot) {
            if (snapshot.hasError) {
              return Text('附件读取失败：${snapshot.error}');
            }
            if (!snapshot.hasData) return const Center(
              child: CircularProgressIndicator());
            if (snapshot.data!.isEmpty) {
              return const SoftCard(child: Text('暂无附件'));
            }
            return Column(children: snapshot.data!.map((asset) => NavCard(
              icon: asset['mime'] == 'application/pdf' ?
                Icons.picture_as_pdf_outlined : Icons.description_outlined,
              title: asset['filename']?.toString() ?? '私密附件',
              subtitle: '点击查看 · 仅本机解密',
              onTap: () => openPage(context, JournalAttachmentViewer(
                journalId: id,
                assetId: asset['id'] as String,
                filename: asset['filename'] as String)),
            )).toList());
          },
        ),
      ]);
  }
}

class JournalEditPage extends StatefulWidget {
  final String? id;
  const JournalEditPage({super.key, this.id, this.autoImport = false});
  final bool autoImport;
  @override
  State<JournalEditPage> createState() => _JournalEditPageState();
}

class _JournalEditPageState extends State<JournalEditPage> {
  final title = TextEditingController(), body = TextEditingController();
  String? _id;
  bool busy = false, loaded = false, allowPop = false;
  String lastTitle = '', lastBody = '';
  int refreshAssets = 0;
  bool _importStarted = false;
  bool get dirty => title.text.trim() != lastTitle || body.text.trim().isNotEmpty;
  @override
  void initState() {
    super.initState();
    _id = widget.id;
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      if (!mounted) return;
      if (_id != null) {
        final store = CompassScope.of(context);
        final entry = store.byId(_id!);
        try {
          final text = await store.privateJournalBody(_id!);
          if (!mounted) return;
          if (entry != null) {
            title.text = entry.title;
            lastBody = text;
            body.clear();
          }
        } catch (e) {
          if (mounted) notice(context, '日记解密失败：$e；原文件未被更改');
        }
      }
      if (mounted) setState(() {
        loaded = true;
        lastTitle = title.text;
        // lastBody is the last durable encrypted journal text.
        allowPop = true;
      });
      if (mounted && widget.autoImport && !_importStarted) {
        _importStarted = true;
        await importDocument();
      }
    });
  }
  @override
  void dispose() {
    title.dispose(); body.dispose(); super.dispose();
  }
  void changed(String _) {
    if (!loaded) return;
    // Draft is saved only when explicitly requested. Clearing after a
    // three-second debounce would interrupt long journal writing.
    setState(() => allowPop = false);
  }

  Future<bool> save({bool silent = false}) async {
    if (busy) return false;
    final t = title.text.trim().isEmpty ?
      '日记 · ${DateTime.now().year}-${DateTime.now().month}-${DateTime.now().day}' : title.text.trim();
    final newPart = body.text.trim();
    final text = lastBody.isEmpty ? newPart :
      newPart.isEmpty ? lastBody : '$lastBody\n\n$newPart';
    if (_id != null && t == lastTitle && newPart.isEmpty) {
      if (mounted) setState(() => allowPop = true);
      return true;
    }
    setState(() => busy = true);
    try {
      final store = CompassScope.of(context);
      if (_id == null) {
        final created = await store.add(kind: 'journal', title: t,
          body: text, status: '私密');
        _id = created.id;
      } else {
        final previous = store.byId(_id!);
        if (previous == null) throw StateError('日记已被删除');
        await store.update(previous.copyWith(title: t), privateBody: text);
      }
      if (!mounted) return true;
      lastTitle = t;
      lastBody = text;
      // Clear the append input ONLY AFTER durable encrypted storage succeeds.
      body.clear();
      if (title.text.trim().isEmpty) title.text = t;
      setState(() { allowPop = !dirty; refreshAssets++; });
      if (!silent) notice(context, '日记已加密保存并保留历史版本');
      return true;
    } catch (e) {
      if (mounted) notice(context, '保存失败：$e。请不要退出当前页面');
      return false;
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  Future<void> importDocument() async {
    if (busy) return;
    File? temporary;
    try {
      final chosen = await FilePicker.platform.pickFiles(
        type: FileType.custom,
        allowedExtensions: ['docx', 'pdf'],
        withData: true,
      );
      if (chosen == null || chosen.files.isEmpty || !mounted) return;
      final selected = chosen.files.single;
      final name = selected.name;
      final isWord = name.toLowerCase().endsWith('.docx');
      final isPdf = name.toLowerCase().endsWith('.pdf');
      if (!isWord && !isPdf) {
        throw const FormatException('请选择 Word（DOCX）或 PDF 文件');
      }
      if (selected.size <= 0 || selected.size > 40 * 1024 * 1024) {
        throw const FormatException('请选择 40 MB 以内的非空文件');
      }

      // Android cloud/document providers can return bytes without a usable path.
      // Use the selected file itself instead of requiring a second picker.
      File source;
      final pickedPath = selected.path;
      if (pickedPath != null && await File(pickedPath).exists()) {
        source = File(pickedPath);
      } else if (selected.bytes != null) {
        final dir = await getTemporaryDirectory();
        temporary = File(path.join(dir.path, 'journal_import_${CompassStore.newId()}${isWord ? '.docx' : '.pdf'}'));
        await temporary.writeAsBytes(selected.bytes!, flush: true);
        source = temporary;
      } else {
        throw StateError('无法读取此文件，请下载到手机本地后再试');
      }

      final length = await source.length();
      if (length <= 0 || length > 40 * 1024 * 1024) {
        throw const FormatException('文件大小须在 1 字节至 40 MB 之间');
      }
      if (_id == null && title.text.trim().isEmpty) {
        title.text = path.basenameWithoutExtension(name);
      }
      if (_id == null || dirty) {
        if (!await save()) return;
      }
      final store = CompassScope.of(context);
      await store.addJournalAttachment(_id!, source, name);
      if (!mounted) return;
      setState(() => refreshAssets++);
      notice(context, '附件已加密保存到本机');

      // Attachment persistence and optional text extraction are separate:
      // a non-text/scanned PDF must never turn a saved attachment into failure.
      String extracted;
      try {
        extracted = (await LocalDocumentParser().parseFile(source)).text;
      } catch (_) {
        if (mounted) notice(context,
          '附件已安全保存，暂时无法提取正文；你仍可点击附件查看原文件');
        return;
      }
      if (!mounted) return;
      final append = await showDialog<bool>(context: context, builder: (ctx) =>
        AlertDialog(title: const Text('将文件内容添加到日记？'),
          content: Text('已识别 ${extracted.length} 个字符。原文件已安全保存，'
            '添加正文不会覆盖原日记。'),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx, false),
              child: const Text('仅保存附件')),
            FilledButton(onPressed: () => Navigator.pop(ctx, true),
              child: const Text('添加到正文')),
          ]));
      if (append == true && mounted) {
        body.text = body.text.isEmpty ? extracted : '${body.text}\n\n$extracted';
        changed('');
        if (!await save() && mounted) {
          notice(context, '附件已保存；正文保存未完成，请保留输入框中的内容');
        }
      }

    } catch (e) {
      if (mounted) notice(context, '未能完成导入：$e。已有日记不会被删除');
    } finally {
      if (temporary != null && await temporary.exists()) {
        try { await temporary.delete(); } catch (_) { /* best-effort cleanup */ }
      }
    }
  }

  Future<void> analyze() async {
    // Do not misrepresent a Render deployment as a private Windows processor.
    notice(context, '私密日记 AI 云端授权尚未上线；日记不会在本版本上传。');
  }

  Future<void> onBack(bool didPop, Object? result) async {
    if (didPop || !mounted || busy) return;
    if (!dirty) {
      setState(() => allowPop = true);
      Navigator.of(context).pop();
      return;
    }
    final choice = await showDialog<String>(context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('本次追加还没有保存'),
        content: const Text('可以先加密保存再离开，或明确放弃本次未保存的输入。'
          '已保存的日记和附件不会被删除。'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, 'cancel'),
            child: const Text('继续写')),
          TextButton(onPressed: () => Navigator.pop(ctx, 'discard'),
            child: const Text('放弃本次输入')),
          FilledButton(onPressed: () => Navigator.pop(ctx, 'save'),
            child: const Text('保存并离开')),
        ],
      ));
    if (!mounted || choice == null || choice == 'cancel') return;
    if (choice == 'save' && !await save()) return;
    if (!mounted) return;
    setState(() => allowPop = true);
    Navigator.of(context).pop();
  }

  @override
  Widget build(BuildContext context) {
    final id = _id;
    return PopScope(canPop: allowPop && !busy, onPopInvokedWithResult: onBack,
      child: PageFrame(title: id == null ? '写日记' : '追加日记',
        subtitle: '旧正文不覆盖 · 保存后清空本次输入 · 附件可离线查看',
        bottomBar: SafeArea(top: false, child: Container(
          padding: const EdgeInsets.fromLTRB(16, 9, 16, 12),
          decoration: const BoxDecoration(color: Color(0xFFF9F5FA),
            border: Border(top: BorderSide(color: Color(0xFFECE3F0)))),
          child: Row(children: [
            Expanded(child: Text(
              busy ? '正在加密…' : dirty ? '有未保存内容' : '已保存',
              style: const TextStyle(color: grape,
                fontWeight: FontWeight.w600))),
            FilledButton.icon(
              onPressed: busy || !loaded ? null : () => save(),
              icon: const Icon(Icons.save_outlined),
              label: const Text('保存本次内容')),
          ]),
        )), children: [
          const Padding(padding: EdgeInsets.only(bottom: 12), child: Text(
            '写下今天的想法，保存后可随时回来继续写。',
            style: TextStyle(color: Color(0xFF8C8090)))),
          TextField(controller: title, maxLength: 120, onChanged: changed,
            decoration: const InputDecoration(labelText: '日记标题')),
          if (id != null) NavCard(icon: Icons.menu_book_outlined,
            title: '点击阅读已保存的日记',
            subtitle: '已保存 '+lastBody.length.toString()+' 字 · 本次追加输入不会改变旧正文',
            onTap: () => openPage(context, JournalReadPage(id: id))),
          const SizedBox(height: 10),
          Text(busy ? '加密保存中…' : dirty ? '本次内容尚未保存' : '全部内容已保存',
            style: const TextStyle(color: grape)),
          const SizedBox(height: 12),
          TextField(controller: body, minLines: 5, maxLines: 12,
            onChanged: changed, decoration: const InputDecoration(
              labelText: '本次追加内容 · 不覆盖已保存的旧正文（不限字数）')),
          const SizedBox(height: 10),
          OutlinedButton.icon(onPressed: busy || !loaded ? null : importDocument,
            icon: const Icon(Icons.upload_file), label: const Text('导入 Word / PDF 日记')),
          // Cloud diary access is disabled until the consent UI is upgraded.

          if (id != null) ...[
            const SectionHeading('私密附件 · 点击文件查看'),
            FutureBuilder<List<Map<String, Object?>>>(
              key: ValueKey('${id}_${refreshAssets}'),
              future: CompassScope.of(context).journalAttachments(id),
              builder: (ctx, snap) {
                if (snap.hasError) return Text('附件读取失败：${snap.error}');
                if (!snap.hasData) return const CircularProgressIndicator();
                if (snap.data!.isEmpty) return const SoftCard(child: Text('还没有附件'));
                return Column(children: snap.data!.map((a) => ListTile(
                  leading: Icon(a['mime'] == 'application/pdf' ?
                    Icons.picture_as_pdf_outlined : Icons.description_outlined,
                    color: grape),
                  title: Text(a['filename']?.toString() ?? '私密附件'),
                  subtitle: Text('加密附件 · ${a['byte_count']} 字节 · 点击查看'),
                  onTap: () => openPage(context, JournalAttachmentViewer(
                    journalId: id,
                    assetId: a['id'] as String,
                    filename: a['filename'] as String)),
                  trailing: IconButton(
                    tooltip: '删除附件',
                    icon: const Icon(Icons.delete_outline),
                    onPressed: () async {
                      final yes = await showDialog<bool>(context: context,
                        builder: (ctx) => AlertDialog(title: const Text('删除附件？'),
                          content: Text('将永久删除 ${a['filename']} 的加密文件。'),
                          actions: [
                            TextButton(onPressed: () => Navigator.pop(ctx, false),
                              child: const Text('取消')),
                            FilledButton(onPressed: () => Navigator.pop(ctx, true),
                              child: const Text('删除')),
                          ]));
                      if (yes == true && mounted) {
                        await CompassScope.of(context).removeJournalAttachment(
                          id, a['id'] as String);
                        if (mounted) setState(() => refreshAssets++);
                      }
                    }),
                )).toList());
              }),
            NavCard(icon: Icons.history, title: '日记历史版本',
              subtitle: '浏览和恢复以前保存的正文，不覆盖旧版本',
              onTap: () async {
                await openPage(context, JournalHistoryPage(id: id));
                if (!mounted) return;
                final text = await CompassScope.of(context).privateJournalBody(id);
                if (mounted) setState(() {
                  lastBody = text; body.clear();
                  allowPop = !dirty;
                });
              }),
            TextButton(onPressed: () async {
              final store = CompassScope.of(context);
              final entry = store.byId(id);
              if (entry == null) return;
              final approved = await showDialog<bool>(context: context,
                builder: (ctx) => AlertDialog(title: const Text('永久删除私密日记？'),
                  content: const Text('日记正文、历史版本和所有加密附件将被删除。'),
                  actions: [
                    TextButton(onPressed: () => Navigator.pop(ctx, false),
                      child: const Text('取消')),
                    FilledButton(onPressed: () => Navigator.pop(ctx, true),
                      child: const Text('永久删除')),
                  ]));
              if (approved == true && mounted) {
                await store.remove(entry);
                if (mounted) {setState(() => allowPop = true); Navigator.pop(context);}
              }
            }, child: const Text('删除这篇日记', style: TextStyle(color: Colors.red))),
          ],
        ]));
  }
}

class JournalHistoryPage extends StatelessWidget {
  final String id;
  const JournalHistoryPage({super.key, required this.id});
  @override
  Widget build(BuildContext context) {
    final store = CompassScope.of(context);
    return PageFrame(title: '私密日记历史', subtitle: '恢复会创建一个新版本，原来的版本仍保留',
      children: [
        FutureBuilder<List<Map<String, Object?>>>(
          future: store.journalHistory(id),
          builder: (ctx, snap) {
            if (snap.hasError) return Text('读取失败：${snap.error}');
            if (!snap.hasData) return const CircularProgressIndicator();
            return Column(children: snap.data!.map((v) => NavCard(
              icon: Icons.history,
              title: '保存于 ${v['saved_at']}',
              subtitle: '正文长度 ${v['byte_count']} 字节 · 点击查看和恢复',
              onTap: () async {
                final revision = v['revision_id'] as String;
                try {
                  final text = await store.privateJournalBody(id);
                  // The actual historical revision is only decrypted on demand.
                  final answer = await showDialog<bool>(context: context,
                    builder: (ctx) => AlertDialog(
                      title: const Text('恢复历史版本？'),
                      content: Text('将所选历史版本恢复为最新版，当前正文会作为历史保留。'
                        '当前内容约 ${text.length} 字符。'),
                      actions: [
                        TextButton(onPressed: () => Navigator.pop(ctx, false),
                          child: const Text('取消')),
                        FilledButton(onPressed: () => Navigator.pop(ctx, true),
                          child: const Text('恢复')),
                      ]));
                  if (answer == true) {
                    await store.restoreJournalRevision(id, revision);
                    if (context.mounted) {
                      notice(context, '已恢复为新版本');
                      Navigator.pop(context);
                    }
                  }
                } catch (e) {
                  if (context.mounted) notice(context, '恢复失败：$e');
                }
              })).toList());
          }),
      ]);
  }
}

class DeviceSettingsPage extends StatefulWidget {
  const DeviceSettingsPage({super.key});
  @override
  State<DeviceSettingsPage> createState() => _DeviceSettingsPageState();
}
class _DeviceSettingsPageState extends State<DeviceSettingsPage> {
  final server = TextEditingController();
  final token = TextEditingController();
  String status = '尚未测试连接';
  bool testing = false;
  bool liveTesting = false;
  @override
  void initState() {
    super.initState(); WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final store = CompassScope.of(context);
      server.text = store.serverUrl; token.text = store.deviceToken;
    });
  }
  @override
  void dispose() {server.dispose(); token.dispose(); super.dispose();}
  Future<void> save() async {
    try {
      await CompassScope.of(context).setConnection(server.text, token.text);
      if (mounted) notice(context, 'AI 工作站设置已保存');
    } catch (e) {if (mounted) notice(context, '配置无效：$e');}
  }
  Future<void> test() async {
    setState(() {testing = true; status = '正在连接…';});
    try {
      await CompassScope.of(context).setConnection(server.text, token.text);
      final health = await AiClient(CompassScope.of(context)).health();
      if (mounted) setState(() => status = health.providerConfigured ?
        '节点在线 · 模型 ${health.model} · 已配置密钥（尚未执行实际问答）' :
        '节点在线 · 模型 ${health.model} · 未配置 DeepSeek 密钥');
    } catch (e) {if (mounted) setState(() => status = '无法连接：$e');}
    finally {if (mounted) setState(() => testing = false);}
  }
  Future<void> testRealAi() async {
    if (liveTesting) return;
    setState(() {liveTesting = true; status = '正在调用 DeepSeek 进行真实问答…';});
    try {
      await CompassScope.of(context).setConnection(server.text, token.text);
      final result = await AiClient(CompassScope.of(context))
          .ask('请用简短的一句话确认 AI 服务已连接。', mode: 'ask');
      if (mounted) setState(() => status = '真实 AI 调用成功 · ${result.model} · ${result.answer}');
    } catch (e) {
      if (mounted) setState(() => status = '真实 AI 调用失败：$e');
    } finally {
      if (mounted) setState(() => liveTesting = false);
    }
  }
  @override
  Widget build(BuildContext context) => PageFrame(title: '设备与 AI 设置',
    subtitle: '你的 DeepSeek Key 只应该保存在服务端环境变量里，不能写入 APK。', children: [
      const SoftCard(color: Color(0xFFF4EDF9), child: Column(children: [
        Icon(Icons.computer_outlined, color: grape, size: 33),
        SizedBox(height: 8),
        Text('云端 AI 网关', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18)),
        SizedBox(height: 8),
        Text('电脑离线不影响手机记笔记；在线且正确配置服务端密钥后可使用 AI。',
          textAlign: TextAlign.center),
      ])),
      TextField(controller: server, keyboardType: TextInputType.url,
        decoration: const InputDecoration(labelText: 'AI 服务地址',
          hintText: '例如 https://你的安全私有域名')),
      const SizedBox(height: 12),
      TextField(controller: token, obscureText: true, enableSuggestions: false,
        autocorrect: false,
        decoration: const InputDecoration(labelText: '设备配对令牌', hintText: '与服务端 APP_DEVICE_TOKEN 一致')),
      const SizedBox(height: 14),
      FilledButton.icon(onPressed: save, icon: const Icon(Icons.save),
        label: const Text('保存连接设置')),
      const SizedBox(height: 10),
      OutlinedButton.icon(onPressed: testing ? null : test, icon: const Icon(Icons.wifi_tethering),
        label: Text(testing ? '正在测试…' : '测试节点连接')),
      const SizedBox(height: 10),
      FilledButton.icon(onPressed: liveTesting ? null : testRealAi,
        icon: const Icon(Icons.verified_outlined),
        label: Text(liveTesting ? 'DeepSeek 请求中…' : '真实 AI 问答测试（需要流量和额度）')),
      const SizedBox(height: 12),
      SoftCard(child: Text(status)),
      NavCard(icon: Icons.wifi_off, title: '离线状态说明',
        onTap: () => openPage(context, const OfflineStatusPage())),
      const SoftCard(color: Color(0xFFFFF1E8), child: Text('安全提示：通过外网访问必须使用 HTTPS 和设备令牌。'
        '请勿直接将没有认证的本地服务暴露到公网。')),
    ]);
}

class OfflineStatusPage extends StatelessWidget {
  const OfflineStatusPage({super.key});
  @override
  Widget build(BuildContext context) => PageFrame(title: '离线工作模式',
    subtitle: 'AI 工作站未连接时仍可继续学习。', children: [
      const SoftCard(color: Color(0xFFF1EAF7), child: Column(children: [
        Icon(Icons.cloud_off_outlined, size: 40, color: grape),
        SizedBox(height: 12),
        Text('离线并不意味着停止成长', style: TextStyle(fontSize: 19, fontWeight: FontWeight.bold)),
        SizedBox(height: 8),
        Text('可继续保存来源链接、写知识卡、做任务、记录决策和私密日记。'),
      ])),
      NavCard(icon: Icons.note_add_outlined, title: '离线收集', onTap: () => openPage(context, const CapturePage())),
      NavCard(icon: Icons.book_outlined, title: '离线阅读知识卡', onTap: () => openPage(context, const KnowledgePage())),
      NavCard(icon: Icons.settings, title: '设置 AI 连接', onTap: () => openPage(context, const DeviceSettingsPage())),
    ]);
}

class MinePage extends StatelessWidget {
  const MinePage({super.key});
  @override
  Widget build(BuildContext context) {
    final store = CompassScope.of(context);
    return PageFrame(title: '我的', subtitle: '让改变变得有迹可循。', children: [
      SoftCard(color: const Color(0xFFF3E9F7), child: Row(children: [
        const CircleAvatar(radius: 28, backgroundColor: Color(0xFFE4D4ED),
          child: Icon(Icons.person_outline, color: grape, size: 30)),
        const SizedBox(width: 14),
        Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          const Text('知行者', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 20)),
          Text('${store.ofKind('card').length} 张知识卡 · ${store.ofKind('decision').length} 条决策记录',
            style: const TextStyle(color: Colors.grey, fontSize: 12)),
        ])),
      ])),
      NavCard(icon: Icons.edit_note_outlined, title: '写日记 / 查看日记',
        subtitle: '私密保存，支持导入 Word / PDF',
        onTap: () => openPage(context, const JournalPage())),
      NavCard(icon: Icons.favorite_border, title: '成长与学习路径',
        onTap: () => openPage(context, const LearningPage())),
      NavCard(icon: Icons.balance, title: '我的决策与复盘',
        onTap: () => openPage(context, const DecisionPage())),
      NavCard(icon: Icons.devices_outlined, title: 'AI 节点与设备管理',
        onTap: () => openPage(context, const DeviceSettingsPage())),
      NavCard(icon: Icons.privacy_tip_outlined, title: '账户与隐私',
        onTap: () => openPage(context, const PrivacyPage())),
    ]);
  }
}

class PrivacyPage extends StatefulWidget {
  const PrivacyPage({super.key});
  @override
  State<PrivacyPage> createState() => _PrivacyPageState();
}
class _PrivacyPageState extends State<PrivacyPage> {
  String result = '';
  @override
  Widget build(BuildContext context) => PageFrame(title: '账户与隐私',
    subtitle: '你的资料应当可查看、可导出、可删除。', children: [
      const SoftCard(color: Color(0xFFF1ECF7), child: Column(
        crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text('隐私边界', style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
        SizedBox(height: 8),
        Text('• 私密日记正文单独保存在系统安全存储。\n'
          '• 默认不进入普通知识搜索和 AI 自动资料。\n'
          '• 每次发送给 DeepSeek 前都会提示并要求确认。\n'
          '• 公开版多用户账号与云同步尚未启用。',
          style: TextStyle(height: 1.7)),
      ])),
      NavCard(icon: Icons.file_download_outlined, title: '保存普通数据备份文件',
        subtitle: '选择保存位置并导出 JSON；不包含私密日记和二进制附件',
        onTap: () async {
          try {
            final json = await CompassScope.of(context).exportJson();
            final today = DateTime.now().toIso8601String().substring(0, 10).replaceAll('-', '');
            final saved = await FilePicker.platform.saveFile(
              dialogTitle: '保存知行罗盘普通数据备份',
              fileName: 'zhixing-backup-$today.json',
              type: FileType.custom, allowedExtensions: ['json'],
              bytes: Uint8List.fromList(utf8.encode(json)));
            if (context.mounted && saved != null) {
              setState(() => result = '已请求系统保存普通数据备份到所选位置：$saved');
            }
          } catch (e) {
            if (context.mounted) setState(() => result = '文件备份失败：$e');
          }
        }),
      NavCard(icon: Icons.restore, title: '恢复普通数据备份',
         subtitle: '选择此前导出的 JSON；只导入新记录，不覆盖旧记录',
         onTap: () async {
           try {
             final selection = await FilePicker.platform.pickFiles(
               type: FileType.custom, allowedExtensions: ['json']);
             if (selection == null || selection.files.isEmpty || !context.mounted) return;
             final item = selection.files.single;
             if (item.path == null || item.size > 15 * 1024 * 1024) {
               notice(context, '无法读取或备份大于 15 MB'); return;
             }
             final approved = await showDialog<bool>(context: context,
               builder: (ctx) => AlertDialog(title: const Text('确认恢复备份？'),
                 content: const Text('仅添加当前设备不存在的普通记录，重复 ID 跳过，不覆盖私密日记。'),
                 actions: [TextButton(onPressed: () => Navigator.pop(ctx, false),
                   child: const Text('取消')),
                   FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('开始恢复'))]));
             if (approved != true || !context.mounted) return;
             final count = await CompassScope.of(context).importJson(await File(item.path!).readAsString());
             if (context.mounted) setState(() => result = '成功恢复 $count 条新记录；已有记录未覆盖');
           } catch (e) {
             if (context.mounted) setState(() => result = '恢复失败，原有记录未修改：$e');
           }
         }),
       NavCard(icon: Icons.lock_outline, title: '查看私密日记',
        subtitle: '单次授权不会长期保存',
        onTap: () => openPage(context, const JournalPage())),
      NavCard(icon: Icons.manage_accounts_outlined, title: '设备连接令牌',
        onTap: () => openPage(context, const DeviceSettingsPage())),
      if (result.isNotEmpty) SoftCard(child: Text(result)),
      const SoftCard(color: Color(0xFFFFF1E8), child: Text('目前是个人测试版，尚未实现账号注册、完整加密备份和公开发行数据合规流程。请勿以正式公开服务部署。')),
    ]);
}
