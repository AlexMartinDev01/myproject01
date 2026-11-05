import 'dart:io';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:path/path.dart' as path;
import 'package:path_provider/path_provider.dart';
import 'package:url_launcher/url_launcher.dart';
import 'main.dart';
import 'core/store.dart';
import 'core/ai.dart';

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
            Text('手机离线保存内容；可选连接自己的 Windows AI 工作站。'),
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
        SectionHeading('快速开始'),
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
            content: const Text('删除后无法恢复。'), actions: [
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
    setState(() {type = '书籍/PDF'; title.text = name; url.text = dest.path;});
    notice(context, '文件已复制到本机应用目录；全文解析需连接 AI 工作站');
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
      final duplicate = isWeb && store.ofKind('source').any((e) => e.origin == raw);
      if (duplicate) { notice(context, '这条链接已经在收集箱中'); return; }
      await store.add(kind: 'source', title: title.text.trim().isEmpty ?
          (raw.isEmpty ? body.text.trim().split('\n').first : raw) : title.text.trim(),
        body: body.text.trim(), origin: raw,
        status: body.text.trim().isEmpty ? '需补内容' : '待处理',
        meta: {'source_type': type, 'content_level': body.text.trim().isEmpty ? 'metadata_only' : 'user_supplied'});
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
      OutlinedButton.icon(onPressed: attachFile, icon: const Icon(Icons.upload_file),
        label: const Text('选择 PDF / 文本 / EPUB 文件')),
      const SizedBox(height: 12),
      SizedBox(width: double.infinity, child: FilledButton(
        onPressed: saving ? null : save,
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
    if (source.body.trim().isEmpty) {
      openPage(context, SourceMissingPage(sourceId: source.id)); return;
    }
    setState(() => thinking = true);
    try {
      final result = await AiClient(CompassScope.of(context)).ask(
        '请忠实提炼这篇资料，注意正文是用户上传或摘录的内容，并不保证完整。',
        mode: 'brief', evidence: [source]);
      if (!mounted) return;
      setState(() => aiBrief = result.answer);
    } catch (e) {
      if (mounted) notice(context, 'AI 整理未完成：$e');
    } finally {if (mounted) setState(() => thinking = false);}
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
      SectionHeading('原文 / 用户提供的材料'),
      SoftCard(child: Text(full ? source.body : '当前仅保存了链接或书目信息，尚无可分析的完整正文。',
        style: const TextStyle(height: 1.65))),
      if (source.origin.toLowerCase().endsWith('.pdf') && !full)
        NavCard(icon: Icons.picture_as_pdf_outlined, title: '从本地 PDF 提取文字',
          subtitle: '使用已配对的 Windows AI 工作站，不会绕过加密',
          onTap: () async {
            try {
              final extracted = await AiClient(store).extractPdf(source.origin);
              final parsedText = extracted['text']?.toString() ?? '';
              if (parsedText.trim().isEmpty) {
                if (context.mounted) notice(context, '此文件可能是扫描 PDF，需要 OCR；未生成虚假正文');
                return;
              }
              await store.update(source.copyWith(body: parsedText, status: '待处理',
                meta: {...source.meta, 'content_level': 'extracted_pdf',
                  'page_count': extracted['page_count']}));
              if (context.mounted) notice(context, 'PDF 文字已按页提取');
            } catch (e) {if (context.mounted) notice(context, 'PDF 解析失败：$e');}
          }),
      if (!full) NavCard(icon: Icons.edit_note, title: '补录字幕或原文',
        subtitle: '补全材料后才可做基于正文的 AI 摘要',
        onTap: () => openPage(context, SourceMissingPage(sourceId: source.id))),
      const SectionHeading('AI 提炼'),
      if (aiBrief != null) SoftCard(color: const Color(0xFFF1E9F6), child: SelectableText(aiBrief!,
        style: const TextStyle(height: 1.6))),
      SoftCard(child: Column(children: [
        SizedBox(width: double.infinity, child: FilledButton.icon(
          onPressed: thinking ? null : () => summarize(source),
          icon: const Icon(Icons.auto_awesome),
          label: Text(thinking ? 'AI 正在整理…' : 'AI 提炼关键内容'))),
        const SizedBox(height: 8),
        SizedBox(width: double.infinity, child: OutlinedButton.icon(
          onPressed: () => openPage(context, CardEditPage(
            originalSourceId: source.id, initialTitle: source.title,
            initialBody: aiBrief ?? source.body)),
          icon: const Icon(Icons.library_add_outlined), label: const Text('创建我自己的知识卡'))),
        SizedBox(width: double.infinity, child: TextButton(
          onPressed: () async {
            await store.update(source.copyWith(status: '已整理'));
            if (context.mounted) notice(context, '已标记为整理完成');
          }, child: const Text('标记为已整理'))),
      ])),
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
        const SectionHeading('知识专题'),
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
  late final TextEditingController counter;
  late final TextEditingController application;
  String topic = '自我成长';
  @override
  void initState() {
    super.initState();
    title = TextEditingController(text: widget.initialTitle);
    explanation = TextEditingController(text: widget.initialBody);
    counter = TextEditingController(); application = TextEditingController();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || widget.id == null) return;
      final e = CompassScope.of(context).byId(widget.id!);
      if (e != null) setState(() {
        title.text = e.title;
        explanation.text = e.body;
        counter.text = e.meta['counter']?.toString() ?? '';
        application.text = e.meta['application']?.toString() ?? '';
        topic = e.meta['topic']?.toString() ?? topic;
      });
    });
  }
  @override
  void dispose() {
    title.dispose(); explanation.dispose(); counter.dispose(); application.dispose();
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
        final meta = {'topic': topic, 'counter': counter.text.trim(),
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
      NavCard(icon: Icons.question_answer_outlined, title: '从知识库提问',
        subtitle: '带来源的问答', onTap: () => openPage(context, const AiChatPage(mode: 'ask'))),
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

class AiChatPage extends StatefulWidget {
  final String mode;
  final List<CompassEntry> selectedEvidence;
  final String? approvedPrivateText;
  const AiChatPage({super.key, this.mode = 'ask', this.selectedEvidence = const [], this.approvedPrivateText});
  @override
  State<AiChatPage> createState() => _AiChatPageState();
}
class _AiChatPageState extends State<AiChatPage> {
  final question = TextEditingController();
  bool busy = false;
  bool privateAlreadySent = false;
  String? answer;
  String? model;
  String? error;
  List<String> references = [];
  @override
  void dispose() {question.dispose(); super.dispose();}
  Future<void> ask() async {
    if (question.text.trim().isEmpty) {notice(context, '请输入要思考的问题'); return;}
    if (busy) return;
    if (widget.approvedPrivateText?.isNotEmpty == true) {
      if (privateAlreadySent) {
        final approved = await showDialog<bool>(context: context, barrierDismissible: false,
          builder: (ctx) => AlertDialog(
            title: const Text('再次授权读取私密日记？'),
            content: const Text('上一次授权已经失效。继续发送这一条问题将再次把当前日记正文发送到你的 AI 节点，且可能转发至 DeepSeek。'),
            actions: [TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('拒绝')),
              FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('允许本次访问'))]));
        if (approved != true || !mounted) return;
      }
      // Consumed before network work: any retry requires new explicit approval.
      privateAlreadySent = true;
    }
    setState(() {busy = true; error = null; answer = null; references = [];});
    final store = CompassScope.of(context);
    final candidates = widget.selectedEvidence.isNotEmpty ? widget.selectedEvidence :
        store.items.where((e) => (e.kind == 'card' || e.kind == 'source') && e.body.isNotEmpty).take(8).toList();
    try {
      final response = await AiClient(store).ask(question.text.trim(), mode: widget.mode,
        evidence: candidates, privateText: widget.approvedPrivateText);
      if (mounted) setState(() {
        answer = response.answer; model = response.model; references = response.sourceIds;
      });
    } catch (e) {
      if (mounted) setState(() => error = e.toString());
    } finally {if (mounted) setState(() => busy = false);}
  }
  @override
  Widget build(BuildContext context) {
    final titles = {'ask': '知识问答', 'learn': '费曼学习', 'challenge': '反方思辨',
      'decision': '决策分析', 'review': '复盘教练', 'brief': '资料提炼'};
    final store = CompassScope.of(context);
    return PageFrame(title: titles[widget.mode] ?? 'AI 思考',
      subtitle: widget.approvedPrivateText == null ?
        '只引用有权访问的知识卡与用户提供的正文，不自动读取私密日记。' :
        '已授权单次发送私密日记内容到你的 AI 工作站；不会自动保存日记到聊天记录。',
      children: [
        if (store.serverUrl.isEmpty) NavCard(icon: Icons.wifi_off_outlined,
          title: 'AI 节点尚未配置', subtitle: '笔记功能继续离线可用。请先连接 AI 工作站。',
          onTap: () => openPage(context, const DeviceSettingsPage())),
        TextField(controller: question, minLines: 3, maxLines: 8,
          textInputAction: TextInputAction.newline,
          decoration: const InputDecoration(labelText: '写下你想理解或判断的问题',
            hintText: '例如：我总在重大选择前犹豫，真正需要补哪些信息？')),
        const SizedBox(height: 12),
        FilledButton.icon(onPressed: busy ? null : ask,
          icon: Icon(busy ? Icons.hourglass_top : Icons.auto_awesome),
          label: Text(busy ? '正在请求 AI…' : '开始深度思考')),
        if (busy) const Padding(padding: EdgeInsets.all(18), child: LinearProgressIndicator()),
        if (error != null) SoftCard(color: const Color(0xFFFFF0EF),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(error!, style: const TextStyle(color: Color(0xFFA22B35))),
            TextButton(onPressed: () => openPage(context, const DeviceSettingsPage()),
              child: const Text('检查设备连接')),
          ])),
        if (answer != null) ...[
          const SectionHeading('AI 的分析'),
          SoftCard(color: const Color(0xFFF3EDF7), child: SelectableText(answer!,
            style: const TextStyle(fontSize: 15, height: 1.7))),
          Text('模型：${model ?? '-'} · 已提供 ${references.length} 个可用来源 ID，具体引用以回答中的标注为准',
            style: const TextStyle(fontSize: 11, color: Colors.grey)),
          const SizedBox(height: 10),
          OutlinedButton.icon(onPressed: () => Clipboard.setData(ClipboardData(text: answer!)),
            icon: const Icon(Icons.copy), label: const Text('复制分析文本')),
          if (widget.mode != 'decision') OutlinedButton.icon(onPressed: () =>
            openPage(context, CardEditPage(initialTitle: question.text.trim(), initialBody: answer!)),
            icon: const Icon(Icons.bookmark_add_outlined), label: const Text('整理为知识卡草稿')),
        ],
      ]);
  }
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

class DecisionReviewPage extends StatefulWidget {
  final String id;
  const DecisionReviewPage({super.key, required this.id});
  @override
  State<DecisionReviewPage> createState() => _DecisionReviewPageState();
}
class _DecisionReviewPageState extends State<DecisionReviewPage> {
  late final TextEditingController forecast;
  late final TextEditingController reality;
  late final TextEditingController next;
  String? analysis;
  bool busy = false;
  @override
  void initState() {
    super.initState(); forecast = TextEditingController();
    reality = TextEditingController(); next = TextEditingController();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final e = CompassScope.of(context).byId(widget.id);
      if (e != null) {forecast.text = e.meta['prediction']?.toString() ?? '';
        reality.text = e.meta['observed']?.toString() ?? '';
        next.text = e.meta['next_action']?.toString() ?? '';}
    });
  }
  @override
  void dispose() {forecast.dispose(); reality.dispose(); next.dispose(); super.dispose();}
  @override
  Widget build(BuildContext context) {
    final store = CompassScope.of(context); final entry = store.byId(widget.id);
    if (entry == null) return const PageFrame(title: '决策不存在', children: [Text('此记录可能已删除。')]);
    final options = (entry.meta['options'] as List? ?? []).map((e) => e.toString()).toList();
    return PageFrame(title: '决策分析与复盘', subtitle: entry.title, children: [
      SoftCard(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text('目标：${entry.meta['goal'] ?? '-'}', style: const TextStyle(fontWeight: FontWeight.bold)),
        const SizedBox(height: 12),
        Text('选项：${options.join(' / ')}'),
        const SizedBox(height: 10),
        Text('约束：${entry.body.isEmpty ? '尚未记录' : entry.body}'),
        const SizedBox(height: 10),
        Text('评分权重：${entry.meta['criteria'] ?? {}}'),
        const SizedBox(height: 10),
        SmallChip(entry.status),
      ])),
      FilledButton.icon(onPressed: busy ? null : () async {
        setState(() => busy = true);
        try {
          final answer = await AiClient(store).ask(
            '请按照决策室结构分析以下问题，先证据后判断，列反方和一个两周小实验。\n'
            '问题：${entry.title}\n目标：${entry.meta['goal']}\n选项：${options.join('、')}\n'
            '约束：${entry.body}\n权重：${entry.meta['criteria']}',
            mode: 'decision', evidence: store.items.where((e) =>
              (e.kind == 'source' || e.kind == 'card') && e.body.isNotEmpty).take(8).toList());
          if (mounted) setState(() => analysis = answer.answer);
        } catch (e) {if (mounted) notice(context, '决策分析失败：$e');}
        finally {if (mounted) setState(() => busy = false);}
      }, icon: const Icon(Icons.auto_awesome),
        label: Text(busy ? '正在审议…' : '使用 AI 对方案做反方审查')),
      if (analysis != null) SoftCard(color: const Color(0xFFF3EDF8),
        child: SelectableText(analysis!, style: const TextStyle(height: 1.7))),
      const SectionHeading('预测与行动'),
      TextField(controller: forecast, minLines: 3, maxLines: 6,
        decoration: const InputDecoration(labelText: '我的预测 / 当时选择 / 成功指标')),
      const SizedBox(height: 10),
      TextField(controller: next, minLines: 2, maxLines: 5,
        decoration: const InputDecoration(labelText: '下一步最小实验')),
      const SizedBox(height: 10),
      TextField(controller: reality, minLines: 2, maxLines: 8,
        decoration: const InputDecoration(labelText: '事后观察的实际结果')),
      const SizedBox(height: 14),
      FilledButton(onPressed: () async {
        await store.update(entry.copyWith(status: reality.text.trim().isEmpty ? '执行中' : '已复盘',
          meta: {...entry.meta, 'prediction': forecast.text.trim(),
            'next_action': next.text.trim(), 'observed': reality.text.trim()}));
        if (context.mounted) notice(context, '决策记录已保存');
      }, child: const Text('保存预测与复盘')),
      TextButton(onPressed: () async {
        final ok = await showDialog<bool>(context: context,
          builder: (c) => AlertDialog(title: const Text('删除决策？'), actions: [
            TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('取消')),
            TextButton(onPressed: () => Navigator.pop(c, true), child: const Text('删除'))]));
        if (ok == true) {await store.remove(entry); if (context.mounted) Navigator.pop(context);}
      }, child: const Text('删除这条决策', style: TextStyle(color: Colors.red))),
    ]);
  }
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

class JournalPage extends StatelessWidget {
  const JournalPage({super.key});
  @override
  Widget build(BuildContext context) {
    final entries = CompassScope.of(context).ofKind('journal');
    return PageFrame(title: '我的日记', subtitle: '默认隔离于知识检索。AI 每次读取都要先征求你同意。',
      actions: [IconButton(onPressed: () => openPage(context, const JournalEditPage()),
        icon: const Icon(Icons.edit_note))], children: [
        const SoftCard(color: Color(0xFFF1EAF8), child: Row(children: [
          Icon(Icons.lock_outline, color: grape), SizedBox(width: 10),
          Expanded(child: Text('本机私密存储。不会自动进入 AI 检索或远程同步。')),
        ])),
        if (entries.isEmpty) const SoftCard(child: Text('从今天开始记录一个真实的观察或感受。')),
        ...entries.map((e) => NavCard(icon: Icons.book_outlined, title: e.title,
          subtitle: '私密 · ${e.createdAt.year}-${e.createdAt.month}-${e.createdAt.day}',
          onTap: () => openPage(context, JournalEditPage(id: e.id)))),
        OutlinedButton.icon(onPressed: () => openPage(context, const JournalEditPage()),
          icon: const Icon(Icons.add), label: const Text('写一篇私密日记')),
      ]);
  }
}

class JournalEditPage extends StatefulWidget {
  final String? id;
  const JournalEditPage({super.key, this.id});
  @override
  State<JournalEditPage> createState() => _JournalEditPageState();
}
class _JournalEditPageState extends State<JournalEditPage> {
  final title = TextEditingController();
  final body = TextEditingController();
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      if (!mounted || widget.id == null) return;
      final store = CompassScope.of(context);
      final e = store.byId(widget.id!);
      final content = await store.privateJournalBody(widget.id!);
      if (mounted && e != null) setState(() {title.text = e.title; body.text = content;});
    });
  }
  @override
  void dispose() {title.dispose(); body.dispose(); super.dispose();}
  Future<void> save() async {
    if (title.text.trim().isEmpty || body.text.trim().isEmpty) {
      notice(context, '请输入标题和日记正文'); return;
    }
    final store = CompassScope.of(context);
    if (widget.id == null) {
      await store.add(kind: 'journal', title: title.text.trim(), body: body.text.trim(), status: '私密');
    } else {
      final e = store.byId(widget.id!);
      if (e != null) await store.update(e.copyWith(title: title.text.trim()),
        privateBody: body.text.trim());
    }
    if (mounted) notice(context, '已安全保存在本机');
  }
  Future<void> analyze() async {
    if (body.text.trim().isEmpty) {notice(context, '日记正文为空'); return;}
    final approved = await showDialog<bool>(context: context, barrierDismissible: false,
      builder: (ctx) => AlertDialog(
        title: const Row(children: [Icon(Icons.lock_person_outlined, color: grape),
          SizedBox(width: 8), Expanded(child: Text('本次私密日记授权'))]),
        content: SingleChildScrollView(child: Column(mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start, children: [
          const Text('下列内容将仅在你下一次 AI 请求中发送到你配置的 AI 工作站，'
            '该工作站可能将其转发给 DeepSeek。授权一次使用后即失效，不默认保存历史。'),
          const SizedBox(height: 14),
          Container(padding: const EdgeInsets.all(10), color: const Color(0xFFF6F0F9),
            child: Text(body.text.length > 300 ? '${body.text.substring(0, 300)}…' : body.text,
              maxLines: 10, overflow: TextOverflow.ellipsis)),
          const SizedBox(height: 10),
          const Text('发送范围：当前日记正文\n用途：一次 AI 思辨分析\n保留策略：不存入普通知识库',
            style: TextStyle(fontSize: 12)),
        ])),
        actions: [TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('拒绝')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('允许本次访问'))]));
    if (approved != true || !mounted) return;
    await openPage(context, AiChatPage(mode: 'challenge', approvedPrivateText: body.text));
  }
  @override
  Widget build(BuildContext context) => PageFrame(title: widget.id == null ? '写日记' : '编辑日记',
    subtitle: '只有你主动确认，本次日记才会被 AI 访问。', children: [
      const SoftCard(color: Color(0xFFF4EDF9), child: Row(children: [
        Icon(Icons.lock, color: grape), SizedBox(width: 10),
        Expanded(child: Text('私密正文单独存储，默认不参与知识库搜索。')),
      ])),
      TextField(controller: title, maxLength: 120, decoration: const InputDecoration(labelText: '日记标题')),
      const SizedBox(height: 12),
      TextField(controller: body, minLines: 10, maxLines: 22,
        decoration: const InputDecoration(labelText: '今天发生了什么？我有什么思考？')),
      const SizedBox(height: 14),
      FilledButton.icon(onPressed: save, icon: const Icon(Icons.save_outlined),
        label: const Text('保存私密日记')),
      OutlinedButton.icon(onPressed: analyze, icon: const Icon(Icons.auto_awesome),
        label: const Text('经本次授权后，让 AI 帮我思辨')),
      if (widget.id != null)
        TextButton(onPressed: () async {
          final store = CompassScope.of(context);
          final e = store.byId(widget.id!);
          if (e == null) return;
          final yes = await showDialog<bool>(context: context,
            builder: (c) => AlertDialog(title: const Text('永久删除这篇私密日记？'), actions: [
              TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('取消')),
              TextButton(onPressed: () => Navigator.pop(c, true), child: const Text('删除'))]));
          if (yes == true) {await store.remove(e); if (context.mounted) Navigator.pop(context);}
        }, child: const Text('删除本篇日记', style: TextStyle(color: Colors.red))),
    ]);
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
        Text('Windows 私人 AI 工作站', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18)),
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
      NavCard(icon: Icons.favorite_border, title: '成长与学习路径',
        onTap: () => openPage(context, const LearningPage())),
      NavCard(icon: Icons.book_outlined, title: '私密日记',
        subtitle: '独立本地存储 · 按次授权', onTap: () => openPage(context, const JournalPage())),
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
      NavCard(icon: Icons.file_download_outlined, title: '导出普通知识数据',
        subtitle: '复制 JSON 到剪贴板（私密日记不包含在内）',
        onTap: () async {
          final json = await CompassScope.of(context).exportJson();
          await Clipboard.setData(ClipboardData(text: json));
          if (context.mounted) setState(() => result = '已复制普通数据 JSON；请自行保存到安全文件中');
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