import 'dart:convert';
import 'dart:io';
import 'package:archive/archive.dart';
import 'package:html/parser.dart' as html;
import 'package:read_pdf_text/read_pdf_text.dart';
import 'docx_extract.dart';

class LocalDocumentResult {
  final String text;
  final int? pageCount;
  final bool partial;
  final String warning;
  const LocalDocumentResult(this.text,{this.pageCount,this.partial=false,this.warning=''});
}

class LocalDocumentParser {
  static const maxFile = 40 * 1024 * 1024;
  static const maxExtractedChars = 6000000;

  static String extension(String filename) {
    final name=filename.toLowerCase();
    for(final x in ['.pdf','.docx','.txt','.md','.epub']) {
      if(name.endsWith(x))return x;
    }
    return '';
  }

  Future<LocalDocumentResult> parseFile(File file) async {
    if(!await file.exists())throw StateError('本地素材文件已不存在，请重新选择文件');
    final length=await file.length();
    if(length<=0||length>maxFile) {
      throw const FormatException('文件需介于 1 字节与 40 MB 之间');
    }
    final ext=extension(file.path);
    String text;
    int? pages;
    if(ext=='.pdf') {
      final parts=await ReadPdfText.getPDFtextPaginated(file.path);
      pages=parts.length;
      text=[
        for(var i=0;i<parts.length;i++)
          if(parts[i].trim().isNotEmpty)
            '【第'+(i+1).toString()+'页】\n'+parts[i].trim(),
      ].join('\n\n');
      if(text.trim().isEmpty) {
        throw const FormatException('PDF 未检测到可提取文字，扫描件需要 OCR；没有生成虚假正文');
      }
    } else if(ext=='.docx') {
      text=await extractDocxText(file);
    } else if(ext=='.txt'||ext=='.md') {
      text=utf8.decode(await file.readAsBytes(),allowMalformed:true);
    } else if(ext=='.epub') {
      text=parseEpubBytes(await file.readAsBytes());
    } else {
      throw const FormatException('仅支持 PDF、DOCX、TXT、Markdown、EPUB');
    }
    if(text.trim().isEmpty)throw const FormatException('未能提取到可索引的文字');
    if(text.length>maxExtractedChars) {
      return LocalDocumentResult(text.substring(0,maxExtractedChars),
        pageCount:pages,partial:true,
        warning:'解析文字超过本机单条索引限制；只索引前 600 万字符，原始文件完整保留');
    }
    return LocalDocumentResult(text,pageCount:pages);
  }

  /// EPUB may contain many HTML chapters. Ignore images, scripts, style and
  /// navigation; keep the original compressed file for future reprocessing.
  static String parseEpubBytes(List<int> bytes) {
    final zip=ZipDecoder().decodeBytes(bytes,verify:true);
    final chapters=zip.files.where((f) =>
      f.isFile && (f.name.toLowerCase().endsWith('.xhtml') ||
                   f.name.toLowerCase().endsWith('.html') ||
                   f.name.toLowerCase().endsWith('.htm'))).toList();
    if(chapters.isEmpty)throw const FormatException('EPUB 中未找到可读章节');
    chapters.sort((a,b)=>a.name.compareTo(b.name));
    var inflated=0;
    final lines=<String>[];
    for(final part in chapters) {
      inflated+=part.size;
      if(inflated>90*1024*1024) {
        throw const FormatException('EPUB 解压内容过大，已停止解析');
      }
      final dom=html.parse(utf8.decode(part.content,allowMalformed:true));
      dom.querySelectorAll('script,style,nav').forEach((x)=>x.remove());
      final text=(dom.body?.text??dom.documentElement?.text??'').trim();
      if(text.isNotEmpty)lines.add('【章节 '+part.name+'】\n'+text);
    }
    if(lines.isEmpty)throw const FormatException('EPUB 章节没有可提取的正文');
    return lines.join('\n\n');
  }
}
